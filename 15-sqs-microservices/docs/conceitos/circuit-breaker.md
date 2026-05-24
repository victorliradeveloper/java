# Circuit Breaker

**Disjuntor elétrico aplicado a chamadas de rede:** detecta que uma dependência externa está degradada e **para de chamá-la** por um tempo, evitando que a falha derrube o resto do sistema.

No projeto está em **um único lugar**: [`EmailService.send`](../../notification-service/src/main/java/com/microservices/notification/service/EmailService.java) do `notification-service`, protegendo as chamadas ao SMTP via [Resilience4j](https://resilience4j.readme.io/).

---

## Utilidade no projeto

O `notification-service` envia email via SMTP — única dependência **externa e síncrona** que pode ficar lenta ou fora. Sem CB:

```
SMTP fora → cada msg trava 30s esperando timeout
SQS reentrega → mais threads travadas
pool consumer exausto → fila acumula → notification congela
```

**Com CB**, depois que 50% das últimas 20 chamadas falham (ou passam de 5s), o circuito **abre**. Próximas chamadas falham em microssegundos com `CallNotPermittedException`, o listener não acka, msg volta pra fila e — após 3 reentregas — cai na DLQ. SMTP descansa 30s sem receber nem 1 request; quando o CB volta a testar (`HALF_OPEN`), se já recuperou, retoma normal.

**Por que não tem CB no `todo-service`:** as dependências externas dele são Mongo (driver já tem retry/timeout próprio) e SNS via `OutboxPublisher` — e o **outbox já é** o mecanismo de resiliência. Falha de publish vira `attempts++` + `next_attempt_at` com backoff exponencial, sem martelar SNS. Empilhar CB seria redundante. Ver [`docs/sqs/outbox.md`](../sqs/outbox.md).

| Microservice | Tem CB? | Por quê |
|---|---|---|
| `notification-service` | ✓ | SMTP é externo, síncrono, pode ficar lento → risco de retry storm |
| `todo-service` | ✗ | Outbox cobre o papel pro SNS; Mongo não precisa |
| `audit-service` | ✗ | Só grava no Mongo local — sem dependência externa instável |

---

## Os 3 estados

```
        Falhas atingem o threshold
   ┌────────────────────────────────┐
   ▼                                │
 CLOSED  ────────────────────────►  OPEN
                                      │
                                      │  Cooldown expira
                                      ▼
                                 HALF_OPEN
                                      │
                ┌─── Sucessos ────────┤── Falha ───┐
                ▼                                  ▼
              CLOSED                              OPEN
```

| Estado | O que faz | Quando entra |
|---|---|---|
| `CLOSED` | Deixa passar tudo, conta sucessos/falhas | Inicial; sucesso em HALF_OPEN |
| `OPEN` | **Bloqueia** — lança `CallNotPermittedException` na hora | Threshold de falha atingido |
| `HALF_OPEN` | Permite N chamadas de teste | Cooldown de OPEN expira |

---

## Como está implementado

**Anotação** ([`EmailService.send`](../../notification-service/src/main/java/com/microservices/notification/service/EmailService.java)):

```java
@CircuitBreaker(name = "smtp")
@Retry(name = "smtp")
public void send(TodoEvent event) { /* envio SMTP */ }
```

Ordem importa: `@Retry` é interno, `@CircuitBreaker` é externo. Fluxo numa chamada:

1. CB verifica estado. Se `OPEN` → lança `CallNotPermittedException` **sem chamar SMTP**.
2. Se `CLOSED`/`HALF_OPEN` → deixa rodar. Sucesso vira sucesso; exception conta como falha.
3. Em exception SMTP → `@Retry` tenta de novo (até 3x, 200ms → 400ms → 800ms).
4. `CallNotPermittedException` **não** está na whitelist do `@Retry` → propaga direto.

**Config** ([`application.yml`](../../notification-service/src/main/resources/application.yml)):

```yaml
resilience4j:
  circuitbreaker.instances.smtp:
    sliding-window-size: 20
    minimum-number-of-calls: 10
    failure-rate-threshold: 50           # >=50% falha → OPEN
    slow-call-duration-threshold: 5s     # >5s conta como falha
    wait-duration-in-open-state: 30s
    permitted-number-of-calls-in-half-open-state: 3
    record-exceptions:
      - EmailDeliveryException
      - MailException
      - MessagingException
  retry.instances.smtp:
    max-attempts: 3
    wait-duration: 200ms
    enable-exponential-backoff: true
    exponential-backoff-multiplier: 2
    retry-exceptions:                    # whitelist: só SMTP real
      - EmailDeliveryException           # CallNotPermittedException fica de fora
      - MailException
      - MessagingException
```

**Integração com SQS:**

```
SQS msg → @SqsListener → EmailService.send()
                          ├─ CB OPEN  → CallNotPermittedException → listener NÃO acka
                          ├─ CB CLOSED → @Retry tenta 3x → sucesso ou exception propaga
                          └─ exception propaga → listener NÃO acka
                                              → msg volta após VisibilityTimeout
                                              → maxReceiveCount=3 → DLQ
```

---

## Trade-off do dedupe (send-before-insert)

Pro CB ter efeito, a ordem do listener foi invertida de `insert-before-send` pra `send-before-insert`:

| Cenário | insert-before | send-before (atual) |
|---|---|---|
| SMTP fora | Insert grava, send falha → email **PERDIDO** | Send falha → msg volta → DLQ |
| CB OPEN | Insert grava, CB abre → email **PERDIDO** | CB abre → msg volta → retoma após cooldown |
| Crash entre passos | — | Pode **duplicar** email (raro) |

Trocou "perde raro" por "duplica raro" — padrão em sistemas de notificação. Sem isso, `CB OPEN` viraria "perder sempre durante outage", o oposto do objetivo. Detalhes em [`.spec/01-issues/closed/idempotency.md`](../../.spec/01-issues/closed/idempotency.md) §1.3.

---

## Cenário concreto — SMTP lento (8s/call, threshold 5s)

```
T+0s   .. T+90s   10 chamadas, todas lentas (8s)
T+99s             CB checa: 10/10 slow >= 50% → OPEN
T+99s  .. T+129s  todas as msgs caem em CallNotPermittedException (fail-fast)
                  → msgs voltam pra fila, SMTP descansa
T+129s            CB → HALF_OPEN, permite 3 chamadas teste
T+131s            3 sucessos → CB CLOSED, drena fila normalmente
```

Sem CB com pool de 10 threads e SMTP a 8s: throughput máximo = 1.25 msg/s, backlog explode. Com CB: SMTP descansa 30s, latência média menor no agregado.

---

## Camadas de resiliência no projeto

| Mecanismo | Protege contra | Janela |
|---|---|---|
| **Retry** (`@Retry` / SQS broker) | Falha transitória curta | segundos |
| **Circuit Breaker** | Falha sustentada (retry storm) | dezenas de segundos |
| **DLQ** | Mensagens "envenenadas" | minutos a horas |
| **Idempotência** | At-least-once delivery | transversal |

Idempotência é o **pré-requisito** — sem ela, qualquer retry vira bug.

---

## Pegadinhas

| Pegadinha | Sintoma | Como evitar |
|---|---|---|
| Self-invocation (`this.send()`) | Anotação ignorada | Chamar via outro bean (já é o caso aqui) |
| `@Retry` retentando `CallNotPermittedException` | Tempo gasto retentando em circuito aberto | Whitelist em `retry-exceptions` só com SMTP real |
| Threshold muito baixo | CB abre em flakiness normal | `minimum-number-of-calls: 10` |
| `record-exceptions` faltando | Falha não conta, CB nunca abre | Listar todas as exceções de falha |
| Esquecer `spring-boot-starter-aop` | Anotações silenciosamente ignoradas | Conferir `mvn dependency:tree` |

---

## Observabilidade

```bash
# Estado do CB
curl -s http://localhost:8082/actuator/circuitbreakers/smtp | jq

# Métricas Micrometer / Prometheus
curl -s http://localhost:8082/actuator/metrics/resilience4j.circuitbreaker.state | jq
curl -s http://localhost:8082/actuator/metrics/resilience4j.retry.calls | jq

# Health (CB faz parte do healthcheck — útil pra K8s readiness)
curl -s http://localhost:8082/actuator/health | jq
```

Forçar CB a abrir em dev: apontar `SPRING_MAIL_HOST` pra `localhost:9999` e disparar 10+ POSTs no `todo-service`.

---

## Pra entrevista

**O que é?** Padrão de resiliência que **para de chamar** dependência externa degradada, evitando retry storm e cascata de falhas. 3 estados (CLOSED/OPEN/HALF_OPEN), threshold típico 50% em janela de 20 chamadas.

**Diferença pra Retry?** Retry resolve **falha curta** (timeout transitório). CB reconhece **falha sustentada** e para de tentar. Compõem: retry pra flakiness, CB pra outage.

**Quando NÃO usar?** Recursos locais, dependências que já têm retry/timeout no driver (Mongo), ou onde já existe outra camada de resiliência (outbox).

---

## Referências

- [`docs/sqs/retry.md`](../sqs/retry.md) — primeiro nível antes do CB
- [`docs/sqs/dlq.md`](../sqs/dlq.md) — último recurso depois de CB esgotar
- [`docs/conceitos/idempotencia.md`](./idempotencia.md) — pré-requisito de retry seguro
- [Resilience4j — CircuitBreaker](https://resilience4j.readme.io/docs/circuitbreaker)
- [Martin Fowler — CircuitBreaker](https://martinfowler.com/bliki/CircuitBreaker.html)

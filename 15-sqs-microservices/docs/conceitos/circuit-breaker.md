# Circuit Breaker

Mecanismo que **detecta quando uma dependência externa está degradada e para de chamá-la temporariamente**, evitando que falhas em uma parte do sistema derrubem o resto. É um disjuntor elétrico aplicado a chamadas de rede.

Implementado no projeto em [`EmailService.send`](../../notification-service/src/main/java/com/microservices/notification/service/EmailService.java) via [Resilience4j](https://resilience4j.readme.io/), protegendo as chamadas SMTP do [`TodoEventListener`](../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventListener.java).

---

## Por que existe — o problema do "retry storm"

Imagina SMTP fora. Sem circuit breaker:

```
T+0s    msg1 → SMTP timeout (30s)
T+30s   msg2 → SMTP timeout (30s)
T+60s   msg3 → SMTP timeout (30s)
...
T+30s   msg1 reentregue pelo SQS (visibility expirou) → SMTP timeout
...
```

Cada thread do consumer fica **30s travada** num SMTP morto. SQS reentrega msgs (at-least-once), gerando mais tentativas. O resultado:

- **Latência cascateia** — outras filas com consumers compartilhados ficam travadas.
- **Threads exaustas** — todo o pool consumer ocupado esperando timeout.
- **SMTP pode ficar pior** — quando voltar, recebe avalanche de tentativas acumuladas.
- **DLQ não escala** — msgs caem em DLQ por `maxReceiveCount`, mas só *depois* de gastar tempo tentando.

Circuit breaker corta isso: depois de N falhas, **para de tentar por um tempo**. Falha rápida em vez de falha lenta.

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

| Estado | Comportamento | Quando entra |
|---|---|---|
| `CLOSED` | Deixa passar todas as chamadas, conta sucessos/falhas | Estado inicial; saída de HALF_OPEN com sucesso |
| `OPEN` | **Bloqueia todas** as chamadas, lança `CallNotPermittedException` imediatamente | Threshold de falha atingido em CLOSED ou HALF_OPEN |
| `HALF_OPEN` | Deixa passar N chamadas de teste; decide se volta pra CLOSED ou OPEN | Cooldown de OPEN expira |

---

## Como o projeto implementa

### 1. Dependência ([`pom.xml`](../../notification-service/pom.xml))

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-micrometer</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

Resilience4j substituiu o Hystrix (em manutenção) como biblioteca padrão de resiliência no ecossistema Spring/Java desde 2018.

### 2. Anotação no método ([`EmailService.send`](../../notification-service/src/main/java/com/microservices/notification/service/EmailService.java))

```java
@CircuitBreaker(name = "smtp")
@Retry(name = "smtp")
public void send(TodoEvent event) {
    // ... envio SMTP
}
```

`@Retry` envolve `@CircuitBreaker` (ordem default do Resilience4j). Fluxo:

1. `@Retry` chama o método.
2. `@CircuitBreaker` verifica o estado: se `OPEN`, lança `CallNotPermittedException` imediatamente.
3. Se `CLOSED`/`HALF_OPEN`, deixa o método rodar. Sucesso vira sucesso; exceção vira falha contabilizada.
4. Se exceção: `@Retry` decide se retenta (configurado pra retentar SMTP real, **não** retentar `CallNotPermittedException`).

### 3. Configuração ([`application.yml`](../../notification-service/src/main/resources/application.yml))

```yaml
resilience4j:
  circuitbreaker:
    instances:
      smtp:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50           # >=50% falha → OPEN
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 5s     # >5s conta como falha
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        record-exceptions:
          - EmailDeliveryException
          - MailException
          - MessagingException
  retry:
    instances:
      smtp:
        max-attempts: 3
        wait-duration: 200ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        retry-exceptions:                    # whitelist: SO retentar SMTP real
          - EmailDeliveryException
          - MailException
          - MessagingException
```

Parâmetros chave:

| Config | Valor | Razão |
|---|---|---|
| `sliding-window-size: 20` | últimas 20 chamadas | Estatística com volume razoável |
| `minimum-number-of-calls: 10` | só decide com ≥10 dados | Evita decisão prematura |
| `failure-rate-threshold: 50` | abre se ≥50% falham | Stripe usa 50, Netflix 50-80 |
| `slow-call-duration-threshold: 5s` | >5s conta como falha | SMTP normal responde em <2s |
| `wait-duration-in-open-state: 30s` | cooldown | Suficiente pra SMTP normalizar |
| `retry-exceptions` whitelist | só SMTP real | `CallNotPermittedException` **não** entra → CB OPEN não desperdiça retries |

### 4. Integração com a malha de resiliência

A peça do CB se encaixa no sistema existente:

```
SQS msg
  │
  ▼
TodoEventListener.process(event, messageId)
  ├─ existsById(messageId)? → SIM: skip (dedupe)
  │                          → NÃO: continue
  ├─ emailService.send(event)
  │     ├─ @Retry: até 3x com backoff 200ms→400ms→800ms
  │     └─ @CircuitBreaker:
  │          ├─ CLOSED: chama SMTP → sucesso ou falha contabilizada
  │          ├─ OPEN:   lança CallNotPermittedException SEM chamar SMTP
  │          └─ HALF_OPEN: até 3 chamadas teste
  │
  ├─ Sucesso → tryInsert(messageId) → marca processado
  │
  └─ Exceção → @SqsListener NÃO acka → msg volta pra fila
                  ├─ retry 1, 2, 3 (visibility timeout = 30s entre cada)
                  └─ maxReceiveCount=3 → DLQ (todo-created-dlq)
```

**Importante**: a ordem de operações no listener foi invertida (`send` **antes** do `tryInsert`) pra esse fluxo funcionar. Detalhes em [Trade-off do dedupe](#trade-off-do-dedupe).

### 5. Observabilidade

Actuator expõe os endpoints:

```bash
# Estado atual do CB
curl http://localhost:8082/actuator/circuitbreakers

# Métricas de retry
curl http://localhost:8082/actuator/retries

# CB inclusos no health check do serviço
curl http://localhost:8082/actuator/health

# Métricas detalhadas em formato Prometheus / Micrometer
curl http://localhost:8082/actuator/metrics/resilience4j.circuitbreaker.state
curl http://localhost:8082/actuator/metrics/resilience4j.circuitbreaker.calls
curl http://localhost:8082/actuator/metrics/resilience4j.retry.calls
```

Health endpoint pode mostrar `DOWN` se o CB estiver `OPEN` — ótimo pra K8s readiness probe.

---

## Trade-off do dedupe

Pra CB ter efeito real, a ordem no listener foi mudada de **insert-before-send** pra **send-before-insert**:

| Aspecto | Antes (insert-before) | Depois (send-before) |
|---|---|---|
| SMTP fora | Insert grava, send falha → email PERDIDO | Send falha → msg volta pra fila → DLQ após 3x |
| CB OPEN | Insert grava, CB OPEN → email PERDIDO | CB OPEN → msg volta pra fila → tenta de novo após cooldown |
| Crash entre 2 passos | (não aplicável) | Send sucedeu, insert falhou → próxima entrega manda email de novo (DUPLICA) |
| Trade-off | "perde raro" | "duplica raro" |

**Por que mudou**: com CB, "perde raro" se tornaria "perde sempre durante outage" — exatamente o cenário onde mais precisamos do email chegar (ou pelo menos cair na DLQ pra ser inspecionado). "Duplica raro" é o trade-off padrão em sistemas de notificação modernos.

Documentado em [`.spec/01-issues/closed/idempotency.md`](../../.spec/01-issues/closed/idempotency.md) §1.3.

---

## Fluxo end-to-end com SMTP degradado

Cenário: SMTP responde mas com 8s de latência (passou do threshold de 5s).

```
T+0s    msg1 chega → send() → 8s na SMTP → returns OK → tryInsert OK
        CB conta: 1 slow call

T+9s    msg2 chega → send() → 8s na SMTP → returns OK → tryInsert OK
        CB conta: 2 slow calls

... (após 10 chamadas, todas lentas, CB checa: 10/10 lentas >= 50% threshold)

T+99s   msg11 chega → send() → @CircuitBreaker abre o circuito antes da chamada
                              → lança CallNotPermittedException
                              → @SqsListener NÃO acka
                              → msg volta pra fila
        CB estado: OPEN
        
T+99s a T+129s: TODAS as msgs caem direto em CallNotPermittedException (30s cooldown)
        - msgs ficam acumulando "em voo" na fila (visibility timeout)
        - SMTP descansa, não recebe nem 1 request

T+129s  CB transiciona pra HALF_OPEN automaticamente
        msg N chega → send() → permite teste → SMTP responde rápido (recuperou)
        CB conta: 1 sucesso
        
T+130s  msg N+1 → teste 2 → sucesso
T+131s  msg N+2 → teste 3 → sucesso
        CB: 3 sucessos em HALF_OPEN → volta pra CLOSED
        
T+132s+ Operação normal. Msgs acumuladas em fila são drenadas.
```

**Sem CB**, durante 30s de SMTP lento, **cada thread** ficaria 8s travada. Com pool de 10 threads, throughput máximo = 1.25 msg/s. Backlog explode.

**Com CB**, SMTP descansa 30s, fila acumula sem desperdício, drena rápido quando volta. Latência média menor no agregado.

---

## Quando NÃO usar Circuit Breaker

CB protege chamadas a **dependências externas instáveis**. Não faz sentido em:

| Caso | Por que não |
|---|---|
| Chamadas locais (mesma JVM) | Nada de rede pra falhar |
| Mongo da própria infra | Driver tem retry/timeout, CB extra é redundante |
| `OutboxPublisher` → SNS | Outbox **já é** o mecanismo de resiliência: falha = `attempts++`, próximo ciclo retenta |
| Operação que **precisa** acontecer (cobrança, fraude) | CB OPEN bloqueia chamadas legítimas — prefira fila + retry indefinido |
| Latência mais importante que disponibilidade | CB adiciona overhead pequeno mas mensurável |

Regra: use CB quando a chamada externa pode degradar O CONSUMIDOR (este serviço). Se o consumidor já tem outra rede de proteção (outbox, fila, dedupe), avalie antes de empilhar.

---

## Comparação com retry, DLQ, idempotência

| Mecanismo | Protege contra | Onde no projeto |
|---|---|---|
| **Retry** (Resilience4j ou SQS broker) | Falha transitória de curta duração | `@Retry` no `EmailService`; `maxReceiveCount=3` no SQS |
| **Circuit Breaker** | Falha sustentada que causaria retry storm | `@CircuitBreaker` no `EmailService` |
| **DLQ** | Mensagens "envenenadas" que falham determinísticamente | Filas `*-dlq` no LocalStack/SQS |
| **Idempotência** | Retry seguro + at-least-once delivery | `IdempotencyService` + `processed_messages` + `_id=messageId` |

**Compõem em camadas**:

```
Resiliente em segundos  →  Resiliente em minutos  →  Resiliente em horas
─────────────────────      ─────────────────────      ─────────────────────
Retry (200ms-1s)           Circuit Breaker (30s)      DLQ + redrive manual
                                                       (após 3x maxReceiveCount)
```

Idempotência é transversal: torna **todas** as camadas seguras (sem ela, qualquer retry vira bug).

---

## Pegadinhas comuns

| Pegadinha | Sintoma | Como evitar |
|---|---|---|
| Self-invocation (`this.send()`) | CB não dispara, anotação ignorada | Sempre chamar via outro bean (proxy) — é o caso aqui (`TodoEventListener` chama `EmailService`) |
| `@Retry` retenta `CallNotPermittedException` | Tempo gasto retentando em circuito aberto | Whitelist em `retry-exceptions` só com exceções de SMTP real |
| Threshold muito baixo | CB abre em flakiness normal | `minimum-number-of-calls: 10` evita decisão prematura |
| Cooldown muito curto | CB fecha sem SMTP ter recuperado, abre de novo | 30s é razoável; 60s pra dependências mais críticas |
| `record-exceptions` faltando | Falha não é contabilizada, CB nunca abre | Listar todas as exceções "de falha" — incluindo ancestral comum (`Exception`/`Throwable` é amplo demais) |
| Esquecer `spring-boot-starter-aop` | Anotações são silenciosamente ignoradas | Conferir dep tree: `mvn dependency:tree | grep aop` |

---

## Comandos pra verificar

```bash
# Estado dos circuit breakers
curl -s http://localhost:8082/actuator/circuitbreakers | jq

# Estado específico do "smtp"
curl -s http://localhost:8082/actuator/circuitbreakers/smtp | jq

# Métricas Micrometer
curl -s http://localhost:8082/actuator/metrics/resilience4j.circuitbreaker.calls | jq

# Health check (mostra CB no payload)
curl -s http://localhost:8082/actuator/health | jq
```

Pra forçar CB a abrir em dev: derrubar SMTP (mudar a senha do Gmail, ou apontar `SPRING_MAIL_HOST` pra `localhost:9999`) e disparar 10+ POSTs no `todo-service`.

---

## Pra entrevista

**Pergunta clássica**: *"O que é Circuit Breaker e quando usar?"*

Resposta em 3 frases:
1. **Padrão de resiliência** que para de chamar uma dependência externa quando ela está degradada, evitando retry storm e cascata de falhas.
2. **3 estados**: CLOSED (normal), OPEN (fail-fast por X segundos), HALF_OPEN (teste se a dependência voltou). Threshold típico: 50% de falhas em janela de 20 chamadas.
3. **Onde aplicar**: chamadas síncronas a serviços externos instáveis (SMTP, REST de terceiros, banco remoto). **Não** aplicar em recursos locais ou onde já existe outra camada de resiliência (outbox).

**Follow-up clássico**: *"Diferença entre Circuit Breaker e Retry?"*

Retry tenta resolver **falha de curta duração** (timeout transitório). Circuit Breaker reconhece que a dependência está **sustentadamente ruim** e para de tentar. Os dois se compõem: retry resolve flakiness, CB resolve outage.

---

## Referências

- [`docs/sqs/retry.md`](../sqs/retry.md) — retry é o "primeiro nível" antes do CB
- [`docs/sqs/dlq.md`](../sqs/dlq.md) — DLQ é o "último recurso" depois de CB + retry esgotarem
- [`docs/conceitos/idempotencia.md`](./idempotencia.md) — pré-requisito pra retry seguro
- [`.spec/01-issues/closed/idempotency.md`](../../.spec/01-issues/closed/idempotency.md) — decisão de inverter ordem do dedupe
- [Resilience4j docs — CircuitBreaker](https://resilience4j.readme.io/docs/circuitbreaker)
- [Martin Fowler — CircuitBreaker](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Netflix Hystrix retrospective](https://github.com/Netflix/Hystrix#hystrix-status) — porque o ecossistema migrou pra Resilience4j

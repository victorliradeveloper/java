# Resilience4j no todo-service

## O problema que isso resolve

Em arquitetura sincrona, se o `audit-service` ou `notification-service` esta
caido/lento, naively cada chamada do `todo-service` vai:

- esperar o timeout do Feign (5s aqui),
- repetir esse custo a cada novo request,
- propagar o erro pro cliente final,
- empilhar threads bloqueadas ate esgotar o pool.

Eh o classico "retry storm + cascading failure". Resilience4j evita isso.

## Como esta configurado

Cada downstream tem instancia separada (`audit-service`, `notification-service`)
pra que falha em um nao abra o circuito do outro. Configurado em
`application.yml`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      audit-service:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 3s
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
      notification-service:
        # ... (similar mas slow-call-duration-threshold: 5s — SMTP eh naturalmente mais lento)
  retry:
    instances:
      audit-service:
        max-attempts: 3
        wait-duration: 200ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        ignore-exceptions:
          - io.github.resilience4j.circuitbreaker.CallNotPermittedException
```

E aplicado no `DownstreamNotifier`:

```java
@CircuitBreaker(name = "audit-service", fallbackMethod = "auditFallback")
@Retry(name = "audit-service")
public void notifyAudit(TodoEventPayload event) {
    auditClient.recordEvent(event);
}

private void auditFallback(TodoEventPayload event, Throwable ex) {
    log.error("[AUDIT-FALLBACK] evento perdido eventId={} ...", event.eventId());
}
```

## Estados do Circuit Breaker

```
        falhas >=50% das ultimas 20
CLOSED ----------------------------> OPEN
  ^                                    |
  |                                    | wait 30s
  | sucessos                           v
HALF_OPEN <----------------------- (testa 3 chamadas)
  |   |
  |   `--> falha qualquer -> OPEN
  `------> 3 sucessos -> CLOSED
```

- **CLOSED**: tudo normal, chamadas passam.
- **OPEN**: chamadas falham IMEDIATAMENTE com `CallNotPermittedException`. Nem
  tenta HTTP. Fail-fast.
- **HALF_OPEN**: depois de 30s em OPEN, deixa N chamadas de teste passarem
  pra ver se o downstream voltou. Se sucedem, volta pra CLOSED.

## Por que `ignore-exceptions` em CallNotPermittedException

Quando o CB esta OPEN, o Retry NAO deve retentar — seria desperdicio: o CB
ja decidiu nao deixar passar. Sem essa config, voce perderia 3 chamadas
(3 retries) sempre que o CB estivesse OPEN, multiplicando o trafego inutil.

## Por que fallback ao inves de @Recover ou catch

O `fallbackMethod` do Resilience4j eh chamado **depois** de esgotado o retry.
Ele tem que ter a mesma assinatura do metodo original + um `Throwable` no
final. O AOP do Resilience4j injeta automaticamente a exception.

Se eu fizesse try/catch dentro do metodo, o CircuitBreaker nao registraria a
chamada como falha — afinal, ela "deu sucesso" pra ele. O fallback externo
mantem as estatisticas corretas.

## Como observar em runtime

```bash
# Estado atual dos CBs do todo-service
curl http://localhost:8081/actuator/circuitbreakers | jq

# Metricas detalhadas
curl http://localhost:8081/actuator/metrics/resilience4j.circuitbreaker.calls
curl http://localhost:8081/actuator/metrics/resilience4j.retry.calls
```

## Como testar o fallback

```bash
# 1. Sobe tudo
docker compose --env-file .env.dev up

# 2. Derruba o audit-service
docker compose stop audit-service

# 3. Cria um Todo
curl -X POST http://localhost:8090/todos \
  -H "Content-Type: application/json" \
  -d '{"title":"teste fallback","description":""}'

# 4. Observa os logs do todo-service:
#    - 3 retries com backoff
#    - Depois fallback: "[AUDIT-FALLBACK] evento perdido eventId=..."
#    - Mesmo assim retorna 201 pro cliente!
```

Se voce derrubar o audit e fizer ~10 chamadas seguidas, o CB abre e voce vai
ver no log que as proximas chamadas vao DIRETO pro fallback sem nem tentar
HTTP (a latencia despenca).

## Hook de teste de email

O `notification-service` tem um hook: se `title` comeca com `!fail`, ele
forca exception. Util pra disparar o fallback do `todo-service`:

```bash
curl -X POST http://localhost:8090/todos \
  -H "Content-Type: application/json" \
  -d '{"title":"!fail teste","description":""}'
```

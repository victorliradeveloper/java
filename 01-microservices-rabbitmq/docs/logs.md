# Logging e Tracing

O `todo-service` emite **logs estruturados** com **trace context** (traceId/spanId)
em todas as linhas. Em dev o formato é legível por humano; em prod é **JSON**,
uma linha por evento, pronto pra ingestão em ELK/Loki.

---

## Os dois formatos (por profile)

Controlado por [`logback-spring.xml`](../todo-service/src/main/resources/logback-spring.xml) via `<springProfile>`:

| Profile | Appender | Formato |
|---|---|---|
| `dev` / `!prod` | `CONSOLE` | Texto legível, trace context inline |
| `prod` | `JSON` | `LogstashEncoder`, uma linha JSON por evento |

**dev** — `%d{HH:mm:ss.SSS} %-5level [%X{traceId},%X{spanId}] [%X{idempotencyKey}] %logger - %msg`:

```
21:14:47.334 INFO  [3a1f...,b2c9...] [] c.m.todo.service.TodoService - [TODO] criado id=abc title='comprar pão' createdAt=2026-06-15T21:14:47
```

**prod** — MDC promovido a campos de topo + `service`:

```json
{"@timestamp":"2026-06-15T21:14:47.334Z","level":"INFO","logger_name":"com.microservices.todo.service.TodoService",
 "message":"[TODO] criado id=abc title='comprar pão' createdAt=2026-06-15T21:14:47",
 "service":"todo-service","traceId":"3a1f...","spanId":"b2c9..."}
```

> Os níveis (`logging.level.*`) continuam vindo dos `application-*.yml` e são
> aplicados **por cima** do `logback-spring.xml`. Ver [profiles.md](profiles.md).

---

## Trace context (Micrometer Tracing + Brave)

- Dependência: `micrometer-tracing-bridge-brave` — **sem reporter** (Zipkin/OTLP).
  Só queremos correlação de logs, não export pra um backend de tracing.
- `management.tracing.propagation.type: w3c` — formato `traceparent` (padrão moderno).
- `management.tracing.sampling.probability: 1.0` — toda request gera trace, então
  **toda linha de log tem `traceId`** (o default 0.1 deixaria 90% sem).

### MDC disponível

| Chave | Origem | Quando aparece |
|---|---|---|
| `traceId` / `spanId` | Micrometer/Brave | Sempre (todo request/scheduled) |
| `idempotencyKey` | [`IdempotencyService`](../todo-service/src/main/java/com/microservices/todo/idempotency/IdempotencyService.java) (`MDC.put`) | Só no caminho de idempotência |

### Trace costurado através do outbox

O [`OutboxPublisher`](../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java)
publica num `@Scheduled` (thread própria, **sem** o trace da request HTTP original).
Sem tratamento, HTTP → outbox → AMQP → consumer apareceriam como traces desconexos.
Solução:

```
POST /todos (trace A)
  └─ OutboxService.record()  → captura traceparent de A → coluna trace_parent
        ⋮ (commit, thread acaba)
  @Scheduled OutboxPublisher.publishOne()  (thread do scheduler, trace B)
  └─ OutboxTracePropagator.restore(trace_parent)  → reabre trace A
        └─ log "[OUTBOX] publicado" sai sob traceId A
        └─ RabbitTemplate (observationEnabled) injeta traceparent A no header AMQP
              → consumer dá join no trace A
```

- Migration [`V002__outbox_trace_parent.sql`](../todo-service/src/main/resources/db/migration/V002__outbox_trace_parent.sql) adiciona a coluna `trace_parent VARCHAR(64)`.
- `setObservationEnabled(true)` (em [`RabbitMQConfig`](../todo-service/src/main/java/com/microservices/todo/config/RabbitMQConfig.java)) injeta o header.
- Captura/restauração do contexto: [`OutboxTracePropagator`](../todo-service/src/main/java/com/microservices/todo/outbox/OutboxTracePropagator.java), chamado por [`OutboxService`](../todo-service/src/main/java/com/microservices/todo/outbox/OutboxService.java).

---

## Catálogo de logs

Convenção: todo log começa com uma **tag** `[AREA]` pra facilitar grep/filtro.

### `[TODO]` — ciclo de vida da entidade ([`TodoService`](../todo-service/src/main/java/com/microservices/todo/service/TodoService.java))

| Nível | Mensagem |
|---|---|
| DEBUG | `[TODO] criando title='...'` |
| INFO | `[TODO] criado id=... title='...' createdAt=...` |
| INFO | `[TODO] atualizado id=... title='...'` |
| INFO | `[TODO] removido id=...` |

### `[OUTBOX]` — outbox pattern ([`OutboxService`](../todo-service/src/main/java/com/microservices/todo/outbox/OutboxService.java) / [`OutboxPublisher`](../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java))

| Nível | Mensagem |
|---|---|
| INFO | `[OUTBOX] enfileirado id=... aggregateId=... aggregateType=... eventType=... routingKey=...` |
| INFO | `[OUTBOX] publisher iniciado nodeId=...` (no boot) |
| INFO | `[OUTBOX] publicado id=... exchange=... rk=... eventType=...` |
| WARN | `[OUTBOX] falha id=... attempts=... nextAttemptAt=...: <erro>` |

### `[IDEMPOTENCY]` — idempotência ([`IdempotencyService`](../todo-service/src/main/java/com/microservices/todo/idempotency/IdempotencyService.java))

| Nível | Mensagem |
|---|---|
| INFO | `[IDEMPOTENCY] retornando response cacheada key=...` |
| INFO | `[IDEMPOTENCY] claim resolvido com sucesso key=...` |
| WARN | `[IDEMPOTENCY] hash mismatch key=...` (payload diferente → 409) |
| WARN | `[IDEMPOTENCY] requisicao concorrente em processamento key=...` (→ 409 IN_PROGRESS) |
| WARN | `[IDEMPOTENCY] operation falhou, claim liberado key=... err=...` |
| ERROR | `[IDEMPOTENCY] operation OK mas falha ao cachear response key=...` |
| ERROR | `[IDEMPOTENCY] falha ao liberar claim key=...` |

### `[IDEMPOTENCY-CLEANUP]` — job agendado ([`IdempotencyKeyCleanupJob`](../todo-service/src/main/java/com/microservices/todo/idempotency/IdempotencyKeyCleanupJob.java))

| Nível | Mensagem |
|---|---|
| INFO | `[IDEMPOTENCY-CLEANUP] N claim(s) expirado(s) removido(s)` |

### `[API]` — erros HTTP ([`GlobalExceptionHandler`](../todo-service/src/main/java/com/microservices/todo/exception/GlobalExceptionHandler.java))

| Nível | Mensagem |
|---|---|
| WARN | `[API] 404 Not Found: ...` |
| WARN | `[API] 409 Idempotency-Key conflict reason=... key=...` |

---

## Como ler / filtrar

**Seguir uma operação ponta a ponta** — pegue o `traceId` de qualquer linha e
filtre por ele (vale inclusive entre serviços, se o consumer der join):

```powershell
docker logs todo-service | Select-String "3a1f"
```

**Filtrar por área** (tag):

```powershell
docker logs todo-service | Select-String "\[OUTBOX\]"
```

**Em prod (JSON)** — com `jq`, filtrar por trace e projetar campos:

```bash
docker logs todo-service | jq -c 'select(.traceId=="3a1f...") | {ts:.["@timestamp"], level, msg:.message}'
```

**Ligar SQL/DEBUG sob demanda** sem reiniciar: ver
[profiles.md](profiles.md#ligar-sql-sob-demanda-sem-reiniciar) (`/actuator/loggers`).

---

## Decisões de design

**1. Tag `[AREA]` em todo log.** Torna grep/alerta trivial sem depender de
`logger_name`. Áreas atuais: `TODO`, `OUTBOX`, `IDEMPOTENCY`, `IDEMPOTENCY-CLEANUP`, `API`.

**2. Sem reporter de tracing.** Brave gera traceId/spanId pra correlação de log,
mas não exportamos pra Zipkin/OTLP — fora do escopo do projeto. Adicionar um
reporter depois é só trocar/incluir a dependência.

**3. `sampling.probability: 1.0`.** Sem reporter, amostrar não economiza nada e
deixaria linhas sem `traceId`. Em produção real com export, abaixar faz sentido.

**4. Não logar payload cru.** `create` loga só `title`, nunca o DTO inteiro —
evita ruído e vazamento. Logs de negócio carregam `id` + campos chave, não o objeto.

**5. Erros de API logados no handler.** 404/409 saíam só como resposta HTTP, sem
rastro. Agora o [`GlobalExceptionHandler`](../todo-service/src/main/java/com/microservices/todo/exception/GlobalExceptionHandler.java)
loga em WARN (não são bugs do servidor).

---

## Pendências

- **Trace end-to-end:** o todo-service já injeta o `traceparent` no header AMQP,
  mas [**notification-service**](../notification-service) e [**audit-service**](../audit-service) só dão join se tiverem
  `micrometer-tracing-bridge-brave` + `propagation.type: w3c` (mesmo type, senão
  não casa) e observation habilitada no listener container.
- **Access log de request:** não há log de entrada/saída no controller. Se quiser
  rastrear toda request HTTP, dá pra adicionar um filtro de logging ou habilitar o do Spring.

---

## Referências

- [profiles.md](profiles.md) — níveis por ambiente, `/actuator/loggers`
- [debug.md](debug.md) — debug local e remoto
- [logback-spring.xml](../todo-service/src/main/resources/logback-spring.xml) — appenders por profile
- [application.yml](../todo-service/src/main/resources/application.yml) — config de tracing
- Propagação através do outbox: [OutboxTracePropagator.java](../todo-service/src/main/java/com/microservices/todo/outbox/OutboxTracePropagator.java) · [OutboxService.java](../todo-service/src/main/java/com/microservices/todo/outbox/OutboxService.java) · [OutboxPublisher.java](../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java)
- [V002__outbox_trace_parent.sql](../todo-service/src/main/resources/db/migration/V002__outbox_trace_parent.sql) — coluna `trace_parent`
- [RabbitMQConfig.java](../todo-service/src/main/java/com/microservices/todo/config/RabbitMQConfig.java) — `observationEnabled`

# Fluxo de eventos ponta-a-ponta

Documento descrevendo o caminho completo de uma requisição HTTP até os efeitos
colaterais finais (email + audit log), com as garantias de cada hop.

Exemplo usado: `POST /todos`. `PUT` e `DELETE` seguem o mesmo formato, mudando
apenas o `eventType` propagado.

---

## Visão geral

```
Client ──► api-gateway ──► todo-service ──► Mongo (TX: todo + outbox)
                                │
                                ▼
                          OutboxPublisher (poll)
                                │
                                ▼
                          SNS topic todo-events
                                │
              ┌─────────────────┼─────────────────┐
              ▼                 ▼                 ▼
     todo-created-queue   todo-audit-queue    (filter:UPDATED/DELETED)
              │                 │
              ▼                 ▼
     notification-service   audit-service
       (SMTP + dedupe)      (insert _id=messageId)
```

### Diagrama em blocos

Visão minima — Producer → Broker → Consumer:

```
┌──────────┐   publish()    ┌────────┐   poll/push    ┌──────────┐
│ Producer ├───────────────►│ Broker ├───────────────►│ Consumer │
└──────────┘                └────────┘                └──────────┘
  todo-service           SNS + SQS                notification-service
                         (LocalStack)             audit-service
```

#### Zoom no Producer

```
┌─────────────┐   @Tx    ┌─────────────┐   poll/claim   ┌─────────────┐
│ TodoService ├─────────►│  outbox_    ├───────────────►│   Outbox    ├──► publish()
└─────────────┘  save    │  events     │  findAndModify │  Publisher  │
                         │  (Mongo)    │     + lease    └─────────────┘
                         └─────────────┘
```

#### Zoom no Broker

```
              ┌──────────┐   fan-out   ┌────────────────┐
publish() ───►│   SNS    ├────────────►│  SQS  4 queues ├──► consume
              │  topic   │   filter    │  +  4 DLQs     │
              │ todo-    │   policy    │  maxReceive=3  │
              │ events   │             └────────────────┘
              └──────────┘
```

#### Zoom no Consumer

```
              ┌──────────────────┐  @SqsListener   ┌──────────────┐
consume ─────►│  TodoEvent       ├────────────────►│ side effect  │
              │  + MessageId     │   dedupe        │ SMTP / audit │
              └──────────────────┘                 └──────────────┘
                 processed_messages              EmailService (CB+Retry)
                 ou _id=messageId                / insert todo_audit_log
```

#### Tudo em uma linha (visão completa)

```
┌────────┐  POST   ┌─────────┐  @Tx    ┌─────────┐  poll    ┌──────────┐  publish  ┌──────┐  fan-out  ┌──────┐  consume   ┌──────────────┐
│ Client ├────────►│ Gateway ├────────►│ Service ├─────────►│  Outbox  ├──────────►│ SNS  ├──────────►│ SQS  ├───────────►│  notification│
└────────┘         └─────────┘  save   └─────────┘  claim   │ Publisher│           │topic │  filter   │+ DLQs│            │  + audit     │
                                                            └──────────┘           └──────┘           └──────┘            └──────────────┘
                                          │
                                          ▼ Mongo (TX: todos + outbox_events)
```

Legenda das setas:
- `─►` síncrono dentro da request HTTP (steps até o 201)
- `─►` (depois do Service) assíncrono — disparado pelo `@Scheduled` do Publisher
- Cada hop pode falhar e retentar isolado: TX rollback no Service, lease re-claim no Publisher, redrive pra DLQ no Consumer

---

## Hop 1 — Client → api-gateway → todo-service

```
POST /todos
Idempotency-Key: abc-123        # opcional
Content-Type: application/json
{ "title": "X", "description": "..." }
```

- `api-gateway` (porta 8090) aplica rate limit no Redis e roteia via Eureka.
- `TodoController.create` recebe a request e delega ao `IdempotencyService`.

### Idempotência HTTP (`IdempotencyService.executeIdempotent`)

Estilo Stripe — só ativa se o header `Idempotency-Key` estiver presente.

1. Valida key (ASCII printável, `1..maxKeyLength`).
2. Calcula `requestHash = SHA-256("POST /todos\n" + body)`.
3. Tenta `repository.insert(claim)` na collection `idempotency_keys`
   (unique index em `_id` garante atomicidade).
4. Resultado:

| Caso | Comportamento |
|---|---|
| Insert OK | Executa `service.create(dto)`, grava response no claim, retorna 201 |
| `DuplicateKeyException` + hash igual + response cacheado | Replay: retorna response salvo |
| `DuplicateKeyException` + hash igual + sem response | 409 `IN_PROGRESS` (request concorrente) |
| `DuplicateKeyException` + hash diferente | 409 `PAYLOAD_MISMATCH` |

### Tratamento de falhas no claim

- **Operação de negócio lança**: claim é deletado, exceção propaga.
  Cliente vê erro real e pode retentar com a mesma key.
- **Cache do response falha**: loga ERROR mas devolve 201. Operação já
  sucedeu, cliente precisa do recurso. Retry dentro do TTL pode bater
  409 `IN_PROGRESS` (até o doc expirar pelo TTL index).

### TTL

Index TTL em `expires_at` (`V004_IdempotencyKeyIndexes`). Mongo limpa em
background — sem job manual.

---

## Hop 2 — TodoService: write + outbox (mesma TX Mongo)

`TodoService.create` em `@Transactional`:

```
TX BEGIN
  todos.insert({
    _id = UUID,
    title, description,
    createdAt = now,
    updatedAt = now
  })
  outbox_events.insert({
    _id = UUID,
    aggregateId = todo.id,
    aggregateType = "Todo",
    eventType = "CREATED",
    destination = "todo-events",
    payload = <JSON do TodoEvent>,
    createdAt = now,
    publishedAt = null,
    attempts = 0
  })
TX COMMIT
```

**Garantia chave**: ambos os inserts commitam juntos ou nenhum.
Sem outbox no commit → não publica. Com outbox commitado mas sem todo →
impossível (mesma TX).

Replica set Mongo (`rs0`) é requisito — sem ele, sem multi-document transaction.

### Particularidades por operação

| Operação | Quando publica |
|---|---|
| `create` | Sempre |
| `update` | Apenas se `TodoSnapshot.before != after` (no-op PUT não publica nem bumpa `updatedAt`) |
| `delete` | Apenas se o doc existe (idempotência implícita) |

---

## Hop 3 — OutboxPublisher: outbox → SNS

`OutboxPublisher.publishPending` roda em `@Scheduled(fixedDelayString="${outbox.poll-interval-ms:2000}")`.

### Claim atômico

`OutboxEventRepositoryImpl.claimNext(nodeId, lease)` faz `findAndModify`:

- Query: `publishedAt = null`
  AND (`nextAttemptAt = null OR nextAttemptAt <= now`)
  AND (`leaseExpiresAt = null OR leaseExpiresAt <= now`)
- Update: seta `processing_node = nodeId`, `lease_expires_at = now + lease`
- Sort por `created_at` asc (FIFO best-effort)

Garante que múltiplos workers (réplicas do todo-service) não publicam o
mesmo doc — só um vence o `findAndModify`.

### Publicação

`publishOne(event)` em `@Transactional(propagation = REQUIRES_NEW)`:

```java
TodoEvent payload = objectMapper.readValue(event.getPayload(), TodoEvent.class);
Map<String, Object> headers = Map.of("action", event.getEventType());
snsTemplate.convertAndSend(event.getDestination(), payload, headers);
event.markPublished();   // publishedAt = now, limpa lease/lastError
```

`REQUIRES_NEW` via `@Lazy OutboxPublisher self` — a chamada precisa
passar pelo proxy do Spring; `this.publishOne(...)` ignoraria a TX nova.

O header `"action"` vira **SNS message attribute** — é o que alimenta o
`FilterPolicy` das subscriptions no hop 4.

### Falha + backoff

`markFailed(reason, backoffPolicy::nextAttemptAt)`:

- `attempts++`
- `lastError = reason` (truncado em 2000 chars)
- `processing_node = null`, `lease_expires_at = null` (libera para retry)
- `next_attempt_at = now + backoff(attempts)`

`ExponentialJitterBackoffPolicy`: `base * 2^(attempts-1)` com jitter
aleatório (full jitter). Limitado a um máximo configurável.

### Garantia

**At-least-once**: se o publisher crashar entre `convertAndSend` OK e
`markPublished` (gap microscópico mas existente), o lease expira → outro
worker claim'a → republica. SNS recebe a mesma mensagem 2x, com
`MessageId` SQS *diferentes* a jusante. Dedupe é feito no consumer.

---

## Hop 4 — SNS → SQS fan-out (LocalStack)

SNS topic `todo-events` recebe `{ payload, attributes:{action:"CREATED"} }`
e distribui via subscriptions configuradas em `localstack/init-aws.sh`:

```
SNS todo-events ──┬─► todo-created-queue   FilterPolicy {"action":["CREATED"]}
                  ├─► todo-updated-queue   FilterPolicy {"action":["UPDATED"]}
                  ├─► todo-deleted-queue   FilterPolicy {"action":["DELETED"]}
                  └─► todo-audit-queue     sem filtro (recebe tudo)
```

Para um evento `CREATED`, o SNS entrega 2 cópias: `todo-created-queue` e
`todo-audit-queue`. Cada cópia ganha seu próprio `MessageId` SQS.

### Atributos das filas

| Atributo | Valor | Por quê |
|---|---|---|
| `RawMessageDelivery` | `true` | Body chega como JSON puro do `TodoEvent`, sem envelope SNS. Spring Cloud AWS desserializa direto pro POJO |
| `ReceiveMessageWaitTimeSeconds` | `20` | Long polling — reduz API calls e latência |
| `RedrivePolicy.maxReceiveCount` | `3` | Após 3 falhas, mensagem vai pra DLQ correspondente (`<queue>-dlq`) |

---

## Hop 5a — notification-service consome

`TodoEventListener.onTodoCreated` (e `onTodoUpdated`, `onTodoDeleted`):

```java
@SqsListener(SqsConfig.QUEUE_CREATED)
public void onTodoCreated(TodoEvent event, @Header(MessageHeaders.ID) UUID messageId) {
    process(event, messageId);
}
```

`MessageHeaders.ID` é mapeado pelo Spring Cloud AWS direto do
`MessageId` do SQS (não é UUID gerado local).

### Fluxo do `process`

```
1. processed_messages.existsById(messageId)?
   sim → log DEDUPE, retorna (mensagem é ack'd normalmente)
   não → segue
2. emailService.send(event)
   - Protegido por @CircuitBreaker + @Retry
   - Pode lançar EmailDeliveryException (SMTP) ou CallNotPermittedException (CB OPEN)
3. processed_messages.tryInsert(messageId)
   - true  → ok
   - false → log "race detectada" (outra thread também enviou)
```

### Por que dedupe DEPOIS do send

Trade explícito: **duplicar email raro** > **perder email raro**.

- Send falha → exceção sobe → `@SqsListener` não ack'a → SQS reentrega
  após visibility timeout → próxima tentativa repete o fluxo.
- Após 3 entregas falhas → mensagem move pra `todo-created-dlq`.

### Janela de duplicação

Crash entre `send` OK e `tryInsert` → próxima entrega manda email de
novo. Janela típica < 50ms.

### Race condition entre threads concorrentes

Se o visibility timeout for muito curto e o SQS reentregar antes da
primeira thread chegar no `tryInsert`, ambas mandam email. O `tryInsert`
da segunda retorna `false` → log WARN. Sem impacto além do email duplicado.

### DLQ

`TodoEventDlqListener` consome `todo-*-dlq` e loga ERROR — operador
investiga e decide se reprocessa ou descarta.

---

## Hop 5b — audit-service consome (em paralelo)

`TodoEventAuditListener.onTodoEvent`:

```java
@SqsListener(SqsConfig.QUEUE_AUDIT)
public void onTodoEvent(TodoEvent event, @Header(MessageHeaders.ID) UUID messageId) {
    TodoAuditLog log = TodoAuditLog.builder()
            .id(messageId.toString())   // _id = MessageId SQS
            .aggregateId(event.todoId())
            .eventType(event.action())
            // ...
            .build();
    try {
        repository.insert(log);
    } catch (DuplicateKeyException e) {
        // ack normal — era retry
    }
}
```

### Dedupe sem coleção separada

O próprio `insert` com `_id = messageId` é a checagem atômica. Mais
barato e mais correto que ter uma `processed_messages` paralela: não
existe janela entre "checar" e "agir".

### DLQ

`TodoEventAuditDlqListener` — mesmo padrão do notification.

---

## Cenário concreto de falha

SMTP do notification cai por 10min. Linha do tempo:

```
t=0     POST /todos → 201 (Mongo: todo + outbox commitados)
t=2s    OutboxPublisher claim'a → publishOne → SNS OK → markPublished
t=2s    SNS → todo-created-queue + todo-audit-queue
t=2s    audit-service insere log ✓
t=2s    notification-service: send falha → exception → SQS NÃO ack'a
t=32s   reentrega #1 (visibility timeout 30s) → falha
t=62s   reentrega #2 → falha
t=92s   reentrega #3 → falha → move pra todo-created-dlq
        TodoEventDlqListener loga ERROR
```

Estado final:
- Audit log: gravado.
- Outbox: `publishedAt` setado — nada republica.
- Notification: mensagem em DLQ aguardando intervenção.
- Cliente: já recebeu 201 há ~92s — não vê o problema.

Isolamento: falha no notification não afeta audit nem outbox nem cliente.

---

## Onde a entrega pode duplicar e quem cobre

| Onde | Causa | Cobertura |
|---|---|---|
| Outbox publisher crash pós-send | Lease expira, outro worker republica | Dedupe no consumer (MessageId diferente, mas evento idêntico) |
| Multi-worker no outbox | Dois workers tentam claim simultâneo | `findAndModify` atômico — só um vence |
| SQS at-least-once | Reentrega por visibility timeout | `processed_messages` (notification) / `_id = messageId` (audit) |
| Cache do idempotency claim falha | Operação OK mas claim sem `responseBody` | Retry HTTP recebe 409 `IN_PROGRESS` até TTL expirar |

---

## Onde NÃO há cobertura ainda

Do backlog (ver memória `project_todo_service_idempotency.md`):

- `PUT /todos/{id}` e `DELETE /todos/{id}` não aceitam `Idempotency-Key`.
- Idempotência do publish do outbox (ex.: deduplicar `OutboxEvent` lido
  do mesmo doc) só é detectada *no consumer*, não no SNS.
- Sem schema validation no evento — se o `TodoEvent` mudar de forma
  incompatível, consumer quebra na desserialização e vai pra DLQ.

---

## Referências cruzadas

- Patterns: [`outbox`](../../.spec/03-patterns/outbox.md), [`fan-out`](../../.spec/03-patterns/fan-out.md), [`dlq`](../../.spec/03-patterns/dlq.md), [`mongock`](../../.spec/03-patterns/mongock.md)
- SQS internals: [`sqs-template-send`](sqs-template-send.md), [`visibility-timeout`](visibility-timeout.md), [`message-attributes`](message-attributes.md), [`long-polling`](long-polling.md)
- Provisionamento: [`localstack/init-aws.sh`](../../localstack/init-aws.sh)

# Pattern — SNS + SQS Fan-out

Distribuição **1-para-N** de eventos de domínio: o publisher emite uma única mensagem num **topic SNS**, e múltiplas filas SQS inscritas no topic recebem cópias independentes — opcionalmente filtradas por `FilterPolicy`.

Implementado em [`todo-service`](../../todo-service) (publisher → SNS), [`notification-service`](../../notification-service) (3 filas filtradas por `action`), [`audit-service`](../../audit-service) (1 fila sem filtro). Provisionado em [`localstack/init-aws.sh`](../../localstack/init-aws.sh).

---

## Problema que resolve

Antes do fan-out o publisher conhecia cada consumer:

```java
// Acoplamento explícito — toda fila precisa ser conhecida pelo publisher.
outboxService.record("todo-created-queue", ...);
outboxService.record("todo-updated-queue", ...);
outboxService.record("todo-deleted-queue", ...);
```

Adicionar consumer (audit, analytics, webhook) exigia:

1. Criar fila SQS nova.
2. **Modificar o publisher** pra publicar nela também.
3. Coordenar deploy do publisher com o consumer novo.

Resultado: deploys acoplados, acoplamento de tempo entre times, publisher virando "central de routing".

---

## Ideia central

Inverter o conhecimento: o publisher emite **um evento canônico** sem saber quem vai consumir. Os consumers se inscrevem no topic **de fora** (na infra, não no código do publisher).

```
                          ┌─► todo-created-queue ──► notification-service
                          │   (filter: action=CREATED)
                          │
todo-service              ├─► todo-updated-queue ──► notification-service
publisher  ──► [SNS]      │   (filter: action=UPDATED)
            todo-events   │
                          ├─► todo-deleted-queue ──► notification-service
                          │   (filter: action=DELETED)
                          │
                          └─► todo-audit-queue   ──► audit-service
                              (sem filtro — recebe tudo)
```

Adicionar consumer novo: criar fila SQS + subscription no topic. **Zero mudança no `todo-service`**.

---

## Componentes

### 1. Topic SNS `todo-events`

Único point of entry pra eventos de mudança de Todo. Provisionado por [`init-aws.sh`](../../localstack/init-aws.sh):

```bash
awslocal sns create-topic --name todo-events
```

ARN: `arn:aws:sns:us-east-1:000000000000:todo-events` (em LocalStack).

### 2. Subscriptions com `FilterPolicy`

Cada fila SQS é inscrita no topic. A `FilterPolicy` declara quais mensagens interessam, baseado em **message attributes** (não no body):

```bash
awslocal sns set-subscription-attributes \
  --subscription-arn <arn> \
  --attribute-name FilterPolicy \
  --attribute-value '{"action":["CREATED"]}'
```

| Fila | FilterPolicy | Consumer |
|---|---|---|
| `todo-created-queue` | `{"action":["CREATED"]}` | notification-service |
| `todo-updated-queue` | `{"action":["UPDATED"]}` | notification-service |
| `todo-deleted-queue` | `{"action":["DELETED"]}` | notification-service |
| `todo-audit-queue` | _(sem filtro)_ | audit-service |

O filtro acontece **no SNS**, antes de chegar na fila — `notification`'s `created-queue` nunca recebe um `DELETED`. Reduz tráfego, custo e ruído.

### 3. `RawMessageDelivery = true`

Sem isso, o SNS envelopa o payload:

```json
{
  "Type": "Notification",
  "MessageId": "...",
  "Message": "<payload-as-string>",
  ...
}
```

E o consumer precisaria parsear duas vezes (envelope + payload). Com `RawMessageDelivery=true` o body chega direto na fila, atributos SNS viram atributos SQS, e o `@SqsListener` desserializa pro POJO `TodoEvent` direto.

```bash
awslocal sns set-subscription-attributes \
  --subscription-arn <arn> \
  --attribute-name RawMessageDelivery \
  --attribute-value true
```

### 4. Publisher — [`MessagingConfig.java`](../../todo-service/src/main/java/com/microservices/todo/config/MessagingConfig.java) + [`OutboxPublisher.java`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java)

`SnsTemplate` substitui o `SqsTemplate`. O `OutboxPublisher` lê eventos pendentes do outbox e publica no topic com `action` como header (vira message attribute SNS):

```java
Map<String, Object> headers = Map.of("action", event.getEventType());
snsTemplate.convertAndSend(event.getDestination(), payload, headers);
```

`event.getDestination()` agora é `"todo-events"` (constante `MessagingConfig.TOPIC_TODO_EVENTS`), não mais nome de fila. Mongock [V003](../../todo-service/src/main/java/com/microservices/todo/infrastructure/migration/V003_BackfillOutboxDestinationToTopic.java) backfill eventos pendentes da versão pré-fan-out.

### 5. Consumers — sem mudança no notification, novo no audit

**notification-service**: zero mudança no código. Continua consumindo das 3 filas antigas via `@SqsListener` — a fonte da mensagem (SQS direto vs. SNS → SQS) é transparente pro consumer.

**audit-service** ([código completo](../../audit-service)): nova aplicação Spring Boot, listener único na `todo-audit-queue`, persiste em `todo_audit_log` no Mongo. Dedupe natural via `_id = MessageId` (insert duplicado lança `DuplicateKeyException`, trata como "já processado").

---

## Fluxo end-to-end

```
POST /todos                              T+0.000s
  └─ TodoService.create [@TX]
       ├─ repository.save(todo)
       └─ outboxService.record(TOPIC_TODO_EVENTS, "CREATED", event)

@Scheduled OutboxPublisher               T+~2.000s
  ├─ claimNext()                          (lease pattern)
  └─ snsTemplate.convertAndSend(
        "todo-events",
        payload,
        headers={action: "CREATED"})

SNS distribui                            T+~2.001s
  ├─ FilterPolicy match: action=CREATED  → todo-created-queue
  ├─ FilterPolicy NO match               → todo-updated-queue (descartado)
  ├─ FilterPolicy NO match               → todo-deleted-queue (descartado)
  └─ Sem filtro                          → todo-audit-queue

notification-service                     T+~2.500s
  └─ @SqsListener(todo-created-queue) → EmailService.send(...)

audit-service                            T+~2.500s
  └─ @SqsListener(todo-audit-queue) → repository.insert(TodoAuditLog)
```

Os dois consumers processam em **paralelo** — independência total. Falha no email não afeta o audit, e vice-versa.

---

## O que dá pra mudar e o que **não** dá

### Dá pra mudar livre

- **Adicionar consumer novo**: criar fila + DLQ + subscription com `FilterPolicy` no `init-aws.sh`. `todo-service` não muda.
- **Mudar `FilterPolicy`** (ex: notification ignorar `DELETED` por algum motivo): só altera a subscription, sem touchar código.
- **Migrar `audit-service` pra outro DB** (data warehouse, OpenSearch): só o listener muda.

### Cuidado redobrado

- **`RawMessageDelivery=true` em todas as subs**: se esquecer numa, o consumer dela recebe envelope SNS e quebra.
- **`FilterPolicy` mais especifica que message attributes**: se mudar o nome do header (`action` → `eventType`), filtros param de bater silenciosamente — todas as mensagens viram "no match".
- **Aggregate de várias entidades no mesmo topic**: `todo-events` é só pra Todo. Pra Order/Payment/etc, criar topic separado. Misturar leva a `FilterPolicy` complexas e quebra de schema.

### Não funciona

- **Confiar em ordem de entrega** entre filas: SNS standard não garante ordem. Pra ordem, usar SNS FIFO + SQS FIFO (caro, complexo, restritivo).
- **Replay de eventos passados pra subscriber novo**: SNS não armazena histórico. Pra replay, eventos precisam vir do outbox (que ainda existe) ou de um event store. SNS é só pipe.

---

## Sintomas de problema

| Sintoma | Causa provável | Onde olhar |
|---|---|---|
| `audit-service` registra evento mas notification não manda email | `FilterPolicy` da fila do notification não bate com `action` enviado (typo, case-sensitive) | `awslocal sns get-subscription-attributes --subscription-arn ...` |
| Consumer recebe `{"Type":"Notification"...}` em vez do payload | `RawMessageDelivery` não setado | mesmo comando acima — atributo `RawMessageDelivery` deve ser `"true"` |
| Mensagem chega em fila errada | `FilterPolicy` ausente ou mal formada (JSON inválido vira "aceita tudo") | `get-subscription-attributes` no SNS |
| `outbox_events` cresce, publish falha | endpoint SNS errado ou topic inexistente | log do `OutboxPublisher` mostra `attempts++` e `last_error` |
| audit-service grava 2x mesmo evento | `_id` não está sendo setado com `MessageId` no listener | revisar `TodoEventAuditListener` |

---

## Trade-offs registrados

### SNS topic único vs. um topic por tipo de evento

Escolhido: **topic único** (`todo-events`) com `action` como attribute discriminador.

**Por quê**: 1 topic é mais simples — 1 ARN, 1 IAM policy, 1 dashboard. Filtros declarativos via `FilterPolicy`. Adicionar tipo novo de evento (ex: `COMPLETED`) é só usar valor novo no attribute.

**Trade-off**: filtros viram parte essencial da config. Sem `FilterPolicy`, **todos os consumers receberiam todos os eventos** — desperdício de I/O e potencial de bugs de "consumer processando o que não devia".

### Headers como discriminador, não body

`action` no header (message attribute), **não** dentro do payload JSON.

**Por quê**: `FilterPolicy` do SNS só sabe filtrar por attributes. Filtrar por campo do body exigiria function-based filtering (não nativo) ou consumer baseado em conteúdo (perde a vantagem do fan-out).

**Implicação**: o `OutboxPublisher` precisa **duplicar a informação** — `event.getEventType()` vira tanto campo do `TodoEvent` (payload) quanto header (`action`). Custo: 1 linha de código. Benefício: filtros declarativos no SNS.

### Dedupe no audit via `_id = MessageId`

`audit-service` **não** tem tabela `processed_messages` (como o notification). Usa o `_id` natural do Mongo.

**Por quê**: o audit log é append-only por natureza. `DuplicateKeyException` no insert ja é a verificação atômica de "já vi essa mensagem". Não precisa de coleção separada.

**Trade-off**: `_id` é o `MessageId` do SQS (UUID gerado pelo broker), não tem semântica de domínio. Buscar todas as auditorias de um Todo é por `aggregate_id` (indexado em V001).

---

## Quando usar este pattern

**Use** quando:

- Múltiplos consumers reagem ao mesmo evento de domínio (e adicionar mais é cenário esperado).
- Os consumers são independentes — falha de um não deve bloquear os outros.
- Filtros por tipo de evento ou tenant fazem sentido.

**Não use** quando:

- Existe apenas 1 consumer hoje e nenhum plano realista de mais. Fan-out vira complexidade sem benefício.
- Ordem entre consumers importa (ex: audit DEVE registrar antes da notification). Aí o fluxo precisa ser orquestrado, não pub/sub.
- Latência sub-100ms é requisito — SNS adiciona um hop extra (~50–200ms em AWS real).

---

## Referências

- [`03-patterns/outbox.md`](./outbox.md) — par natural: outbox garante "o evento sai do publisher", fan-out garante "chega em todos os interessados".
- [`03-patterns/dlq.md`](./dlq.md) — cada fila do fan-out tem sua própria DLQ.
- [`docs/sqs/sns-fanout.md`](../../docs/sqs/sns-fanout.md) — referência conceitual concisa.
- [AWS — Common Amazon SNS scenarios: fanout](https://docs.aws.amazon.com/sns/latest/dg/sns-common-scenarios.html).

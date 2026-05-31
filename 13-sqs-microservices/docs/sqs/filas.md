# Filas

O que cada fila SQS do projeto representa. Todas vivem em LocalStack e são provisionadas por [`localstack/init-aws.sh`](../../localstack/init-aws.sh).

São **8 filas** no total: 4 principais (uma por tipo de evento + uma de auditoria) e 4 DLQs irmãs (sufixo `-dlq`).

> **Importante**: o `todo-service` **não publica direto nas filas**. Ele registra o evento no outbox com destino igual ao topic SNS `todo-events` (constante `MessagingConfig.TOPIC_TODO_EVENTS` — ver [`TodoService.create/update/delete`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java)). O `OutboxPublisher` lê do outbox e publica no topic. Quem coloca a mensagem em cada fila é o **SNS**, com base no `FilterPolicy` da subscription daquela fila. Padrão: [fan-out](../../.spec/03-patterns/fan-out.md).

---

## Fluxo completo do evento

```
 Cliente
   │  POST /todos
   ▼
┌─────────────────────────────┐
│ todo-service                │
│ 1. Salva o Todo no banco    │
│ 2. Anota o evento numa      │
│    "lista de pendentes"     │
│    (tudo na mesma operação) │
└──────────────┬──────────────┘
               │
               ▼  (a cada 2 segundos)
┌─────────────────────────────┐
│ OutboxPublisher             │
│ Pega o que está pendente    │
│ e entrega ao "carteiro" SNS │
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│ SNS  (o carteiro)           │
│ Olha a etiqueta da mensagem │
│ (action = CREATED / UPDATED │
│  / DELETED) e faz CÓPIAS    │
│ pras filas certas           │
└──┬──────┬──────┬─────────┬──┘
   │      │      │         │
   ▼      ▼      ▼         ▼
created updated deleted  audit       ← as 4 filas (caixas de mensagem)
queue   queue   queue    queue
   │      │      │         │
   ▼      ▼      ▼         ▼
┌───────────────────┐  ┌─────────────┐
│ notification-     │  │ audit-      │
│ service           │  │ service     │
│ (manda email)     │  │ (grava log) │
└───────────────────┘  └─────────────┘

   Se um consumer falhar 3x na mesma mensagem,
   ela vai pra DLQ irmã (ex: todo-created-dlq)
   pra ninguém perder o sinal.
```

### Exemplo: POST /todos { "title": "Estudar SNS" }

1. **todo-service** salva o Todo + anota o evento pendente (mesma operação no banco) e responde 201 ao cliente.
2. Em até 2s, o **OutboxPublisher** vê o pendente e entrega no **SNS** com etiqueta `action=CREATED`.
3. O **SNS** copia a mensagem pra `todo-created-queue` (etiqueta bate) e pra `todo-audit-queue` (recebe tudo). As outras duas filas são ignoradas.
4. O **notification-service** lê a `todo-created-queue` e dispara o email.
5. O **audit-service** lê a `todo-audit-queue` e grava no log de auditoria — **em paralelo** ao passo 4.

---

## Como o SNS conversa com as filas

O SNS é um **topic pub/sub**: quem publica (o producer) joga uma mensagem dentro do topic e **não sabe** quem vai receber. Quem decide "essa mensagem chega em mim" são as **subscriptions** — pequenos registros configurados na infra que ligam uma fila SQS a um topic SNS. No nosso caso, todas as 4 filas principais são "subscribers" do mesmo topic `todo-events`.

### O fluxo concreto, passo a passo

Quando o `OutboxPublisher` precisa publicar um evento de `CREATED`, o código efetivamente faz:

```java
snsTemplate.convertAndSend(
    "todo-events",                            // nome do topic
    payload,                                  // o TodoEvent serializado
    Map.of("action", "CREATED")               // headers → viram message attributes
);
```

A partir daí, **o producer está fora**. O que acontece dentro do SNS:

```
                                   ┌──────────────────────────────────────────────┐
                                   │ SNS topic: todo-events                       │
                                   │                                              │
publish(payload,                   │  Pra cada subscription inscrita no topic:    │
        action=CREATED) ─────────► │   1. Olha o FilterPolicy                     │
                                   │   2. Compara com os message attributes       │
                                   │      da mensagem                             │
                                   │   3. Se bate, entrega uma cópia na fila      │
                                   │      destino daquela subscription            │
                                   └──────┬───────────────────────────────────────┘
                                          │
            ┌─────────────────────────────┼─────────────────────────────┐
            │                             │                             │
            ▼                             ▼                             ▼
  ┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
  │ FilterPolicy:    │         │ FilterPolicy:    │         │ FilterPolicy:    │
  │ action=CREATED   │         │ action=UPDATED   │         │ (nenhuma)        │
  │                  │         │                  │         │                  │
  │  ✓ MATCH         │         │  ✗ skip          │         │  ✓ MATCH (tudo)  │
  └────────┬─────────┘         └──────────────────┘         └────────┬─────────┘
           ▼                                                         ▼
  todo-created-queue                                          todo-audit-queue
  (notification-service)                                      (audit-service)
```

Note que `todo-updated-queue` e `todo-deleted-queue` **também recebem a oferta**, mas o SNS descarta antes de entregar — o filtro não bate. Já a `todo-audit-queue` recebe tudo porque sua subscription não tem `FilterPolicy`.

### Os 3 ingredientes que fazem isso funcionar

1. **A subscription**. É criada uma vez no `init-aws.sh` (`awslocal sns subscribe --topic-arn ... --protocol sqs --notification-endpoint <fila>`). A partir desse momento, o SNS sabe "essa fila quer receber". Sem subscription, a fila simplesmente nunca vê nada do topic, mesmo existindo no mesmo broker.

2. **O message attribute (header) que carrega o critério**. Quando o producer manda `Map.of("action", "CREATED")`, isso vira um **atributo da mensagem** no SNS — separado do body. É justamente esse atributo que o SNS lê pra decidir o roteamento. O critério **precisa** estar nos attributes; `FilterPolicy` não consegue espiar dentro do JSON do body (a não ser que se ative `FilterPolicyScope=MessageBody`, recurso mais novo que o projeto não usa).

3. **O `FilterPolicy` da subscription**. É um pequeno JSON registrado na subscription do tipo `{"action":["CREATED"]}`. Significa: "só me entregue mensagens cujo attribute `action` valha exatamente `CREATED`". Subscription sem `FilterPolicy` = aceita tudo.

### `RawMessageDelivery` — por que o consumer recebe o `TodoEvent` direto

Por default, o SNS enrola o payload num envelope JSON quando entrega na SQS:

```json
{
  "Type": "Notification",
  "MessageId": "...",
  "TopicArn": "...",
  "Message": "<seu payload original como string>",
  "MessageAttributes": { "action": { "Type": "String", "Value": "CREATED" } }
}
```

Isso obrigaria o consumer a desserializar duas vezes — primeiro o envelope, depois o `Message` de dentro. Pra evitar essa fricção, cada subscription do projeto tem `RawMessageDelivery=true`, o que faz o SNS:

- entregar o **body original puro** na fila (o JSON do `TodoEvent` direto, sem envelope);
- transformar os **message attributes do SNS em message attributes da SQS** (o `action` continua acessível pelo consumer, agora como header SQS).

Resultado: o `@SqsListener` do Spring Cloud AWS recebe o `TodoEvent` já tipado, e o `FilterPolicy` continua funcionando porque o attribute foi preservado durante o caminho SNS → SQS.

### Em uma frase

O producer joga 1 mensagem com 1 attribute (`action`) no SNS; o SNS pergunta a cada subscription "esse attribute bate com seu `FilterPolicy`?"; pra cada "sim", grava uma cópia da mensagem na fila daquela subscription; o consumer da fila lê normalmente, sem saber que veio do SNS.

---

## Filas principais

### `todo-created-queue`

Inscrita no SNS `todo-events` com `FilterPolicy = {"action":["CREATED"]}` — o SNS encaminha pra essa fila apenas mensagens cujo header `action` é `CREATED`. Consumida pelo `notification-service`, que dispara o email de "Todo criado".

### `todo-updated-queue`

Inscrita no SNS `todo-events` com `FilterPolicy = {"action":["UPDATED"]}`. Consumida pelo `notification-service` pra mandar o email de "Todo atualizado". PUT no-op no `todo-service` não gera evento no outbox — só mudanças reais (idempotência PR 1.2) — então a fila só vê `UPDATED` de fato.

### `todo-deleted-queue`

Inscrita no SNS `todo-events` com `FilterPolicy = {"action":["DELETED"]}`. Consumida pelo `notification-service` pra mandar o email de "Todo removido". DELETE no `todo-service` é silencioso na segunda chamada (idempotência PR 1.1), então só o primeiro DELETE de cada id chega aqui.

### `todo-audit-queue`

Inscrita no SNS `todo-events` **sem `FilterPolicy`** — recebe todos os eventos (`CREATED`, `UPDATED`, `DELETED`). Consumida pelo `audit-service`, que persiste cada evento como documento append-only em `todo_audit_log` (Mongo). Dedupe via `_id = MessageId` na própria collection.

---

## DLQs

Cada fila principal tem uma DLQ correspondente com `maxReceiveCount=3` na `RedrivePolicy`. Significa: o SQS tenta entregar até 3 vezes; na 4ª, em vez de entregar de novo, move pra DLQ.

### `todo-created-dlq`

Holding pen das mensagens que falharam 3 vezes em `todo-created-queue`. Causas típicas: payload malformado (publisher mudou schema), Mongo fora durante o dedupe do consumer, bug determinístico no handler `onTodoCreated`.

### `todo-updated-dlq`

Equivalente pra `todo-updated-queue`. Mesmas causas; mantida separada pra a inspeção/log ficar por tipo de evento.

### `todo-deleted-dlq`

Equivalente pra `todo-deleted-queue`. Tende a ser a menos movimentada — o payload de `DELETED` é o mais simples (só carrega id), então tem menos superfície pra falhar.

### `todo-audit-dlq`

Holding pen da `todo-audit-queue`. Como o audit-service é append-only e tem dedupe via `_id = MessageId`, o caminho realista pra cair aqui é payload malformado ou Mongo fora durante o insert.

---

## Resumo

| Fila | Tipo | Consumer | FilterPolicy da subscription no SNS `todo-events` |
|---|---|---|---|
| `todo-created-queue` | principal | notification-service | `{"action":["CREATED"]}` |
| `todo-updated-queue` | principal | notification-service | `{"action":["UPDATED"]}` |
| `todo-deleted-queue` | principal | notification-service | `{"action":["DELETED"]}` |
| `todo-audit-queue` | principal | audit-service | (sem filtro — recebe tudo) |
| `todo-created-dlq` | DLQ | — (inspeção manual) | n/a (não é inscrita no SNS) |
| `todo-updated-dlq` | DLQ | — (inspeção manual) | n/a |
| `todo-deleted-dlq` | DLQ | — (inspeção manual) | n/a |
| `todo-audit-dlq` | DLQ | — (inspeção manual) | n/a |

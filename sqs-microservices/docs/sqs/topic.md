# Topic (SNS) — o que é e como entra no fluxo

Topic é o ponto de **publicação único** do padrão pub/sub do AWS SNS
(Simple Notification Service). O publisher envia uma mensagem ao topic,
e o SNS replica a mensagem para **todos os subscribers** inscritos —
SQS queues, e-mails, HTTPS endpoints, Lambdas, etc.

Neste projeto: `todo-service` publica em **um** topic (`todo-events`); o
SNS replica em fan-out para 4 filas SQS (3 com `FilterPolicy`, 1 sem
filtro). É o coração do pattern documentado em [`sns-fanout.md`](./sns-fanout.md)
e [`.spec/03-patterns/fan-out.md`](../../.spec/03-patterns/fan-out.md).

---

## Topic vs Queue

| | **Topic (SNS)** | **Queue (SQS)** |
|---|---|---|
| Padrão | Pub/Sub (1 → N) | Point-to-point (1 → 1) |
| Quem lê | Subscribers (qualquer N) | Um consumer group |
| Mensagem persiste? | Não — entregue na hora pra cada sub | Sim, até consumer dar ack |
| Retry | Por subscriber, isolado | Visibility timeout + redrive |
| Filtro | `FilterPolicy` por subscription | Não tem (precisa de outra fila) |

Resumo: **topic distribui**, **queue armazena**. Por isso quase sempre
aparecem juntos — `SNS → SQS` é o padrão clássico de fan-out durável.

---

## Como o topic entra no fluxo deste projeto

Provisionado em [`localstack/init-aws.sh`](../../localstack/init-aws.sh):

```
todo-service ──► SNS topic todo-events ──┬─► todo-created-queue   (filter: action=CREATED)
                                         ├─► todo-updated-queue   (filter: action=UPDATED)
                                         ├─► todo-deleted-queue   (filter: action=DELETED)
                                         └─► todo-audit-queue     (sem filtro)
```

### Quem publica

`OutboxPublisher.publishOne` (`todo-service`):

```java
Map<String, Object> headers = Map.of("action", event.getEventType());
snsTemplate.convertAndSend("todo-events", payload, headers);
```

- O **nome do topic** (`todo-events`) é definido em `MessagingConfig.TOPIC_TODO_EVENTS`.
- O header `"action"` é traduzido pelo Spring Cloud AWS num **SNS message attribute** —
  é o que o `FilterPolicy` das subscriptions consulta para decidir entrega.

### Quem consome

Ninguém consome o topic diretamente. Os consumers (`notification-service`,
`audit-service`) escutam as **filas SQS** alimentadas pelo topic. O topic é só o
roteador.

---

## ARN do topic

Formato:

```
arn:aws:sns:<region>:<account-id>:<topic-name>
```

Neste projeto (LocalStack):

```
arn:aws:sns:us-east-1:000000000000:todo-events
```

LocalStack usa account ID fixo `000000000000` e aceita qualquer credencial
(`test`/`test`). Em AWS real, o account ID é da sua conta.

---

## Subscription — onde mora a configuração de roteamento

Topic em si é "burro": só recebe e distribui. As **subscriptions** é que
têm a inteligência:

| Atributo | O que controla |
|---|---|
| `Protocol` + `Endpoint` | Pra onde entregar (ex.: `sqs` + ARN da fila) |
| `FilterPolicy` | JSON que decide se a mensagem é entregue ou descartada para *esta* sub |
| `RawMessageDelivery` | Se `true`, entrega o body bruto (sem envelope SNS) |
| `RedrivePolicy` | DLQ da subscription (separada da DLQ da fila) |

Exemplo do init script:

```bash
awslocal sns subscribe \
  --topic-arn "arn:aws:sns:us-east-1:000000000000:todo-events" \
  --protocol sqs \
  --notification-endpoint "arn:aws:sqs:us-east-1:000000000000:todo-created-queue"

awslocal sns set-subscription-attributes \
  --subscription-arn "<sub-arn>" \
  --attribute-name FilterPolicy \
  --attribute-value '{"action":["CREATED"]}'

awslocal sns set-subscription-attributes \
  --subscription-arn "<sub-arn>" \
  --attribute-name RawMessageDelivery \
  --attribute-value true
```

---

## FilterPolicy — fan-out seletivo

Permite que **uma sub só receba mensagens que casem com um filtro**, em
vez de receber tudo. Avaliação é feita do lado do SNS: mensagens que não
casam **nunca chegam na fila**.

Sintaxe é JSON onde a chave é o nome do message attribute e o valor é
um array de matches:

```json
{ "action": ["CREATED"] }                       // bate só CREATED
{ "action": ["CREATED", "UPDATED"] }            // bate qualquer um dos dois
{ "action": [{"anything-but": "DELETED"}] }     // qualquer coisa exceto DELETED
{ "priority": [{"numeric": [">", 5]}] }         // numérico
```

Neste projeto, separação por `action` permite escalar/configurar cada
fila independentemente:

- `todo-created-queue` pode ter mais consumers (alto volume na criação).
- `todo-deleted-queue` pode ter visibility timeout maior (se delete dispara
  job pesado).

Sem `FilterPolicy`: subscriber teria que filtrar na aplicação, gastando
banda + CPU pra descartar mensagens que não interessam.

---

## RawMessageDelivery

Sem essa flag, o SQS recebe um **envelope SNS** envolvendo o body real:

```json
{
  "Type": "Notification",
  "MessageId": "...",
  "TopicArn": "...",
  "Message": "{\"todoId\":\"...\",\"action\":\"CREATED\",...}",
  "MessageAttributes": { ... },
  "Timestamp": "..."
}
```

Consumer teria que fazer parsing duplo (JSON do envelope → string
escapada → JSON do payload real). Com `RawMessageDelivery=true`, o
envelope é descartado e o SQS guarda **só o body**:

```json
{"todoId":"...","action":"CREATED","title":"...","occurredAt":"..."}
```

Spring Cloud AWS desserializa direto pro POJO `TodoEvent` no listener.

---

## Custos (AWS real, não LocalStack)

- $0.50 por **milhão de publicações** no topic (independente do número de subs).
- $0.40 por **milhão de requests** no SQS para entregas (SNS → SQS).
- Mensagem `> 64 KB` é cobrada como múltiplas requests (por blocos de 64 KB).

Boas práticas de custo:
- Use `FilterPolicy` em vez de "publicar várias vezes em topics diferentes".
- Compacte payloads grandes (gzip) ou guarde em S3 e mande só a chave (SNS-S3 extended client).

---

## Limitações que importam neste projeto

| Limite | Valor | Impacto aqui |
|---|---|---|
| Payload max | 256 KB | `TodoEvent` é tiny, sem risco |
| Atributos por mensagem | 10 | Só usamos 1 (`action`) |
| Subscriptions por topic | 12.5 milhões | Irrelevante |
| Throughput | Praticamente ilimitado para Standard | Irrelevante |
| Ordem de entrega | **Não garantida** em SNS Standard | Para ordering, usar SNS FIFO (não usado aqui) |
| Entrega | **At-least-once** | Por isso o dedupe nos consumers (`processed_messages` / `_id = messageId`) |

---

## Quando criar um topic novo neste projeto

Adicione um topic separado quando os subscribers **não compartilham nenhum filtro**
do conjunto existente. Exemplos hipotéticos:

| Caso | Topic |
|---|---|
| Novo evento `UserSignedUp` | Topic novo (`user-events`) — não tem nada a ver com Todo |
| Novo subscriber pra eventos `UPDATED` | Mesmo topic, nova subscription com `FilterPolicy={"action":["UPDATED"]}` |
| Variação de payload (ex.: `TodoEventV2`) | Mesmo topic, atributo novo (`version`) no filtro, ou topic novo se for breaking |

---

## Referências

- Provisionamento: [`localstack/init-aws.sh`](../../localstack/init-aws.sh)
- Pattern fan-out: [`sns-fanout.md`](./sns-fanout.md), [`.spec/03-patterns/fan-out.md`](../../.spec/03-patterns/fan-out.md)
- Publisher: `todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java`
- Config: `todo-service/src/main/java/com/microservices/todo/config/MessagingConfig.java`
- Docs relacionados: [`message-attributes.md`](./message-attributes.md), [`sqs-template-send.md`](./sqs-template-send.md), [`filas.md`](./filas.md)

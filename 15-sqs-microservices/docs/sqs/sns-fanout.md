# SNS + SQS Fan-out

Padrão de **distribuição 1-para-N**: publisher manda 1 mensagem num **topic SNS**, várias filas SQS inscritas no topic recebem cópias — opcionalmente filtradas.

Referência ao projeto: [`localstack/init-aws.sh`](../../localstack/init-aws.sh), [`MessagingConfig.java`](../../todo-service/src/main/java/com/microservices/todo/config/MessagingConfig.java), [`audit-service`](../../audit-service). Pattern detalhado: [`.spec/03-patterns/fan-out.md`](../../.spec/03-patterns/fan-out.md).

---

## Por que existe

Publisher direto em N filas significa que o publisher **conhece cada consumer**. Adicionar consumer = mexer no publisher.

Fan-out inverte: publisher só conhece o **topic**. Consumers se inscrevem **na infra**.

```
ANTES:  publisher → 3 filas (publisher conhece cada uma)
DEPOIS: publisher → 1 topic → N filas (publisher só conhece o topic)
```

---

## Componentes

| Recurso AWS | Função |
|---|---|
| **SNS topic** | Ponto único de publicação. Não armazena mensagens — só distribui. |
| **SQS subscription** | "Esta fila quer receber mensagens deste topic". |
| **FilterPolicy** | JSON na subscription que decide quais mensagens essa fila quer (filtra por message attribute). |
| **RawMessageDelivery** | `true` faz a fila receber o payload bruto; `false` envelopa em metadata SNS. |

---

## Fluxo

```
publisher
   │
   ▼ SNS.publish(topic, payload, attributes)
[SNS topic]
   │
   ├─► FilterPolicy match  → SQS queue A ──► consumer A
   ├─► FilterPolicy match  → SQS queue B ──► consumer B
   ├─► FilterPolicy no match → descartado
   └─► sem FilterPolicy → SQS queue C ──► consumer C (recebe tudo)
```

Filtro acontece **no SNS**, antes de chegar na fila. Fila não vê o que filtrou.

---

## FilterPolicy

Filtra baseado em **message attributes** (não no body):

```json
{
  "action": ["CREATED", "UPDATED"]
}
```

Significa: essa fila só recebe mensagens com attribute `action` igual a `CREATED` ou `UPDATED`. Operadores suportados: lista, prefix, anything-but, numeric ranges, exists.

> **Pegadinha**: filtrar por **campo do body** não é nativo. Tem que estar em attribute. Por isso o publisher precisa duplicar o discriminador (vai no body **e** no header).

---

## RawMessageDelivery

| Valor | O que a fila recebe |
|---|---|
| `false` (default) | `{"Type":"Notification","Message":"<payload-string>","MessageAttributes":...}` |
| `true` | `<payload>` direto, attributes SNS viraram attributes SQS |

**Sempre usar `true`** em fan-out com consumer Spring/SDK típico — evita parsing duplo.

---

## Configuração no projeto

3 consumers conectados ao topic `todo-events`:

| Fila | FilterPolicy | Consumer |
|---|---|---|
| `todo-created-queue` | `{"action":["CREATED"]}` | notification-service |
| `todo-updated-queue` | `{"action":["UPDATED"]}` | notification-service |
| `todo-deleted-queue` | `{"action":["DELETED"]}` | notification-service |
| `todo-audit-queue` | _(sem filtro)_ | audit-service |

Publicar via `SnsTemplate`:

```java
Map<String, Object> headers = Map.of("action", "CREATED");
snsTemplate.convertAndSend("todo-events", payload, headers);
```

---

## Comandos úteis

```powershell
# listar topics
docker exec localstack awslocal sns list-topics

# listar subscriptions de um topic
docker exec localstack awslocal sns list-subscriptions-by-topic `
  --topic-arn arn:aws:sns:us-east-1:000000000000:todo-events

# ver atributos de uma subscription (FilterPolicy, RawMessageDelivery)
docker exec localstack awslocal sns get-subscription-attributes `
  --subscription-arn <arn>

# publicar manualmente (teste)
docker exec localstack awslocal sns publish `
  --topic-arn arn:aws:sns:us-east-1:000000000000:todo-events `
  --message '{"todoId":"x","title":"y","action":"CREATED","occurredAt":"2026-05-23T10:00:00"}' `
  --message-attributes '{"action":{"DataType":"String","StringValue":"CREATED"}}'
```

> `--message-attributes` precisa do `action` setado, senão a `FilterPolicy` rejeita e nenhuma fila filtrada recebe.

---

## Cuidados

- **`FilterPolicy` é case-sensitive**: `"CREATED"` ≠ `"created"`.
- **Sem `action` attribute = filtros não batem**: mensagem entra no topic mas é descartada. Auditar via SNS publish OK + nenhuma fila ApproximateNumberOfMessages++.
- **`RawMessageDelivery=false` quebra o `@SqsListener`**: o body chega como envelope SNS, deserialize falha.
- **Topic não armazena**: novo subscriber só recebe eventos futuros, não passados. Pra replay, usar outbox/event store.
- **Subscription pode ficar "pending confirmation"** em AWS real com HTTP/email — pra SQS é automático.

---

## Referências

- [`.spec/03-patterns/fan-out.md`](../../.spec/03-patterns/fan-out.md) — pattern do projeto.
- [`docs/sqs/dlq.md`](./dlq.md), [`docs/sqs/message-attributes.md`](./message-attributes.md).
- [AWS — SNS message filtering](https://docs.aws.amazon.com/sns/latest/dg/sns-message-filtering.html).
- [AWS — Raw message delivery](https://docs.aws.amazon.com/sns/latest/dg/sns-large-payload-raw-message-delivery.html).

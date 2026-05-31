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

## O que é um topic

Um **topic SNS** é um **canal de broadcast nomeado**. É só isso: um endereço pra onde se publica uma mensagem **uma vez**, e o SNS se encarrega de **entregar cópias** pra cada destino inscrito nele. No projeto, o topic se chama `todo-events` (ARN `arn:aws:sns:us-east-1:000000000000:todo-events`).

Características essenciais:

| Propriedade | Comportamento |
|---|---|
| **Pub/sub puro** | Quem publica não conhece quem consome. Adicionar/remover consumer = mexer na infra (subscription), nunca no publisher. |
| **1-para-N por design** | Cada `publish` vira N entregas, uma por subscription inscrita. Se 4 filas estão inscritas, o topic gera até 4 cópias da mensagem (FilterPolicy pode reduzir). |
| **Não armazena** | Topic é **pipe**, não buffer. Mensagem que chega num topic **sem subscriptions** se perde — não tem "inbox" pra leitura futura. Pra durabilidade, a fila SQS na ponta segura. |
| **Sem ordem garantida** | SNS Standard não preserva ordem entre mensagens. Pra ordenação, existe SNS FIFO (caro, restritivo, fora do projeto). |
| **Múltiplos protocolos** | Subscriptions podem ser SQS, HTTP/S, Lambda, email, SMS, mobile push. No projeto só usamos `--protocol sqs`. |
| **Identificado por ARN** | Toda operação (publish, subscribe, listar) referencia o topic pelo ARN completo, não pelo nome curto. |

### Diferença chave: topic vs fila

| | SNS topic | SQS queue |
|---|---|---|
| Modelo | Pub/sub (broadcast) | Ponto-a-ponto (work queue) |
| Armazena? | **Não** — distribui na hora | **Sim** — segura até consumer ler/deletar |
| Entregas por `publish` | N (uma por subscription) | 1 (a mensagem ocupa um slot até alguém pegar) |
| Quem consome? | Não consome — apenas distribui | Um consumer ack/deleta a mensagem |

É por isso que o padrão **fan-out** combina os dois: topic faz o "broadcast pra quem quiser ouvir", e cada fila inscrita oferece **buffer + at-least-once + DLQ** pro seu consumer.

### No fluxo do projeto

```
todo-service                    OutboxPublisher
   │                                  │
   ▼ outboxService.record(            ▼ snsTemplate.convertAndSend(
       "todo-events", ...)               "todo-events", payload,
                                          Map.of("action","CREATED"))

                          ┌────► [SNS topic: todo-events] ◄────┐
                          │                                    │
                          │  Não persiste. Olha cada            │
                          │  subscription. Entrega cópia        │
                          │  pras que casam com FilterPolicy.   │
                          │                                    │
                          ▼                                    ▼
               todo-{created,updated,deleted}-queue    todo-audit-queue
```

`MessagingConfig.TOPIC_TODO_EVENTS = "todo-events"` no `todo-service` é a única referência ao topic no código. Trocar de SNS pra Kafka seria mudar essa constante + o template — `TodoService` não precisa saber.

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

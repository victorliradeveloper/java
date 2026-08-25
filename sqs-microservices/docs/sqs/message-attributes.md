# Message Attributes no SQS

Metadados estruturados anexados a uma mensagem **fora do body**. Funcionam como headers HTTP: chave/valor com tipo, separados do payload de negócio.

Limite: até 10 attributes por mensagem, soma de tamanho ≤ 256 KB (junto com o body).

---

## Envelope vs payload

```
┌────────────────────────────────────┐
│ Mensagem SQS                       │
│ ┌────────────────────────────────┐ │
│ │ MessageAttributes (envelope)   │ │ ← infra / observabilidade
│ │   trace-id, tenant, version    │ │
│ └────────────────────────────────┘ │
│ ┌────────────────────────────────┐ │
│ │ Body (payload)                 │ │ ← domínio (negócio)
│ │   TodoEvent { todoId, title }  │ │
│ └────────────────────────────────┘ │
└────────────────────────────────────┘
```

Regra: **negócio no body, infra nos attributes**.

---

## Quando usar

| Use attributes para... | Use no body para... |
|---|---|
| `trace-id` (distributed tracing) | dados de domínio (`todoId`, `title`) |
| `tenant` / `org-id` (multi-tenancy) | campos que entram em lógica de negócio |
| `event-version` (schema migration) | qualquer coisa que o handler valida |
| `source-service` (quem publicou) | |
| Filtros de SNS → SQS (`FilterPolicy`) | |

**Não use** pra dados que mudam a lógica do handler — ficam escondidos e difíceis de testar.

---

## Formato

```json
{
  "trace-id": {
    "DataType": "String",
    "StringValue": "abc-123"
  },
  "retry-count": {
    "DataType": "Number",
    "StringValue": "2"
  }
}
```

`DataType` aceito: `String`, `Number`, `Binary`. Mesmo `Number` usa o campo `StringValue` (não existe `NumberValue` na API SQS).

---

## Publicar (Spring Cloud AWS)

```java
sqsTemplate.send(to -> to
    .queue(queueName)
    .payload(event)
    .header("trace-id", MDC.get("traceId"))
    .header("source-service", "todo-service"));
```

A forma curta `send(queue, payload)` **não** suporta headers — precisa do builder.

---

## Consumir

```java
@SqsListener(SqsConfig.QUEUE_CREATED)
public void onTodoCreated(TodoEvent event,
                          @Header(MessageHeaders.ID) UUID messageId,
                          @Header(name = "trace-id", required = false) String traceId) {
    log.info("[NOTIFICATION] trace={} todoId={}", traceId, event.todoId());
}
```

`required = false` evita erro quando mensagens antigas (publicadas antes do attribute existir) chegam sem o header.

---

## Em terminal (uso raro)

```powershell
$body  = '{"todoId":"test","title":"x","action":"CREATED","occurredAt":"2026-05-23T10:00:00"}'
$attrs = '{"trace-id":{"DataType":"String","StringValue":"abc-123"}}'

docker exec localstack awslocal sqs send-message `
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/todo-created-queue `
  --message-body $body `
  --message-attributes $attrs
```

Use só pra:
- Testar listener que lê `@Header` recém-adicionado.
- Reproduzir bug com attribute específico.
- Simular publisher sem subir o serviço real.

No fluxo normal de dev, **publisher real** (`todo-service`) emite os attributes via código.

---

## Cuidados

- **Custo**: na AWS real, attributes contam pro tamanho da mensagem (preço por 64 KB). Atributo grande = mensagem mais cara.
- **Não confundir com `MessageSystemAttributes`**: aqueles são gerenciados pelo SQS (ex: `AWSTraceHeader` pro X-Ray) e têm campos fixos.
- **No projeto hoje**: o `todo-service` **não publica** attributes — só body. Adicionar `trace-id` é evolução natural quando integrar observability (OpenTelemetry, Datadog).

---

## Referências

- [`docs/sqs/dlq.md`](./dlq.md) e [`docs/sqs/visibility-timeout.md`](./visibility-timeout.md).
- [AWS — Amazon SQS message attributes](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-metadata.html#sqs-message-attributes).

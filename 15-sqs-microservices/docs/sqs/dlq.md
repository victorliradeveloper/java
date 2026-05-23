# Dead Letter Queue (DLQ) no SQS

Fila secundária que recebe automaticamente mensagens que falharam N vezes seguidas no consumer. É a "quarentena": isola o que está quebrado sem perder a evidência.

Referência ao projeto: [`localstack/init-aws.sh`](../../localstack/init-aws.sh) e [`TodoEventDlqListener.java`](../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventDlqListener.java). Detalhes em [`.spec/03-patterns/dlq.md`](../../.spec/03-patterns/dlq.md).

---

## Por que existe

Sem DLQ, uma mensagem que **sempre falha** fica em loop:

```
receive → handler throws → no ack → visibility timeout → receive → throws → ...
```

Resultado: CPU desperdiçada, log spam, fila principal entope. DLQ corta isso: depois de N tentativas, o SQS move a mensagem pra fila secundária e segue a vida.

Causas comuns: payload malformado, schema drift entre publisher e consumer, bug em caso de borda, dependência fora.

---

## Como funciona

Configura-se uma `RedrivePolicy` **na fila principal** apontando pra DLQ:

```json
{
  "deadLetterTargetArn": "arn:aws:sqs:us-east-1:123456789012:todo-created-dlq",
  "maxReceiveCount": "3"
}
```

`maxReceiveCount` conta **entregas pelo broker** (cada `ReceiveMessage` sem `DeleteMessage` antes do `VisibilityTimeout`). Excedeu, o SQS move automaticamente — sem código do consumer.

```
fila principal
  ├─ entrega 1: consumer falha
  ├─ entrega 2: consumer falha
  ├─ entrega 3: consumer falha
  └─ entrega 4: SQS move pra DLQ
```

---

## Utilidade

- **Isola sem perder evidência** — mensagem ruim sai do caminho mas fica retida pra debug.
- **Drena pico de erros** — quando uma dependência cai, falhas vão pra DLQ; a fila principal continua aceitando novas mensagens.
- **Habilita alarme** — em produção: alarme em `ApproximateNumberOfMessagesVisible > 0` da DLQ vira o sinal principal de erro no consumer.
- **Permite redrive** — corrigiu o bug, manda tudo da DLQ de volta pra fila principal e reprocessa.

---

## Configuração no projeto

3 filas principais → 3 DLQs correspondentes, `maxReceiveCount=3`. Criadas em [`localstack/init-aws.sh`](../../localstack/init-aws.sh).

Listener das DLQs ([`TodoEventDlqListener.java`](../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventDlqListener.java)) recebe `String` (body pode estar malformado) e loga `WARN`. Em produção esse listener seria substituído por alarme + inspeção manual.

---

## Comandos úteis

```powershell
# profundidade da DLQ
docker exec localstack awslocal sqs get-queue-attributes `
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/todo-created-dlq `
  --attribute-names ApproximateNumberOfMessages

# espiar mensagem sem consumir
docker exec localstack awslocal sqs receive-message `
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/todo-created-dlq `
  --visibility-timeout 0

# devolver tudo pra fila principal (depois de corrigir o bug)
docker exec localstack awslocal sqs start-message-move-task `
  --source-arn arn:aws:sqs:us-east-1:000000000000:todo-created-dlq
```

---

## Cuidados

- **Não cobre tudo**: no projeto, falhas de SMTP **não** caem na DLQ — o dedupe grava `processed_messages` antes do envio, então o retry é descartado antes da 3ª entrega. Design explícito ("perde raro" vs "duplica raro"), ver [`.spec/01-issues/closed/idempotency.md`](../../.spec/01-issues/closed/idempotency.md).
- **DLQ não tem DLQ**: se o listener da DLQ falhar, a mensagem volta pra própria DLQ.
- **Retenção**: padrão 4 dias (máx 14). Em produção, setar pra 14 — sem evidência preservada, DLQ vira arquivo morto.
- **`maxReceiveCount`**: 3–5 é o sweet spot. Baixo demais = falso positivo em falha transiente; alto demais = demora pra perceber.

---

## Referências

- [`.spec/03-patterns/dlq.md`](../../.spec/03-patterns/dlq.md) — pattern do projeto (trade-offs, sintomas).
- [AWS — Amazon SQS dead-letter queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html).

# Long Polling no SQS

Controla se o `ReceiveMessage` retorna **imediatamente** (short polling) ou **aguarda** até uma mensagem chegar (long polling). Definido pelo atributo `ReceiveMessageWaitTimeSeconds` (0 a 20 segundos).

---

## Short vs long polling

| Modo | Valor | Comportamento |
|---|---|---|
| **Short polling** (default) | `0` | Retorna imediatamente, mesmo com fila vazia. |
| **Long polling** | `1`–`20` | Espera dentro do SQS até uma mensagem chegar ou o timeout estourar. |

---

## O problema do short polling

```
T+0.0s   ReceiveMessage → []
T+0.1s   ReceiveMessage → []
T+0.2s   ReceiveMessage → []
T+0.3s   ReceiveMessage → [msg] ✓
```

Consumer dispara centenas de chamadas vazias por minuto. Em AWS real, **cada chamada custa** ($0.40 / milhão de requests).

---

## Long polling resolve

```
T+0.0s    ReceiveMessage (aguarda...)
T+8.5s    ← mensagem chega
T+8.5s    ReceiveMessage retorna [msg] ✓
```

- **Menos chamadas**: 1 a cada 20s em fila vazia, em vez de centenas.
- **Latência menor**: SQS devolve assim que a mensagem chega, sem esperar próximo ciclo.
- **Custo**: redução típica de 90%+ em filas pouco movimentadas.

---

## Quando usar

| Situação | Long polling? |
|---|---|
| Consumer em produção AWS | ✅ **Sempre** — recomendação oficial AWS |
| Fila pouco movimentada | ✅ Maior economia |
| Fila com tráfego alto (sempre tem mensagem) | ✅ OK — retorna na 1ª mensagem, sem aguardar |
| LocalStack / dev | ⚠️ Tanto faz, mas configurar igual prod é boa prática |
| `receive-message` ad-hoc via CLI | ❌ Use `--wait-time-seconds 0` pra resposta imediata |

**Recomendação universal**: setar `ReceiveMessageWaitTimeSeconds=20` em todas as filas em produção.

---

## Configurar

Por fila (atributo persistente):

```powershell
docker exec localstack awslocal sqs set-queue-attributes `
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/todo-created-queue `
  --attributes ReceiveMessageWaitTimeSeconds=20
```

Por chamada (sobrescreve o atributo da fila):

```powershell
docker exec localstack awslocal sqs receive-message `
  --queue-url ... `
  --wait-time-seconds 20
```

---

## No projeto

Spring Cloud AWS `@SqsListener` já usa **long polling por default** no container do listener. Setar no nível da fila é **defesa em profundidade**: qualquer consumer (Spring, CLI, outro SDK) herda o comportamento.

Vale incluir no [`init-aws.sh`](../../localstack/init-aws.sh) pro setup ficar idêntico a prod.

---

## Cuidados

- **Não confunda com `VisibilityTimeout`**: long polling é "espera pra receber"; visibility é "lock depois de receber".
- **Limite máximo: 20s**. Pra esperas maiores, use SNS → Lambda ou EventBridge.
- **`--wait-time-seconds` da request sobrescreve o atributo da fila** — por isso `receive-message --wait-time-seconds 0` continua retornando na hora.

---

## Referências

- [`docs/sqs/dlq.md`](./dlq.md), [`docs/sqs/visibility-timeout.md`](./visibility-timeout.md), [`docs/sqs/message-attributes.md`](./message-attributes.md).
- [AWS — Short and long polling](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-short-and-long-polling.html).

# Visibility Timeout no SQS

Tempo durante o qual uma mensagem fica **invisível** pra outros consumers depois de ser entregue. Se o consumer não deletar a mensagem antes do timeout expirar, ela volta pra fila e pode ser reprocessada — possivelmente por outro consumer, em paralelo.

Default da fila: **30 segundos**. Ajustável por fila (`SetQueueAttributes`) ou por mensagem em runtime (`ChangeMessageVisibility`).

---

## Por que existe

Garante que **um consumer por vez** processe uma mensagem. Sem isso, todo `ReceiveMessage` entregaria a mesma mensagem repetidamente em paralelo, causando duplicação massiva.

---

## O problema do timeout fixo

O default de 30s é uma estimativa — o tempo real de processamento varia:

```
T+0s    receive-message    → NotVisible por 30s
T+0s    consumer começa
T+45s   consumer ainda processando...
T+30s   SQS devolve a mensagem pra fila (Visible de novo)
T+30s   OUTRO consumer pega → DUPLICAÇÃO
```

Se o processamento for mais lento que o timeout, dois (ou mais) consumers acabam processando a mesma mensagem simultaneamente.

---

## ChangeMessageVisibility — estender o lock dinamicamente

O consumer detecta que vai demorar e renova o lock:

```
T+0s    receive-message    → NotVisible por 30s
T+25s   change-message-visibility(120)
        → SQS prorroga: NotVisible até T+145s
T+90s   consumer termina + delete-message ✅
```

Em Spring Cloud AWS, o framework injeta o `Visibility`:

```java
@SqsListener(QUEUE_NAME)
public void handle(Event event, Visibility visibility) {
    if (vaiDemorar(event)) {
        visibility.changeTo(120);  // estende pra 120s
    }
    processar(event);
}
```

---

## Casos de uso

| Caso | Ação |
|---|---|
| Processamento longo (>timeout default) | Estender via `Visibility.changeTo(N)` no listener |
| Heartbeat em loop de processamento | Renovar visibility a cada batch processado |
| Backoff customizado em retry transiente | `changeTo(300)` antes de lançar exceção — adia próxima entrega |
| Yield voluntário (devolver pra fila) | `changeTo(0)` — outro consumer pega imediatamente |
| Pausar poison message em loop (operação) | `change-message-visibility 3600` via terminal, dá tempo de investigar |

---

## Uso em terminal

Quase nunca em dev local. Cenários reais:

```powershell
# pausa uma mensagem específica por 1h (debug em prod)
docker exec localstack awslocal sqs change-message-visibility `
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/todo-created-queue `
  --receipt-handle "AQEB..." `
  --visibility-timeout 3600

# devolve uma mensagem AGORA pra fila (libera o lock)
docker exec localstack awslocal sqs change-message-visibility `
  --queue-url ... `
  --receipt-handle "AQEB..." `
  --visibility-timeout 0
```

O `ReceiptHandle` vem de uma chamada anterior de `receive-message`.

---

## No projeto

Hoje **nenhum listener precisa estender visibility**. Operações são rápidas:

- Insert em `processed_messages` (Mongo, <50ms)
- Render Thymeleaf (<10ms)
- Envio SMTP Gmail (~1–2s)

Total bem abaixo dos 30s default. Margem confortável.

**Quando passaria a precisar:**

- Processamento pesado adicionado (PDF, batch grande, API externa lenta).
- Fila migrada pra FIFO com `messageGroupId` — duplicação por timeout estouro fica mais grave (quebra ordem).
- Retry com backoff customizado em vez do default do SQS.

---

## Cuidados

- **Timeout muito curto**: duplicação por reprocessamento paralelo.
- **Timeout muito longo**: se o consumer crashar, a mensagem fica "presa" até o timeout expirar — atraso grande até outro consumer pegar.
- **`ChangeMessageVisibility` só funciona dentro do timeout vigente**: se já expirou, retorna erro `MessageNotInflight`.
- **Não confundir com retenção** (`MessageRetentionPeriod`, padrão 4 dias): visibility é o lock; retenção é quanto tempo a mensagem fica na fila no total.

---

## Referências

- [`docs/sqs/dlq.md`](./dlq.md) — DLQ usa `maxReceiveCount`, que conta entregas separadas pelo timeout.
- [AWS — Amazon SQS visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html).

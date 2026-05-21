# `SqsTemplate.send(...)` — parâmetros e overloads

Referência ao código deste projeto: `todo-service/src/main/java/com/microservices/todo/service/TodoService.java:57-59`

```java
private void publish(String queueName, TodoEvent event) {
    sqsTemplate.send(queueName, event);
}
```

Essa é a forma mais curta de **3 overloads** que o `SqsTemplate` da awspring (`io.awspring.cloud.sqs.operations.SqsTemplate`) expõe.

---

## 1. Overloads disponíveis

```java
// (a) Forma curta usada hoje — fila + payload
SendResult<T> send(String queue, T payload);

// (b) Só payload — usa a fila definida em SqsTemplate.builder().defaultQueue(...)
SendResult<T> send(T payload);

// (c) Builder fluente — abre acesso a TODOS os parâmetros
SendResult<T> send(Consumer<SqsSendOptions<T>> to);
```

Versões assíncronas (retornam `CompletableFuture<SendResult<T>>`):

```java
CompletableFuture<SendResult<T>> sendAsync(String queue, T payload);
CompletableFuture<SendResult<T>> sendAsync(T payload);
CompletableFuture<SendResult<T>> sendAsync(Consumer<SqsSendOptions<T>> to);
```

Envio em lote:

```java
SendResult.Batch<T> sendMany(String queue, Collection<Message<T>> messages);
```

---

## 2. Parâmetros do builder (overload `c`)

É o único caminho para acessar headers, delay e atributos FIFO:

```java
sqsTemplate.send(to -> to
        .queue(queueName)
        .payload(event)
        // ...opções abaixo
);
```

| Método em `SqsSendOptions<T>`     | O que faz                                                                          | Quando usar                                                       |
| --------------------------------- | ---------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| `.queue(String)`                  | Nome (ou URL) da fila destino                                                      | Sempre, se não usar `defaultQueue`                                |
| `.payload(T)`                     | Corpo da mensagem; serializado pelo `MessageConverter` (Jackson neste projeto)     | Sempre                                                            |
| `.header(String, Object)`         | Adiciona **um** header / message attribute                                         | Tracing, correlation-id, source, etc.                             |
| `.headers(Map<String,Object>)`    | Adiciona vários headers de uma vez                                                 | Propagar contexto inteiro                                         |
| `.delaySeconds(Integer)`          | Atraso na visibilidade da mensagem (0–900 s)                                       | Schedule simples sem usar EventBridge                             |
| `.messageGroupId(String)`         | **Obrigatório em FIFO**; mensagens com o mesmo grupo são processadas em ordem      | Somente em filas `.fifo`                                          |
| `.messageDeduplicationId(String)` | Janela de 5 min de dedup em FIFO                                                   | Em FIFO, a menos que a fila tenha `ContentBasedDeduplication=true` |

> `delaySeconds`, `messageGroupId` e `messageDeduplicationId` **só existem via builder**. A forma curta `send(queue, payload)` não permite passá-los — por isso, se uma fila deste projeto migrar para FIFO, o `publish` atual precisa virar a forma fluente.

---

## 3. Retorno — `SendResult<T>`

| Campo                       | Conteúdo                                                                |
| --------------------------- | ----------------------------------------------------------------------- |
| `messageId()`               | ID gerado pelo SQS (UUID)                                               |
| `message()`                 | `Message<T>` efetivamente enviado (payload + headers finais)            |
| `endpoint()`                | Nome/URL da fila                                                        |
| `additionalInformation()`   | `Map<String,Object>` com metadados (ex.: `sequenceNumber` em FIFO)      |

O `publish` atual ignora esse retorno. Para logar o `messageId` para rastreio:

```java
SendResult<TodoEvent> result = sqsTemplate.send(queueName, event);
log.debug("SQS messageId={} queue={}", result.messageId(), queueName);
```

---

## 4. Exemplo aplicando tudo

```java
private void publish(String queueName, TodoEvent event) {
    sqsTemplate.send(to -> to
            .queue(queueName)
            .payload(event)
            .header("eventType", event.action())
            .header("traceId", MDC.get("traceId"))
            .delaySeconds(0)
            // .messageGroupId(event.todoId())                          // se a fila virar FIFO
            // .messageDeduplicationId(event.todoId() + ":" + event.action())
    );
}
```

---

## 5. Resumo

- Forma usada hoje: **2 parâmetros** — `queueName` e `payload`.
- Headers, delay e atributos FIFO **exigem** o overload com `Consumer<SqsSendOptions<T>>`.
- Variantes `async` e `sendMany` existem com a mesma assinatura conceitual.
- Veja também: [`SQS_STANDARD.md`](../SQS_STANDARD.md) e [`SQS_FIFO.md`](../SQS_FIFO.md) na raiz.

# Como o RabbitMQ Listener Funciona

## Visão Geral

O `notification-service` não expõe endpoints REST. Ele fica escutando filas do RabbitMQ e age quando uma mensagem chega. Para isso funcionar, há duas peças que trabalham juntas: a **configuração das filas** e a **anotação `@RabbitListener`**.

---

## Etapa 1 — RabbitMQConfig: o "cabeamento" no RabbitMQ

**Os dois serviços** (`todo-service` e `notification-service`) têm um `RabbitMQConfig.java` próprio com o mesmo conteúdo. Isso é intencional: cada serviço declara a infraestrutura que precisa para garantir que as filas e o exchange existam no RabbitMQ independentemente de qual serviço subir primeiro. O RabbitMQ ignora a declaração se a estrutura já existir.

Cada `RabbitMQConfig.java` declara três coisas no RabbitMQ:

### 1. O Exchange

```java
@Bean
public TopicExchange todoExchange() {
    return new TopicExchange("todo.exchange");
}
```

O **exchange** é o ponto de entrada das mensagens. O `todo-service` publica mensagens nele, não diretamente nas filas. Pense nele como um roteador postal.

### 2. As Filas

```java
@Bean public Queue createdQueue() { return new Queue("todo.created.queue", true); }
@Bean public Queue updatedQueue() { return new Queue("todo.updated.queue", true); }
@Bean public Queue deletedQueue() { return new Queue("todo.deleted.queue", true); }
```

Cada fila guarda mensagens de um tipo específico de evento. O `true` indica que a fila é **durável** — ela sobrevive a reinicializações do RabbitMQ.

### 3. Os Bindings (a ligação entre exchange e fila)

```java
@Bean
public Binding createdBinding() {
    return BindingBuilder
        .bind(createdQueue())     // fila de destino
        .to(todoExchange())       // exchange de origem
        .with("todo.created");    // routing key que dispara o roteamento
}
```

O **binding** é a regra de roteamento: _"toda mensagem que chegar no `todo.exchange` com routing key `todo.created` vai para a fila `todo.created.queue`"_.

| Routing Key publicada pelo todo-service | Fila que recebe a mensagem |
|-----------------------------------------|---------------------------|
| `todo.created`                          | `todo.created.queue`      |
| `todo.updated`                          | `todo.updated.queue`      |
| `todo.deleted`                          | `todo.deleted.queue`      |

---

## Etapa 2 — @RabbitListener: o Spring escutando a fila

```java
@RabbitListener(queues = RabbitMQConfig.QUEUE_CREATED)
public void onTodoCreated(TodoEvent event) {
    log.info("[NOTIFICATION] Todo CRIADO -> id={} | title='{}' | em={}",
        event.todoId(), event.title(), event.occurredAt());
}
```

A anotação `@RabbitListener` instrui o Spring a:

1. Conectar ao RabbitMQ e monitorar a fila `todo.created.queue`
2. Quando uma mensagem chegar, **desserializar o JSON automaticamente** para um objeto `TodoEvent` (via `Jackson2JsonMessageConverter` configurado no `RabbitMQConfig`)
3. Chamar o método anotado com o objeto já convertido

Há um método separado para cada fila, o que permite tratar cada tipo de evento de forma independente.

---

## O Fluxo Completo

```
Todo Service
  └─ publica no "todo.exchange" com routing key "todo.created"
                    │
              RabbitMQ roteia pela routing key
                    │
              "todo.created.queue"   ← binding conecta exchange → fila
                    │
        @RabbitListener escuta essa fila continuamente
                    │
        onTodoCreated(TodoEvent event) é chamado pelo Spring
                    │
        log.info("[NOTIFICATION] Todo CRIADO -> ...")
```

---

## Por que separar Config e Listener?

| Responsabilidade        | Onde fica           | O que faz                                      |
|-------------------------|---------------------|------------------------------------------------|
| Estrutura do RabbitMQ   | `RabbitMQConfig`    | Cria filas, exchange e bindings no broker      |
| Lógica de consumo       | `TodoEventListener` | Define o que fazer quando uma mensagem chega   |

O `RabbitMQConfig` garante que as filas e o roteamento existam no RabbitMQ antes de qualquer mensagem ser enviada ou recebida. Sem ele, o `@RabbitListener` tentaria escutar uma fila que não existe e falharia.

---

## Resiliência

Se o `notification-service` cair, as mensagens **não são perdidas** — elas ficam armazenadas nas filas (duráveis) do RabbitMQ. Quando o serviço voltar, ele consome todas as mensagens pendentes automaticamente.

```
Todo Service publica evento
       │
  notification-service está fora do ar
       │
  mensagem fica na fila (durável)
       │
  notification-service volta
       │
  onTodoCreated() é chamado com a mensagem pendente
```

---

## Leitura Complementar

- [Como os Serviços se Comunicam](./communication.md) — visão geral da arquitetura e dos dois tipos de comunicação
- [Eventos do Sistema (RabbitMQ)](./events.md) — payload, routing keys e filas de cada evento

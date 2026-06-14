# Consumer

## O que é

O **consumer** é quem **consome** a mensagem — depende dos dados que o outro
lado produz. No Pact, é o consumer que **dita o contrato**: ele declara só os
campos que realmente lê. Por isso o modelo se chama **consumer-driven**.

## No projeto

| | |
|---|---|
| Serviço | `notification-service` |
| Papel | consome o `TodoEvent` publicado pelo `todo-service` via RabbitMQ |
| Teste | `notification-service/src/test/.../pact/TodoEventConsumerPactTest.java` |

O teste declara 3 interações — uma por tipo de evento:

```java
@Pact(provider = "todo-service", consumer = "notification-service")
public MessagePact todoCreated(MessagePactBuilder builder) {
    return builder
            .expectsToReceive("a todo created event")
            .withContent(todoEventBody("CREATED"))
            .toPact();
}
```

Cada `@Pact` descreve **o que o consumer espera receber**. O corpo usa
**matchers** em vez de valores fixos (qualquer string serve pra `todoId`/`title`;
`occurredAt` é validado por regex ISO-8601); só `action` é pinado no valor exato,
porque é nele que o consumer ramifica.

## Por que assíncrono

A comunicação é por RabbitMQ, então o contrato é sobre o **corpo JSON da
mensagem**, não sobre uma request HTTP. Daí
`@PactTestFor(providerType = ProviderType.ASYNCH)`.

## O que o consumer produz

Ao rodar `mvn test -Dtest=TodoEventConsumerPactTest`, o teste **gera** o
[Pact](./pact.md) (arquivo JSON) e o grava em `../pacts/`. Esse arquivo é o
[contrato](./contrato.md) que o [provider](./provider.md) vai verificar.

## Relacionados

- [provider.md](./provider.md) — o outro lado, que verifica o contrato
- [contrato.md](./contrato.md) — o acordo abstrato
- [pact.md](./pact.md) — o arquivo gerado
- [pact-broker.md](./pact-broker.md) — onde o contrato seria publicado em produção

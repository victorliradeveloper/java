# Provider

## O que é

O **provider** é quem **produz** a mensagem — o lado que o consumer depende. Ele
não dita nada: seu trabalho é **provar que o que publica bate com o contrato**
que o consumer declarou.

## No projeto

| | |
|---|---|
| Serviço | `todo-service` |
| Papel | publica o `TodoEvent` no RabbitMQ (consumido pelo `notification-service`) |
| Teste | `todo-service/src/test/.../pact/TodoEventProviderPactTest.java` |

O teste lê os contratos da pasta `../pacts` e, pra cada mensagem declarada,
chama o método `@PactVerifyProvider` de mesma descrição:

```java
@Provider("todo-service")
@PactFolder("../pacts")
class TodoEventProviderPactTest {

    @PactVerifyProvider("a todo created event")
    String createdEvent() {
        return serialize(TodoEvent.of("...", "Comprar leite", "CREATED"));
    }
}
```

O método devolve o **corpo JSON que este serviço realmente publicaria**, e o
Pact compara contra os matchers do contrato. Se um campo sumir, mudar de tipo ou
de formato, o teste falha.

## Serialização fiel à produção

O detalhe que torna o teste honesto: o JSON **não é montado à mão**. Ele é gerado
pelo próprio `RabbitMQConfig.messageConverter()` — o mesmo
`Jackson2JsonMessageConverter` que o `OutboxPublisher` usa em runtime.

```java
private final MessageConverter converter = new RabbitMQConfig().messageConverter();
```

Assim, se a serialização do `TodoEvent` mudar na config de produção, o teste
reflete a mudança automaticamente. (Foi assim que se pegou o `occurredAt`
serializado como **array** em vez de ISO-8601, depois corrigido no converter.)

## `MessageTestTarget`

Como o alvo é uma mensagem assíncrona e não um endpoint HTTP, o target é
`MessageTestTarget`, configurado no `@BeforeEach`.

## Relacionados

- [consumer.md](./consumer.md) — quem gera o contrato que este lado verifica
- [contrato.md](./contrato.md) — o acordo abstrato
- [pact.md](./pact.md) — o arquivo lido por `@PactFolder`
- [pact-broker.md](./pact-broker.md) — alternativa ao `@PactFolder` em produção

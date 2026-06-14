# Pact — Contract Testing

## Visão Geral

**Pact** é uma ferramenta de **contract testing**: em vez de subir dois serviços juntos pra testar se eles se comunicam, **cada lado testa sozinho contra um contrato compartilhado** — um arquivo JSON que descreve a mensagem trocada entre eles.

O contrato é **consumer-driven** ("dirigido pelo consumidor"): quem dita o que a mensagem precisa ter é **quem consome**, não quem produz. Faz sentido — o consumer só depende dos campos que ele realmente lê; ele não tem opinião sobre o resto.

Neste projeto a comunicação é **assíncrona via RabbitMQ**, então usamos o modo **message pact** (não o HTTP). O contrato é sobre o **corpo JSON** do `TodoEvent`:

```
CONSUMER (notification-service)                PROVIDER (todo-service)
  "espero uma msg com todoId,                     "o TodoEvent que eu publico
   title, action, occurredAt"                      bate com esse contrato?"
          │                                                  ▲
          │ gera                                             │ verifica
          ▼                                                  │
     pacts/notification-service-todo-service.json ───────────┘
```

Os dois testes **nunca rodam juntos** e **nenhum precisa de RabbitMQ no ar**. O arquivo `pact.json` é o ponto de encontro.

---

## O problema que o Pact resolve

No projeto, o `todo-service` publica eventos `TodoEvent` e o `notification-service` os consome (ver [fluxo-das-mensagens](../rabbitmq/fluxo-das-mensagens.md)). Como garantir que os dois continuam "se entendendo"?

| Alternativa | Problema |
|---|---|
| **Teste end-to-end** (sobe os dois + RabbitMQ + banco) | Lento, frágil, difícil de rodar no CI a cada commit. |
| **Mockar o outro lado na mão** | O mock pode divergir do comportamento real sem ninguém notar — exatamente o bug que se queria evitar. |
| **Contract testing (Pact)** | Cada lado testa isolado contra um contrato versionado. Rápido e pega divergência de schema **antes do deploy**. |

É o mesmo tipo de divergência cross-service que o `__TypeId__` mapping (ver [consumer](../rabbitmq/consumer.md)) tenta resolver — só que aqui pegamos no teste, não em runtime.

---

## O ciclo, mapeado nos arquivos

```
1. CONSUMER declara o que espera        2. Gera o contrato (arquivo)
   notification-service/.../pact/   ──►   01-microservices-rabbitmq/pacts/
   TodoEventConsumerPactTest.java         notification-service-todo-service.json
                                                      │
                                                      ▼
4. Falha se não bater                  3. PROVIDER lê e verifica
   (foi o que aconteceu com         ◄──    todo-service/.../pact/
    o occurredAt — ver abaixo)            TodoEventProviderPactTest.java
```

**A ordem importa:** o teste do consumer roda **primeiro** (ele gera o `pact.json`); só depois o provider tem o que verificar.

---

## Lado CONSUMER — `notification-service`

O consumer descreve cada mensagem que espera receber e prova que o `TodoEvent` real consegue desserializá-la.

```java
// notification-service/.../pact/TodoEventConsumerPactTest.java
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "todo-service", providerType = ProviderType.ASYNCH)
@PactDirectory("../pacts")   // onde grava o pact.json (raiz do projeto)
class TodoEventConsumerPactTest {

    @Pact(provider = "todo-service", consumer = "notification-service")
    public MessagePact todoCreated(MessagePactBuilder builder) {
        return builder
            .expectsToReceive("a todo created event")
            .withContent(todoEventBody("CREATED"))
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "todoCreated", pactVersion = PactSpecVersion.V3)
    void consumesCreatedEvent(List<Message> messages) throws Exception {
        // Prova que o TodoEvent do serviço lê o corpo declarado no contrato
        TodoEvent event = objectMapper.readValue(messages.get(0).contentsAsString(), TodoEvent.class);
        assertThat(event.action()).isEqualTo("CREATED");
        // ...
    }
}
```

Peças importantes:

- **`providerType = ASYNCH`** — diz ao Pact que é uma mensagem assíncrona, não uma request HTTP.
- **`@PactDirectory("../pacts")`** — manda o `pact.json` pra raiz do projeto (e não pro `target/` do módulo), pra o provider achar.
- **`@Pact`** — cada método anotado descreve **uma** interação. O `expectsToReceive("...")` dá o nome dela — esse texto é a chave que liga consumer e provider.
- **O `@Test`** recebe `List<Message>` (a mensagem que o Pact montou a partir do contrato) e prova que o consumer consegue processá-la.

---

## Matchers — o coração do contrato

Esta é a sacada que faz o Pact valer a pena. O contrato **não** congela valores; ele congela **tipos e formatos**:

```java
private PactDslJsonBody todoEventBody(String action) {
    return new PactDslJsonBody()
        .stringType("todoId", "11111111-...")              // qualquer string serve
        .stringType("title", "Comprar leite")              // qualquer string serve
        .stringValue("action", action)                     // valor EXATO: CREATED/UPDATED/DELETED
        .stringMatcher("occurredAt",
                "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?",  // qualquer data ISO-8601
                "2026-06-13T10:15:30");
}
```

No `pact.json` isso vira:

```json
"contents": { "action": "CREATED", "todoId": "1111...", "title": "Comprar leite", "occurredAt": "2026-06-13T10:15:30" },
"matchingRules": {
  "body": {
    "$.todoId":     { "matchers": [ { "match": "type" } ] },
    "$.title":      { "matchers": [ { "match": "type" } ] },
    "$.occurredAt": { "matchers": [ { "match": "regex", "regex": "\\d{4}-..." } ] }
    // $.action não tem matcher → exige o valor EXATO "CREATED"
  }
}
```

| Campo | Matcher | Significado |
|---|---|---|
| `todoId` | `type` | Tem que ser uma string. O valor é ignorado. |
| `title` | `type` | Idem. |
| `occurredAt` | `regex` | Tem que ser uma string no formato ISO-8601 (fração de segundo opcional). |
| `action` | *(nenhum)* | Tem que ser **exatamente** `CREATED` / `UPDATED` / `DELETED`. |

**Por que não fixar todos os valores?** Porque aí o teste quebraria por causa de *dados* ("o título mudou de 'Comprar leite' pra 'Comprar pão'") em vez de quebrar por *contrato* ("o campo title sumiu" ou "virou número"). Só pinamos `action` no valor exato porque é ele que **diferencia os três eventos** e o consumer ramifica nele.

---

## Lado PROVIDER — `todo-service`

O provider lê o `pact.json` e, pra cada interação, prova que a mensagem que ele realmente produz bate com o contrato.

```java
// todo-service/.../pact/TodoEventProviderPactTest.java
@Provider("todo-service")
@PactFolder("../pacts")   // lê os contratos da raiz do projeto
class TodoEventProviderPactTest {

    // Serializa com o MESMO converter de produção — contrato verifica o real, não JSON na mão
    private final MessageConverter converter = new RabbitMQConfig().messageConverter();

    @BeforeEach
    void setTarget(PactVerificationContext context) {
        context.setTarget(new MessageTestTarget());   // alvo assíncrono, não HTTP
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyContract(PactVerificationContext context) {
        context.verifyInteraction();
    }

    // O texto tem que ser IGUAL ao expectsToReceive("...") do consumer
    @PactVerifyProvider("a todo created event")
    String createdEvent() {
        return serialize(TodoEvent.of("1111...", "Comprar leite", "CREATED"));
    }
}
```

Peças importantes:

- **`@PactVerifyProvider("a todo created event")`** — o texto precisa ser **idêntico** ao `expectsToReceive(...)` do consumer. É assim que o Pact liga "o que o consumer pediu" com "o que o provider entrega".
- **Serialização fiel à produção** — o body é gerado pelo próprio `RabbitMQConfig.messageConverter()`, o mesmo `Jackson2JsonMessageConverter` que o `OutboxPublisher` usa em runtime (ver [publisher](../rabbitmq/publisher.md)). Assim o contrato verifica a serialização **de verdade**, não um JSON montado à mão que poderia mentir.
- **`MessageTestTarget`** — informa que o alvo é uma mensagem. (Atenção: nesta versão do Pact-JVM a classe é `MessageTestTarget`, não `AmqpTestTarget`.)

---

## O bug que o Pact pegou (e por que isso prova o valor dele)

Na primeira execução do provider, os 3 testes **falharam**:

```
1.1) body: $.occurredAt
     Expected [2026,6,13,17,59,51,263700700] to match '\d{4}-\d{2}-\d{2}T...'
```

O `Jackson2JsonMessageConverter` default registra o `JavaTimeModule` mas mantém `WRITE_DATES_AS_TIMESTAMPS=true` — então serializava `LocalDateTime` como **array de inteiros** `[2026,6,13,...]` em vez de string ISO-8601.

Isso funcionava entre os serviços Java (o consumer também sabia ler o array), **mas**:

- É um formato **acoplado ao Java** — um consumer em Python/Node quebraria.
- Não é legível e é frágil a mudanças de precisão.

**O ponto didático:** ninguém subiu os dois serviços, ninguém mandou uma mensagem por RabbitMQ — e o Pact **mesmo assim** apontou que producer e consumer iam divergir. Sem Pact, isso só apareceria em produção.

A correção foi no converter do producer:

```java
// todo-service/.../config/RabbitMQConfig.java
@Bean
public Jackson2JsonMessageConverter messageConverter() {
    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);  // ← ISO-8601
    return new Jackson2JsonMessageConverter(objectMapper);
}
```

Agora a mensagem vai pro wire como `"2026-06-13T10:15:30"`. A mudança é **retrocompatível**: os consumers Java leem string ISO sem alteração.

---

## Como rodar

A ordem importa (consumer gera, provider verifica):

```bash
# 1. Consumer: gera o pacts/notification-service-todo-service.json
mvn -f notification-service/pom.xml -Dtest=TodoEventConsumerPactTest test

# 2. Provider: lê o contrato e verifica a serialização real
mvn -f todo-service/pom.xml -Dtest=TodoEventProviderPactTest test
```

Resultado esperado no provider:

```
a todo created event
  generates a message which
    has a matching body (OK)
Tests run: 3, Failures: 0, Errors: 0
```

---

## Dependências (`pom.xml`)

```xml
<!-- notification-service (consumer) -->
<dependency>
    <groupId>au.com.dius.pact.consumer</groupId>
    <artifactId>junit5</artifactId>
    <version>4.6.17</version>
    <scope>test</scope>
</dependency>

<!-- todo-service (provider) -->
<dependency>
    <groupId>au.com.dius.pact.provider</groupId>
    <artifactId>junit5</artifactId>
    <version>4.6.17</version>
    <scope>test</scope>
</dependency>
```

---

## Glossário

| Termo | Significado |
|---|---|
| **Consumer** | Serviço que **recebe/lê** a mensagem (aqui: `notification-service`). Dita o contrato. |
| **Provider** | Serviço que **produz/publica** a mensagem (aqui: `todo-service`). Verifica o contrato. |
| **Pact / contrato** | O arquivo JSON com as interações esperadas + matchers. |
| **Interaction** | Uma mensagem específica do contrato (ex: "a todo created event"). |
| **Matcher** | Regra que diz *como* comparar um campo (por tipo, por regex, por valor exato). |
| **Message pact** | Contrato de mensagem assíncrona (RabbitMQ/Kafka). O oposto é o **HTTP pact** (REST). |
| **Pact Broker** | Servidor central que versiona contratos (alternativa ao arquivo em `pacts/`). Habilita o `can-i-deploy`. |

---

## O que ficou de fora (roadmap)

- **audit-service** — também consome `TodoEvent`, mas ainda não tem contrato Pact.
- **Pact Broker** — hoje o contrato é um arquivo no repo (`@PactFolder`). Em produção, um Broker versiona os contratos e responde `can-i-deploy` ("esse serviço pode subir sem quebrar quem depende dele?").
- **HTTP pact** — o que fizemos é message pact. O mesmo conceito vale pra REST síncrono.

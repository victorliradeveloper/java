# Serialização / Desserialização (JSON)

## O que é (e por que importa)

Um objeto Java vive na **memória RAM** de um processo: referências, ponteiros, layout que só faz sentido pra aquela JVM, naquele momento. Mas a RAM não atravessa fronteira nenhuma — você não consegue mandar um `Todo` "como ele está" pela rede, nem gravá-lo num banco, nem enfiá-lo numa fila.

- **Serialização** = transformar o objeto em memória num formato **portável** (aqui, texto JSON) que pode ser transmitido ou armazenado.
- **Desserialização** = o caminho de volta — reconstruir o objeto Java a partir desse texto.

```
   objeto Java (RAM)                    texto JSON (rede/disco/fila)
   Todo{title="leite",     ──serializa──▶   {"title":"leite",
        completed=false}    ◀─desserializa──  "completed":false}
```

**Por que isso é o coração de um microservice:** num monolito, um método chama outro passando o objeto direto na memória — sem conversão. No momento em que você quebra em serviços que conversam por **rede** (HTTP, SQS, SNS) e guardam estado em **banco**, *toda* comunicação cruza uma fronteira de processo. E fronteira de processo só transporta **bytes/texto**, não objetos. Então cada salto exige serializar de um lado e desserializar do outro:

- `POST /todos` → o navegador manda **texto JSON**, o servidor desserializa num DTO.
- O `todo-service` publica um evento → serializa o `TodoEvent` pra **texto** e joga no SNS.
- O `notification-service` recebe da fila → desserializa o **texto** de volta num `TodoEvent`.

Sem (de)serialização, microservices simplesmente não se falam. Ela é a "língua franca" entre processos que não compartilham memória — e usar **JSON** (texto legível, independente de linguagem) é o que permite, por exemplo, um consumidor em Python ler um evento publicado por Java.

## O que é o Jackson

**Jackson** é a biblioteca Java mais usada pra (de)serializar JSON — é o padrão de fato do ecossistema Spring (vem incluso no Spring Boot, você nem adiciona a dependência à mão). Ele faz a ponte objeto ⇄ JSON por **reflection**: olha os campos/getters do objeto pra gerar o JSON, e usa o construtor/setters pra reconstruir.

Três peças que vão aparecer no resto do doc:

| Peça | O que é |
|---|---|
| `ObjectMapper` | A classe central do Jackson. `writeValueAsString(obj)` serializa; `readValue(json, Tipo.class)` desserializa. Tudo passa por ela. |
| Módulos | Plugins que ensinam o Jackson a lidar com tipos que ele não conhece de fábrica — ex.: `JavaTimeModule` pra `LocalDateTime` (ver a pegadinha no fim do doc). |
| Anotações (`@JsonProperty`, `@JsonFormat`, …) | Ajustes finos por campo. Este projeto quase não usa — convenção de nome basta. |

No Spring Boot você raramente chama o Jackson direto: o framework já configura **um** `ObjectMapper` (com os defaults certos) e o usa automaticamente no HTTP. Quando o projeto precisa serializar à mão (idempotência, outbox), ele **injeta esse mesmo bean** em vez de criar um novo — e o porquê disso importar está na [regra de ouro](#regra-de-ouro-sempre-injete-o-objectmapper-nunca-dê-new) no fim.

---

## Onde acontece neste projeto

Converter objeto Java ⇄ texto JSON acontece em **toda fronteira**: HTTP, mensageria, e cache. O motor é sempre o **Jackson**, autoconfigurado pelo Spring Boot e exposto como um único bean `ObjectMapper` injetável.

| Camada | Onde | Direção | Motor |
|---|---|---|---|
| HTTP REST | [`TodoController`](../../todo-service/src/main/java/com/microservices/todo/controller/TodoController.java) (`@RequestBody`/retorno) | JSON ⇄ DTO | Spring MVC + Jackson (automático) |
| Idempotência | [`IdempotencyService`](../../todo-service/src/main/java/com/microservices/todo/idempotency/IdempotencyService.java) | response ⇄ JSON (string) | `ObjectMapper` explícito |
| Outbox (publisher) | [`OutboxService`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxService.java) + [`OutboxPublisher`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java) | `TodoEvent` ⇄ JSON | `ObjectMapper` explícito + `SnsTemplate` |
| Consumers SQS | [`TodoEventListener`](../../notification-service/src/main/java/com/microservices/notification/listener/TodoEventListener.java), audit | JSON → `TodoEvent` | spring-cloud-aws (automático) |
| Persistência | `@Document` (`Todo`, `OutboxEvent`, `IdempotencyKey`) | POJO ⇄ **BSON** | Spring Data MongoDB (**não é Jackson**) |

---

## 1. HTTP REST — automático

O Spring MVC usa o `MappingJackson2HttpMessageConverter` por baixo dos panos:

- **Desserializa** o corpo do request (`@RequestBody`) no DTO — [`TodoRequestDTO`](../../todo-service/src/main/java/com/microservices/todo/dto/request/TodoRequestDTO.java), [`TodoUpdateDTO`](../../todo-service/src/main/java/com/microservices/todo/dto/request/TodoUpdateDTO.java), [`TodoReplaceDTO`](../../todo-service/src/main/java/com/microservices/todo/dto/request/TodoReplaceDTO.java).
- **Serializa** o retorno (`ResponseEntity<T>`) em JSON — [`TodoResponseDTO`](../../todo-service/src/main/java/com/microservices/todo/dto/response/TodoResponseDTO.java) e o `ProblemDetail` dos erros.

Os DTOs são `record`s. O Jackson (2.12+) instancia record direto pelo **construtor canônico** — não precisa de setter nem `@JsonCreator`. Os nomes dos componentes do record viram as chaves do JSON.

Você não escreve nenhuma linha de (de)serialização aqui: é tudo convenção.

---

## 2. Idempotência — explícito

[`IdempotencyService`](../../todo-service/src/main/java/com/microservices/todo/idempotency/IdempotencyService.java) injeta o `ObjectMapper` e (de)serializa à mão, porque a resposta é **guardada como string** na collection `idempotency_keys`:

```java
private String serialize(Object obj) {
    return objectMapper.writeValueAsString(obj);     // response -> JSON
}
private <T> T deserialize(String json, Class<T> type) {
    return objectMapper.readValue(json, type);       // JSON -> response (no replay)
}
```

Fluxo: POST sucede → serializa o `TodoResponseDTO` → grava em `response_body`. Retry com a mesma key → lê a string → desserializa de volta no tipo certo (`responseType`, passado pelo controller) → devolve. Ver [idempotencia.md §POST](idempotencia.md#post-todos--idempotency-key).

---

## 3. Outbox — round-trip duplo

O caminho do evento até o SNS passa por **duas serializações e uma desserialização**:

```
TodoService.create
  └─ OutboxService.record(payload)
       └─ serialize(payload)                    ─→  TodoEvent → JSON (string)
          grava em outbox_events.payload                          [serialização #1]

OutboxPublisher.publishOne   (polling, async)
  ├─ objectMapper.readValue(event.getPayload(), TodoEvent.class)  [desserialização]
  │     JSON (string) → TodoEvent
  └─ snsTemplate.convertAndSend(destination, payload, headers)    [serialização #2]
        TodoEvent → JSON → SNS
```

Por que serializar pro banco e depois reconverter? É o **outbox pattern**: o evento precisa ser persistido **na mesma transação** do `save(Todo)` (atomicidade), e só depois publicado de forma assíncrona/resiliente. O banco guarda texto; o publisher reidrata o objeto pra deixar o `SnsTemplate` aplicar a serialização final + os message attributes. Ver [outbox.md](../sqs/outbox.md).

---

## 4. Consumers SQS — automático de novo

No lado consumidor, o spring-cloud-aws desserializa **sozinho**: o `@SqsListener` recebe o corpo da mensagem (JSON) e entrega já convertido no parâmetro tipado:

```java
@SqsListener(SqsConfig.QUEUE_CREATED)
public void onTodoCreated(TodoEvent event, @Header(MessageHeaders.ID) UUID messageId) { ... }
//                        ^^^^^^^^^ JSON -> TodoEvent, sem você chamar o mapper
```

Cada serviço tem sua **própria cópia** do record [`TodoEvent`](../../notification-service/src/main/java/com/microservices/notification/event/TodoEvent.java) — não há dependência compartilhada entre os serviços. A desserialização casa por **nome de campo**, então as cópias só precisam ter os mesmos campos (não a mesma FQN da classe). É o desacoplamento que mensageria proporciona.

---

## 5. MongoDB — **não é Jackson**

Cuidado com a confusão comum: as entidades persistidas (`Todo`, `OutboxEvent`, `IdempotencyKey`) **não** usam Jackson. O Spring Data MongoDB mapeia POJO ⇄ documento **BSON** com o `MappingMongoConverter` dele. Por isso:

- As anotações são `@Document`, `@Id`, `@Indexed` — **não** `@JsonProperty`.
- O `payload` do outbox é uma `String` (JSON) **dentro** de um documento BSON — ou seja, JSON aninhado como texto, não como subdocumento. Quem produziu essa string foi o Jackson (camada 3), não o Mongo.

JSON (Jackson) e BSON (Mongo) coexistem no projeto, com motores e responsabilidades separadas.

---

## A pegadinha do `LocalDateTime` → ISO-8601

O [`TodoEvent`](../../todo-service/src/main/java/com/microservices/todo/event/TodoEvent.java) tem `occurredAt` do tipo `LocalDateTime`. Como tipos `java.time` são serializados depende do módulo **`jackson-datatype-jsr310`** (`JavaTimeModule`):

| Config | Saída de `LocalDateTime` |
|---|---|
| `WRITE_DATES_AS_TIMESTAMPS = true` (default cru do Jackson) | array → `[2026,6,15,10,30,0]` |
| `WRITE_DATES_AS_TIMESTAMPS = false` (default do **Spring Boot**) | string ISO-8601 → `"2026-06-15T10:30:00"` |

O Spring Boot registra o `JavaTimeModule` e desliga o flag **automaticamente** no `ObjectMapper` autoconfigurado. Por isso, neste projeto, datas saem como ISO-8601 — o formato esperado por qualquer consumidor.

> **Essa é a mesma pegadinha que mordeu o projeto `01-microservices-rabbitmq`** no contract testing (Pact): `occurredAt` chegou serializado como array em vez de ISO-8601. A causa raiz é sempre a mesma — um `ObjectMapper` sem o `JavaTimeModule`.

### Regra de ouro: **sempre injete o `ObjectMapper`, nunca dê `new`**

```java
// ❌ ERRADO — perde o JavaTimeModule e toda a config do Spring Boot.
// LocalDateTime volta a virar array; pode até lançar InvalidDefinitionException.
private final ObjectMapper mapper = new ObjectMapper();

// ✅ CERTO — bean autoconfigurado, com JavaTimeModule e WRITE_DATES_AS_TIMESTAMPS=false.
private final ObjectMapper objectMapper;   // injetado via construtor
```

Todos os pontos do projeto (`IdempotencyService`, `OutboxService`, `OutboxPublisher`) **injetam** o bean — nenhum faz `new ObjectMapper()`. Mantenha assim. Se algum dia precisar customizar (módulos, naming strategy, etc.), faça via `@Bean ObjectMapper` ou `Jackson2ObjectMapperBuilderCustomizer`, **um lugar só**, pra todas as camadas herdarem a mesma config.

---

## Resumo

- **Um motor, um bean**: Jackson, via `ObjectMapper` injetado. Mongo é à parte (BSON).
- **Automático** no HTTP e nos consumers SQS; **explícito** na idempotência e no outbox (porque guardam JSON como string).
- **`record`s** como DTOs/eventos — Jackson resolve pelo construtor canônico.
- **Datas em ISO-8601** graças ao `JavaTimeModule` do Spring Boot — só funciona porque ninguém instancia `ObjectMapper` na mão.

## Referências

- [idempotencia.md](idempotencia.md) — usa (de)serialização pra cachear/replay de respostas.
- [outbox.md](../sqs/outbox.md) — o round-trip serializa→persiste→desserializa→publica.
- [http-methods/](../http-methods/README.md) — onde os DTOs entram e saem como JSON.
- [Jackson — Java 8 Date/Time](https://github.com/FasterXML/jackson-modules-java8)

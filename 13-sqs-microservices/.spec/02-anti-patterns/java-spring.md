# Regras de Engenharia para IA — Java + Spring

> Leia antes as [regras gerais](./general.md) e, para Mongo, [`mongo-db.md`](./mongo-db.md). Este arquivo cobre **apenas** o que é específico de Java + Spring **neste projeto** (Spring Boot 3.3.5, Java 21, Mongo, SQS, Eureka, Gateway).

---

## Antes de Sugerir Código

- Stack fixa: **Java 21**, **Spring Boot 3.3.5** (`jakarta.*`, não `javax.*`), **Spring Cloud 2023.0.3**, **Spring Cloud AWS 3.2.1**.
- Não invente anotações ou starters que não existem nessa versão.
- Banco é **MongoDB** (replica set `rs0`). Não sugerir JPA/Hibernate/Flyway aqui.
- Fila é **SQS** via `io.awspring.cloud:spring-cloud-aws-starter-sqs` (LocalStack em dev). Não sugerir RabbitMQ/Kafka.

---

## Arquitetura Mínima Inegociável

- Fluxo: `Controller` → `Service` → `Repository`. Não pule camadas.
- **Nunca exponha entidade (`@Document`) em resposta HTTP.** Sempre DTO (`Request` / `Response`).
- Regra de negócio mora no **Service**, não no Controller nem no Repository.
- Injeção **apenas via construtor**, campos `private final`. Sem `@Autowired` em field/setter.
- Sem `ApplicationContext.getBean(...)` para resolver dependência.
- Quando precisar de self-injection (ex.: `@Transactional REQUIRES_NEW` no mesmo bean), use `@Lazy` no parâmetro do construtor escrito à mão — `@RequiredArgsConstructor` do Lombok **não propaga** anotações de parâmetro.

---

## Transações

- `@Transactional` na camada **Service**, nunca em Controller/Repository.
- **Self-invocation não funciona**: `this.metodo()` ignora `@Transactional`. Use bean separado ou self-injection `@Lazy`.
- Não funciona em `private`, `final`, `static`.
- **Nunca dispare fila/HTTP/e-mail dentro de transação ativa esperando que rollback do banco "desfaça" o side-effect.** Para fila, use o [pattern Outbox](../03-patterns/outbox.md). Para evento pós-commit puro (sem durabilidade), `@TransactionalEventListener`.
- Mongo: transações exigem replica set (já configurado como `rs0`). Single-node standalone não suporta.

---

## REST e DTOs

- Verbo HTTP correto + status code correto. `200 OK` para erro é proibido.
- Use `@GetMapping`, `@PostMapping`, etc. Não use `@RequestMapping` genérico.
- `@RequestBody` **nunca** em `GET`.
- Valide entrada com `@Valid` + Bean Validation (`@NotBlank`, `@Size`, `@Email`...).
- Exceções centralizadas via `@ControllerAdvice` + `@ExceptionHandler` (ver `GlobalExceptionHandler`). Mapeie para status correto.
- Nunca exponha stack trace em resposta de produção.
- DTOs **imutáveis** como `record` (Java 21). Sem `@Data` do Lombok em DTO ou entidade.

---

## SQS (Spring Cloud AWS 3.2.1)

- `SqsTemplate` para publicar; `@SqsListener` para consumir. Não use cliente AWS SDK cru.
- **Consumer deve ser idempotente** — SQS é at-least-once. Use dedupe persistente (ver `processed_messages` em `notification-service`).
- **Não publicar direto do Service** quando há save no banco junto — usa Outbox. `SqsTemplate` no `OutboxPublisher`, não no `TodoService`.
- Header `JavaType` está desabilitado (`doNotSendPayloadTypeHeader()` em `SqsConfig`) — produtor e consumidor têm classes em pacotes diferentes; deserialização usa o tipo do parâmetro do `@SqsListener`.
- Não passar `LocalDateTime` em payload sem `JavaTimeModule` registrado (já vem do Boot).

---

## Logging

- **SLF4J** com placeholder: `log.info("user {} criado", id)`. Sem `+`, sem `System.out`, sem `printStackTrace()`.
- **Nunca** logue: senha, token, dados pessoais.
- `ERROR` = falha real. `WARN` = anomalia recuperável. `INFO` = evento de negócio. `DEBUG` = técnico.
- Prefixe logs de subsistema com tag em colchete: `[OUTBOX]`, `[NOTIFICATION]`, `[EMAIL]` — facilita `grep`.

---

## Concorrência

- Beans são **singleton**: zero estado mutável em campo (exceção: estado controlado por TX ou imutável após `@PostConstruct`).
- Não use `new Thread(...).start()`. Use `@Async`, `TaskExecutor` ou `@Scheduled`.
- `ThreadLocal`: sempre `remove()` no `finally`.
- `@Scheduled` exige `@EnableScheduling` na classe `@SpringBootApplication`. Sem isso o método não roda — falha silenciosa.

---

## Testes

- **JUnit 5** + **Mockito**.
- `@SpringBootTest` apenas integração; unitário não sobe contexto.
- Para Mongo: Testcontainers com `mongo:7` + replica set (H2 não existe pra Mongo).
- Sem `Thread.sleep` em teste — use `Awaitility`.
- Cubra erro e validação, não só caminho feliz.

---

## Stack Moderno (Spring 6 / Boot 3)

- `jakarta.*`, nunca `javax.*`.
- Prefira **`RestClient`** ou `WebClient`. `RestTemplate` está em maintenance.
- `record` para DTOs imutáveis (Java 21).
- `Optional` só em retorno. Nunca em campo, parâmetro ou coleção.
- Texto switch / pattern matching disponível — use quando ajudar.

---

## Top Anti-Padrões da IA neste projeto

1. Entidade (`@Document`) retornada direto pelo controller.
2. `@Autowired` em campo (use construtor + `private final`).
3. `@Transactional` em método `private` ou auto-chamado (`this.x()`).
4. `catch (Exception e) {}` engolindo erro.
5. Misturar `javax.*` e `jakarta.*`.
6. `SqsTemplate.send(...)` direto no Service de negócio quando há save no banco junto — quebra o Outbox.
7. Listener `@SqsListener` sem dedupe — duplica side-effect em retry.
8. `@Scheduled` sem `@EnableScheduling` — método nunca executa.
9. Self-invocation de método `@Transactional`/`@Async` — ignora o proxy.
10. `parallelStream()` dentro de transação ou tocando recurso compartilhado.
11. `@Data` do Lombok em entidade ou DTO (use `@Getter`/`@Setter`/`record`).
12. Inventar método de `MongoRepository` que não segue convenção (`findByXAndYOrderByZ...`).

---

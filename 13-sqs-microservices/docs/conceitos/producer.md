# Producer

Componente que **publica mensagens / eventos** num broker de mensageria (fila, topic, stream). É a "boca" que injeta dados no pipeline assíncrono — o oposto do **consumer**, que retira.

No projeto, o único producer é o [`todo-service`](../../todo-service), que publica eventos de domínio (`CREATED`, `UPDATED`, `DELETED`) no topic SNS `todo-events` via [`OutboxPublisher`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java). Os serviços `notification-service` e `audit-service` são puramente consumers — não publicam nada.

---

## A trinca: producer, broker, consumer

Mensageria assíncrona tem 3 papéis bem separados:

```
 ┌──────────┐   publish()    ┌────────┐   poll/push    ┌──────────┐
 │ Producer ├───────────────►│ Broker ├───────────────►│ Consumer │
 └──────────┘                └────────┘                └──────────┘
   todo-service           SNS + SQS                notification-service
                          (LocalStack)             audit-service
```

| Papel | Responsabilidade | Não conhece |
|---|---|---|
| **Producer** | Gerar mensagem com semântica de negócio + publicar no broker | Quem vai consumir, quantos, em que ordem |
| **Broker** | Receber, persistir, entregar mensagens. Garantir entrega (at-least-once, at-most-once, exactly-once) | Domínio (não interpreta payload) |
| **Consumer** | Ler do broker, aplicar side-effect (email, audit log, cobrança) | Quem produziu, qual instância publicou |

**Princípio chave**: producer e consumer só se conhecem através do **contrato do payload** + nome do destino. Trocar um consumer (Java → Python, Mongo → DynamoDB) **não afeta** o producer. Trocar o broker (SQS → Kafka) afeta os 2 mas só na interface de `send`/`receive`, não no payload.

---

## Tipos de producer

### Por destino

| Tipo | Destino | Exemplo no projeto |
|---|---|---|
| **Direct producer** | Fila SQS específica (`todo-created-queue`) | Versão antiga (pré-fan-out) — abandonado |
| **Fan-out producer** | Topic (SNS, Kafka topic) que distribui pra N filas/subscribers | `todo-service` → SNS `todo-events` ✅ |
| **Routed producer** | Exchange com routing key (RabbitMQ) | Não usado |

### Por sincronismo

| Tipo | Comportamento | Quando faz sentido |
|---|---|---|
| **Sync producer** | `producer.send().get()` — espera ack do broker antes de retornar | Quando o request HTTP do cliente depende de "evento gravado" pra responder |
| **Async producer** | `producer.send()` retorna imediatamente, ack via callback | Throughput alto, latência baixa de request — padrão moderno |
| **Outbox producer** | Service grava no DB, worker separado publica async | Quando a operação principal é uma escrita no DB e o evento precisa sair "se o DB commitou" |

Este projeto usa **outbox + async + fan-out** — explicado abaixo.

---

## O producer deste projeto

### Fluxo end-to-end

```
POST /todos                                T+0.000s
  └─ TodoController.create()
       └─ todoService.create(dto)  [@Transactional]
            ├─ todoRepository.save(todo)            ┐
            └─ outboxService.record(                │ MESMA TX
                  TOPIC_TODO_EVENTS,                │ — ou ambos commitam,
                  todo.id(),                        │ ou nenhum
                  "CREATED",                        │
                  TodoEvent.of(...)                 │
              )                                     ┘
       └─ retorna 201 pro cliente            T+~0.020s

[ Cliente recebe 201, request finalizada. Evento ainda NÃO foi publicado. ]

@Scheduled OutboxPublisher (a cada 2s)       T+~2.000s
  ├─ repository.claimNext(nodeId, 30s lease)
  │     └─ findAndModify atômico — pega 1 doc pendente
  ├─ snsTemplate.convertAndSend(
  │      "todo-events",
  │      payload,
  │      headers={"action": "CREATED"}     ← message attribute pra FilterPolicy
  │   )
  └─ event.markPublished() + save           ← marca published_at

SNS distribui (fan-out)                       T+~2.001s
  ├─ todo-created-queue   (FilterPolicy: action=CREATED) → notification
  └─ todo-audit-queue     (sem filtro)                   → audit
```

### Por que essa arquitetura

#### 1. Producer **não chama o broker direto** no `@Transactional`

Antipattern clássico (proibido pelo [spec](../../.spec/02-anti-patterns/java-spring.md) §Transações):

```java
@Transactional
public Todo create(...) {
    Todo saved = repository.save(todo);
    snsTemplate.send("todo-events", event);   // ❌ DUAL-WRITE
    return saved;
}
```

Problemas:
- `repository.save` commita, SNS cai antes do `send` → **evento perdido**.
- JVM crasha entre as linhas → idem.
- `@TransactionalEventListener` não resolve: roda pós-commit, mas se o processo morre não dispara.

#### 2. Outbox: transforma "publicar no SNS" em "uma escrita a mais no DB"

```java
@Transactional
public TodoResponseDTO create(TodoRequestDTO dto) {
    Todo todo = repository.save(...);
    outboxService.record(TOPIC_TODO_EVENTS, todo.getId(), "CREATED", event);
    return mapper.toResponse(todo);
}
```

`outboxService.record(...)` é um `insert` na collection `outbox_events`. Roda na mesma TX que o `repository.save(todo)`. O Mongo garante atomicidade entre os dois — commitam juntos ou nenhum.

#### 3. `OutboxPublisher` lê do DB e publica async

`@Scheduled(fixedDelay=2s)`: a cada 2s, o publisher:

1. Reivindica até 50 eventos pendentes via `claimNext()` (lease atômico).
2. Pra cada evento, em **TX nova (`REQUIRES_NEW`)**:
   - `snsTemplate.convertAndSend(...)` no topic.
   - Sucesso → `event.markPublished()` (preenche `published_at`).
   - Falha → `event.markFailed(...)` (incrementa `attempts`, grava `last_error`).

`REQUIRES_NEW` isola falhas: 1 evento que quebra não rola back o lote inteiro.

#### 4. Lease pattern pra múltiplas instâncias

[`OutboxEventRepositoryImpl.claimNext`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/repository/OutboxEventRepositoryImpl.java):

```java
Query query = new Query()
    .addCriteria(new Criteria().andOperator(
        Criteria.where("published_at").is(null),                // pendente
        new Criteria().orOperator(
            Criteria.where("lease_expires_at").is(null),         // sem lease
            Criteria.where("lease_expires_at").lt(now)           // lease expirado
        )
    ))
    .with(Sort.by(Sort.Direction.ASC, "created_at"));

Update update = new Update()
    .set("processing_node", nodeId)
    .set("lease_expires_at", now.plus(leaseDuration));

OutboxEvent claimed = mongoTemplate.findAndModify(
    query, update,
    FindAndModifyOptions.options().returnNew(true),
    OutboxEvent.class);
```

`findAndModify` é atômico no Mongo. Se 2 instâncias do `todo-service` rodam o publisher simultaneamente, cada uma pega docs diferentes — o lease serve como "lock por documento".

Equivalente Mongo do `SELECT FOR UPDATE SKIP LOCKED` do Postgres.

**Crash recovery**: se uma instância morre segurando o lease, o `lease_expires_at` expira em 30s e outra instância pega o doc. Sem intervenção manual.

---

## Componentes técnicos

### Beans + configuração

| Componente | Papel | Arquivo |
|---|---|---|
| `SnsTemplate` (bean) | Cliente SNS do Spring Cloud AWS | [`MessagingConfig.snsTemplate`](../../todo-service/src/main/java/com/microservices/todo/config/MessagingConfig.java) |
| `MappingJackson2MessageConverter` | Serializa payload pra JSON, usa o `ObjectMapper` do Spring Boot | mesmo arquivo |
| `OutboxService.record(...)` | API que o `TodoService` chama em vez de `SnsTemplate.send(...)` direto | [`OutboxService.java`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxService.java) |
| `OutboxPublisher` | `@Scheduled` que polla e publica | [`OutboxPublisher.java`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java) |
| `OutboxEventRepositoryImpl.claimNext` | Lease atômico | [`OutboxEventRepositoryImpl.java`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/repository/OutboxEventRepositoryImpl.java) |
| `@EnableScheduling` | Habilita o `@Scheduled` (sem ele o publisher não roda) | [`TodoServiceApplication.java`](../../todo-service/src/main/java/com/microservices/todo/TodoServiceApplication.java) |

### Schema do payload

`TodoEvent` é o contrato de domínio que sai pro broker:

```java
public record TodoEvent(String todoId, String title, String action, LocalDateTime occurredAt) {}
```

`action` aparece **2 vezes**:

1. **No payload** — pro consumer interpretar (`event.action()`).
2. **No header SNS** (message attribute) — pro broker filtrar via `FilterPolicy`.

```java
Map<String, Object> headers = Map.of("action", event.getEventType());
snsTemplate.convertAndSend(event.getDestination(), payload, headers);
```

Duplicação intencional. Custo: 1 linha. Benefício: o SNS filtra mensagens antes de chegar nas filas — `notification` nunca recebe um `DELETED` se a fila dele só tem `FilterPolicy: action=CREATED`. Menos tráfego, menos código de filtro no consumer.

### Configuração

[`application.yml`](../../todo-service/src/main/resources/application.yml):

```yaml
outbox:
  poll-interval-ms: 2000       # quanto tempo entre ciclos do @Scheduled
  batch-size: 50               # máx eventos publicados por ciclo
  lease-duration-ms: 30000     # TTL do lease (deve ser > tempo plausível de publish)

spring:
  cloud:
    aws:
      sns:
        endpoint: ${SPRING_CLOUD_AWS_SNS_ENDPOINT:http://localhost:4566}
      region:
        static: us-east-1
```

**Trade-off do poll-interval**:
- 2s → latência P50 entre POST e evento publicado: ~2-3s. Carga no DB: 1 query indexada / 2s.
- 100ms → latência ~150ms. Carga 20x maior. Faz sentido só se latência for crítica.
- Não dá pra zerar: o `@Scheduled` é polling. Pra sub-100ms, vc precisa de **CDC** (Change Data Capture) — Debezium lendo o oplog do Mongo, sem polling.

---

## Garantias de entrega

### O que esse producer garante

- ✅ **Se a TX do DB commitou, o evento eventualmente sai pro SNS.** Não tem janela onde o Todo existe mas o evento foi perdido.
- ✅ **Ordem de publicação** dentro de um `aggregate_id` é respeitada (sort por `created_at` no `claimNext`). Não é ordem global, mas pra fluxo de Todo é o que importa.
- ✅ **Crash do publisher entre `send` e `markPublished`** é tolerado: próximo ciclo refaz o publish (mesma msg sai 2x).

### O que NÃO garante

- ❌ **Exactly-once**: outbox é **at-least-once**. O consumer precisa ser idempotente — ver [`docs/conceitos/idempotencia.md`](./idempotencia.md).
- ❌ **Ordem global** entre aggregates diferentes. Se Todo A foi criado antes do Todo B, o evento de B pode chegar primeiro no consumer.
- ❌ **Latência sub-segundo**. Outbox adiciona pelo menos o `poll-interval` (2s).

Pra cenários que exigem ordem global e sub-100ms, considerar Kafka com partition key + producer síncrono — não é o trade-off do projeto.

---

## Quando NÃO precisa de outbox no producer

Outbox é solução pra **dual-write hazard** entre DB + broker. Se vc não tem essa combinação, é overhead desnecessário.

| Cenário | Outbox precisa? | Por quê |
|---|---|---|
| Producer só publica (não escreve no DB) | ❌ Não | Só 1 write — sem dual-write |
| Producer responde a **outro evento** (chain consumer→producer) | ❌ Não, ou só se quiser durabilidade extra | A msg de entrada já é durável no broker |
| Producer escreve no DB **e** publica | ✅ Sim | Caso clássico — o do `todo-service` |
| Latência sub-100ms é requisito hard | ⚠️ Considerar CDC (Debezium) | Outbox tem o overhead do polling |
| Producer não tem DB próprio | ❌ Não | Idempotência do producer vira responsabilidade do caller |

---

## Pegadinhas comuns

| Pegadinha | Sintoma | Como evitar |
|---|---|---|
| `SnsTemplate.send` direto no `@Transactional` do service de negócio | Evento perdido em falha de SNS após DB commit | Usar `OutboxService.record(...)` em vez de `SnsTemplate.send(...)` |
| Esquecer `@EnableScheduling` | Eventos ficam pendentes pra sempre, `published_at` sempre `null` | Anotar a classe `@SpringBootApplication`. Sem isso, o `@Scheduled` é silenciosamente ignorado |
| `self.publishOne(...)` em vez de proxy | `@Transactional(REQUIRES_NEW)` ignorado, lote inteiro rola back em 1 falha | Self-injection com `@Lazy` no construtor (padrão usado no `OutboxPublisher`) |
| Esquecer `action` como header | `FilterPolicy` do SNS não bate, todas as filas recebem ou nenhuma | Sempre passar `Map.of("action", event.getEventType())` no `headers` do `convertAndSend` |
| `lease_expires_at` muito curto | 2 instâncias pegam o mesmo evento → publish duplicado | TTL > tempo plausível de publish (30s no projeto, ajustar se SNS estiver lento) |
| Producer publica direto, sem outbox | Funciona em dev (sem falha), explode em prod (falha intermitente) | Outbox sempre que `save(entity) + publish(event)` for parte da mesma operação lógica |
| `payload` armazenado como BSON sub-doc | Difícil de migrar schema, queries acopladas a estrutura interna | Armazenar JSON string — schema-flexível, fácil de migrar |

---

## Comparação: "producer" vs "publisher"

Termos usados quase intercambiavelmente, com nuances por ecossistema:

| Termo | Origem comum | O que significa |
|---|---|---|
| **Producer** | Kafka, RabbitMQ | Quem injeta mensagens no broker. Genérico. |
| **Publisher** | Pub/Sub, AWS SNS | Quem publica em topic (modelo pub/sub). Específico do padrão. |
| **Sender** | JMS | Equivalente a producer em context message-queue (não topic). |

No código do projeto, a classe chama-se `OutboxPublisher` por convenção do padrão Outbox (Chris Richardson). Funcionalmente é o producer do SNS.

---

## Pra entrevista

**Pergunta clássica**: *"Você tem um POST /order que precisa salvar no banco E mandar mensagem pro Kafka. Como garante consistência?"*

Resposta em 3 frases:
1. **Não** publico no Kafka dentro da TX do banco — isso é dual-write, perde evento se Kafka cair entre o `save` e o `send`.
2. **Outbox pattern**: gravo o evento numa tabela `outbox_events` na **mesma TX** do `save` da Order. Atomicidade garantida pelo DB.
3. **Worker separado** (`@Scheduled`) lê pendentes e publica. Sucesso → marca `published_at`; falha → `attempts++`, retoma no próximo ciclo. Consumer precisa ser idempotente porque é at-least-once.

**Follow-up**: *"E se vc tem 3 instâncias do worker rodando?"*

Resposta: **lease pattern**. Worker faz `findAndModify` atômico setando `processing_node` + `lease_expires_at`. Outros workers ignoram docs com lease válido. Equivalente a `SELECT FOR UPDATE SKIP LOCKED` no Postgres. Se um worker crashar, lease expira em N segundos e outro pega.

**Follow-up técnico**: *"Por que não usar `@TransactionalEventListener` do Spring?"*

Resposta: garante "rodar X depois do commit", **mas sem durabilidade**. Se o processo cai entre o commit e o `send`, o evento é perdido. Outbox transforma o evento em **uma escrita a mais no banco** — durabilidade do DB protege o evento até o worker conseguir publicar.

---

## Referências

- [`docs/sqs/outbox.md`](../sqs/outbox.md) — detalhamento do pattern com diagramas e trade-offs
- [`docs/sqs/sns-fanout.md`](../sqs/sns-fanout.md) — como o SNS distribui pra múltiplos consumers
- [`docs/conceitos/idempotencia.md`](./idempotencia.md) — pré-requisito do at-least-once delivery
- [`docs/conceitos/circuit-breaker.md`](./circuit-breaker.md) — proteção do consumer; producer não usa CB porque outbox já é resilient
- [`.spec/03-patterns/outbox.md`](../../.spec/03-patterns/outbox.md) — spec do padrão no projeto
- [`.spec/03-patterns/fan-out.md`](../../.spec/03-patterns/fan-out.md) — spec do fan-out SNS → SQS
- [Chris Richardson — Pattern: Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html)
- [Debezium — CDC alternative](https://debezium.io/) — pra latência sub-100ms quando outbox polling não basta

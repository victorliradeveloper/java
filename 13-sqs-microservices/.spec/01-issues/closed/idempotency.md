# Idempotência — Plano de Correção

Punch list dos problemas identificados na auditoria do `todo-service` + `notification-service`. Cada seção tem o problema, a solução recomendada e tarefas concretas com checkbox.

Status geral: nada implementado.

Ordem sugerida: PR 1 → PR 2 → PR 3 → (opcional) PR 4.

---

## PR 1 — Correções rápidas (alto valor, baixo esforço) ✅ IMPLEMENTADO

### 1.1 DELETE retorna 404 na segunda chamada ✅

**Problema**: `TodoService.delete` chama `getOrThrow(id)`, que lança `EntityNotFoundException` se o registro já foi removido. HTTP contract diz DELETE deve ser idempotente.

**Solução**: deletar silenciosamente, publicar evento apenas se removeu de fato.

**Tarefas**:
- [x] Alterar `TodoService.delete(String id)` para usar `repository.findById(id).ifPresent(...)`
- [x] Mover `publish(QUEUE_DELETED, ...)` para dentro do `ifPresent` — segunda chamada não publica DELETED
- [x] Controller já retorna 204 — sem mudança lá
- [x] Testar: `DELETE /todos/{id}` duas vezes → ambas 204, apenas um evento DELETED — verificado 2026-05-22 (3 DELETEs no mesmo id, 3× 204, 1 evento DELETADO no consumer)

**Arquivos**: `todo-service/src/main/java/com/microservices/todo/service/TodoService.java`

**Trade-off**: cliente perde o sinal "esse id não existia". É o que o contrato HTTP idempotente exige.

---

### 1.2 PUT no-op publica UPDATED desnecessário ✅

**Problema**: `TodoService.update` publica UPDATED em toda chamada, mesmo quando o payload não muda nada (`PUT` com `{}` ou com valores iguais aos atuais).

**Solução**: snapshot dos campos antes/depois do `mapper.updateEntity`, publicar só se mudou.

**Tarefas**:
- [x] Adicionar `private record TodoSnapshot(String title, String description, boolean completed)` em `TodoService`
- [x] Capturar snapshot ANTES do `mapper.updateEntity(dto, todo)`
- [x] Capturar snapshot DEPOIS do mapper
- [x] Comparar com `equals` (record já implementa); só chamar `publish(QUEUE_UPDATED, ...)` se diferentes
- [x] Testar: `PUT /todos/{id}` com payload vazio `{}` → 200, zero eventos UPDATED publicados — verificado 2026-05-22
- [x] Testar: `PUT /todos/{id}` com mesmo `title` atual → zero eventos — verificado 2026-05-22
- [x] Testar: `PUT /todos/{id}` com `title`/`completed` novo → 1 evento — verificado 2026-05-22 (4 PUTs = 3 no-op + 1 real → 1 ATUALIZADO no consumer)

**Arquivos**: `todo-service/src/main/java/com/microservices/todo/service/TodoService.java`

**Trade-off**: `repository.save` ainda roda mesmo no no-op (Hibernate pode pular o UPDATE por dirty checking, mas a TX abre). Aceitável.

---

### 1.3 notification-service manda email duplicado ✅

> **Nota pós-migração Mongo (2026-05-22)**: implementação atualizada. `processed_messages` virou collection no Mongo (`notificationdb`). `tryInsert` agora retorna `boolean` em vez de `int` — usa `MongoTemplate.upsert` com `$setOnInsert` (equivalente semântico ao `INSERT ... ON CONFLICT DO NOTHING` do Postgres). O `init-db.sh` do Postgres foi removido junto com a pasta `postgres/`. Ver [`migration-mongo.md`](./migration-mongo.md).

**Problema**: `TodoEventListener` no `notification-service` não rastreia messageId já processado. SQS Standard é at-least-once → reentrega = email duplicado.

**Solução**: tabela de dedupe local no `notification-service`, keyed por SQS `messageId`. Insere antes de mandar email; se conflito, skip.

**Tarefas**:
- [x] Criar entidade `ProcessedMessage` em `notification-service/.../infrastructure/entity/`
  - Campos: `messageId VARCHAR PK`, `processedAt TIMESTAMP`
- [x] Criar `ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String>`
- [x] Adicionar método `tryInsert(messageId)` — usa `INSERT ... ON CONFLICT DO NOTHING` (Postgres) via `@Query` nativa, retorna `int` (1 = inseriu, 0 = duplicata)
- [x] Em `TodoEventListener`, injetar `@Header(MessageHeaders.ID) UUID messageId` — awspring 3.2.1 mapeia o SQS MessageId pro header padrão `MessageHeaders.ID` do Spring Messaging (a tentativa inicial com `@Header("Sqs_Ms_MessageId")` falhou em runtime com "Missing header")
- [x] Antes de chamar `emailService.send(...)`, chamar `tryInsert(messageId)`; se `0`, log + return (mensagem é ack'd)
- [x] Adicionar Postgres ao `notification-service` (`pom.xml` + `application.yml` com datasource)
- [x] Adicionar database `notificationdb` + usuário `notification_user` via `postgres/init-db.sh` montado em `/docker-entrypoint-initdb.d/`
- [x] `docker-compose.yml`: env vars de datasource e `depends_on: postgres` no `notification-service`
- [ ] Job/scheduled cleanup: deletar `processed_messages` com `processed_at < now() - 7 days` *(adiado — dívida)*
- [x] Testar dedupe — verificado 2026-05-22 via prova direta no banco: `INSERT ... ON CONFLICT DO NOTHING` sobre um messageId já gravado retorna `INSERT 0 0` (zero linhas afetadas). Em produção `tryInsert()` retorna 0 → listener loga `[DEDUPE]` e não reenvia email.

**Arquivos**:
- `notification-service/src/main/java/com/microservices/notification/listener/TodoEventListener.java`
- novos: `infrastructure/entity/ProcessedMessage.java`, `infrastructure/repository/ProcessedMessageRepository.java`
- `notification-service/src/main/resources/application.yml`
- `notification-service/pom.xml`
- `docker-compose.yml` (se precisar de novo Postgres)

**Trade-off (ordem de operações)**:
- Insert ANTES do email → se email falha após insert, perde email (próxima entrega vai detectar como duplicado).
- Insert DEPOIS do email → se crash antes do insert, reenvia email.

> **Revisão 2026-05-24**: ordem invertida pra **insert-DEPOIS-do-send**. Motivo: ao introduzir Circuit Breaker no `EmailService` (ver [`docs/conceitos/circuit-breaker.md`](../../../docs/conceitos/circuit-breaker.md)), a ordem original "perde raro" se tornaria "perde sempre durante outage de SMTP" — CB OPEN com insert-antes apaga o messageId mesmo sem ter enviado o email, e a próxima entrega é descartada como duplicada. Com CB protegendo SMTP, queremos que **falha = msg volta pra fila e eventualmente DLQ**, padrão de notificação moderno. Trade-off atualizado pra "duplica raro" (janela de duplicação ~50ms entre `send` OK e `tryInsert`, aceitável).

---

## PR 2 — Outbox transacional

### 2.1 Dual-write: DB commitou, SQS falhou → evento perdido ❌

**Problema**: `TodoService.publish` roda fora de transação e depois do `save`. Se SQS estiver indisponível, evento é perdido. Anti-pattern explícito do `.spec/02-anti-patterns/java-spring.md` §Transações: "Nunca dispare e-mail/fila/HTTP dentro de transação ativa sem `@TransactionalEventListener`".

**Solução**: outbox pattern. Save do Todo + insert na outbox na MESMA transação. Relay (`@Scheduled`) varre pendentes e publica no SQS, marca como publicado em TX separada.

**Tarefas — entidade e repositório**:
- [ ] Criar entidade `OutboxEvent` em `todo-service/.../infrastructure/entity/`
  - Campos: `id UUID PK`, `aggregateId VARCHAR`, `aggregateType VARCHAR`, `eventType VARCHAR`, `destination VARCHAR`, `payload TEXT`, `createdAt TIMESTAMP`, `publishedAt TIMESTAMP NULL`, `attempts INT DEFAULT 0`, `lastError TEXT NULL`
  - Sem `@Data` do Lombok (regra java-spring §JPA)
- [ ] Criar `OutboxEventRepository extends JpaRepository<OutboxEvent, UUID>`
  - Query: `findTop50ByPublishedAtIsNullOrderByCreatedAtAsc` com `@Lock(LockModeType.PESSIMISTIC_WRITE)` + hint `jakarta.persistence.lock.timeout = -2` (SKIP LOCKED no Postgres)
- [ ] Adicionar índice em `(published_at, created_at)` via `@Index` na anotação `@Table`

**Tarefas — service**:
- [ ] Criar `OutboxService` em `todo-service/.../outbox/`
  - Método `record(String destination, Object event)` — serializa via `ObjectMapper`, insere `OutboxEvent` com `publishedAt = null`
  - Sem `@Transactional` próprio — herda a TX do `TodoService` chamador
- [ ] Anotar métodos `create/update/delete` do `TodoService` com `@Transactional`
- [ ] Substituir `sqsTemplate.send(...)` por `outboxService.record(...)` em todos os 3 métodos
- [ ] Remover `SqsTemplate sqsTemplate` do `TodoService` (move pro publisher)

**Tarefas — relay/publisher**:
- [ ] Criar `OutboxPublisher` em `todo-service/.../outbox/`
- [ ] Método `@Scheduled(fixedDelayString = "${outbox.poll-interval:2000}")` `publishPending()`
- [ ] Buscar lote de pendentes em TX read-only (ou TX nova com lock)
- [ ] Para cada evento: TX nova `REQUIRES_NEW` → publicar via `SqsTemplate.send(destination, deserialized)` → setar `publishedAt = now()` → save
- [ ] Em erro: incrementar `attempts`, gravar `lastError`, deixar `publishedAt = null` (próximo ciclo tenta de novo)
- [ ] Log com SLF4J placeholder, sem logar payload completo (regra java-spring §Logging)

**Tarefas — config**:
- [ ] Adicionar `@EnableScheduling` em `TodoServiceApplication`
- [ ] Adicionar `outbox.poll-interval` em `application.yml` (default 2000ms)

**Tarefas — verificação**:
- [ ] Testar: derrubar LocalStack, fazer `POST /todos` → 201, registro na outbox `publishedAt = null`
- [ ] Subir LocalStack → próximo ciclo publica, `publishedAt` preenchido
- [ ] Confirmar que evento chega no `notification-service`

**Arquivos novos**:
- `todo-service/src/main/java/com/microservices/todo/infrastructure/entity/OutboxEvent.java`
- `todo-service/src/main/java/com/microservices/todo/infrastructure/repository/OutboxEventRepository.java`
- `todo-service/src/main/java/com/microservices/todo/outbox/OutboxService.java`
- `todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java`

**Arquivos alterados**:
- `todo-service/src/main/java/com/microservices/todo/service/TodoService.java`
- `todo-service/src/main/java/com/microservices/todo/TodoServiceApplication.java`
- `todo-service/src/main/resources/application.yml`

**Trade-off**:
- Outbox troca "perda" por "duplicação garantida" em caso de falha do relay (relay publica no SQS, crasha antes de marcar `publishedAt`, próximo ciclo republica). Por isso PR 1 (dedupe no consumer) é pré-requisito real desta entrega — sem ele, esse PR pode aumentar duplicação.
- Retention: por enquanto só marca `publishedAt`. Cleanup periódico fica como dívida.
- Backoff: hoje só incrementa `attempts`. Backoff exponencial (`nextAttemptAt`) é melhoria futura.
- Continuamos com `ddl-auto: update`. Migrar para Flyway é dívida separada (regra java-spring §JPA diz que `update` é proibido em prod, mas o projeto inteiro depende disso hoje — fora do escopo deste PR).

---

## PR 3 — Idempotency-Key no POST ✅ IMPLEMENTADO

### 3.1 POST duplicado cria dois Todos ✅

> **Nota pós-migração Mongo (2026-05-23)**: implementação adaptada do plano original (Postgres → Mongo). Diferenças: `MongoRepository` em vez de `JpaRepository`, claim atômico via `insert` + `DuplicateKeyException` (sem race), TTL index em `expires_at` via Mongock V004 substitui job de cleanup.
>
> **Refatoração arquitetural (2026-05-23, mesma sessão)**: a lógica de idempotência foi **extraída** do `TodoService` pra um novo `IdempotencyService` genérico em `com.microservices.todo.idempotency`. Isso eliminou a self-injection com `@Lazy` no `TodoService` — quando há um bean wrapping a operação, o proxy do Spring funciona naturalmente. Padrão usado em projetos reais Spring/Java enterprise:
> - **Single Responsibility**: `TodoService` voltou a ser puramente sobre Todo. `IdempotencyService` cuida só de idempotência.
> - **Reutilizável**: assinatura genérica (`<T> T executeIdempotent(key, fingerprint, payload, type, supplier)`) — qualquer endpoint pode adotar idempotência sem mudar o service de negócio.
> - **Fingerprint da operação** (`"POST /todos"`) entra no hash → mesma key em endpoints diferentes não colide.
> - **`ProblemDetail` (RFC 7807)** no handler global em vez de `Map<String, String>` improvisado.
> - **Validação do header** (length + ASCII imprimível, Stripe spec).
> - **MDC com `idempotencyKey`** pra trace nos logs.
> - **Config tipada** (`Duration key-ttl`, `int max-key-length`) via `@Value` com sintaxe Duration.

**Problema**: ID é gerado pela aplicação (`UUID.randomUUID()` em `TodoService.create`). Dois POSTs idênticos = dois registros + dois eventos CREATED. Sem `Idempotency-Key`, sem dedupe.

**Solução**: header `Idempotency-Key` (UUID enviado pelo cliente) + collection de dedupe. Padrão Stripe.

**Tarefas — entidade e repositório**:
- [x] Criar entidade `IdempotencyKey` em `todo-service/.../infrastructure/entity/`
  - `@Id String key`, `request_hash`, `response_status`, `response_body`, `created_at`, `expires_at`
- [x] Criar `IdempotencyKeyRepository extends MongoRepository<IdempotencyKey, String>`
- [x] Criar `V004_IdempotencyKeyIndexes` (Mongock) — TTL index em `expires_at` (`expireAfterSeconds=0`)

**Tarefas — service/controller**:
- [x] Adicionar `@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey` em `TodoController.create`
- [x] Overload `TodoService.create(dto, key)`:
  1. Se `key == null`: chama `self.create(dto)` (comportamento legado)
  2. Se `key != null`: calcular `hash = SHA-256(dto)`
  3. **Claim atômico**: `idempotencyKeyRepository.insert(claim)`
     - **Sucesso**: chama `self.create(dto)` em TX nova, grava response na key via `markCompleted`
     - **`DuplicateKeyException`**: já existe — busca o doc:
       - `hash == requestHash` e `responseBody != null`: retorna response cacheada
       - `hash == requestHash` e `responseBody == null`: 409 "requisição concorrente em processamento"
       - `hash != requestHash`: 409 "payload difere do enviado na primeira chamada"
  4. Em caso de falha do `self.create`: deleta o claim → cliente pode retentar com mesma key
- [x] `@ExceptionHandler(IdempotencyKeyConflictException.class)` em `GlobalExceptionHandler` → 409
- [x] Self-injection `@Lazy TodoService self` no construtor (mesmo padrão do `OutboxPublisher`) — `this.create(dto)` ignoraria `@Transactional`

**Tarefas — limpeza**:
- [x] **TTL index** em `expires_at` (V004) — Mongo apaga docs expirados automaticamente. Substitui job manual.

**Tarefas — verificação** (a rodar em dev local):
- [ ] 2 POSTs com mesmo `Idempotency-Key` e mesmo body → 1 Todo, mesma response nos 2
- [ ] 2 POSTs com mesmo `Idempotency-Key` e body diferente → 1º: 201, 2º: 409
- [ ] POST sem header → comportamento atual (cria N)
- [ ] `db.idempotency_keys.find()` mostra `response_status: 201` + `response_body: <json>` após sucesso
- [ ] Forçar falha no `self.create` (ex: derrubar Mongo no meio) → claim é deletado (retry com mesma key funciona)

**Arquivos novos**:
- `todo-service/src/main/java/com/microservices/todo/infrastructure/entity/IdempotencyKey.java`
- `todo-service/src/main/java/com/microservices/todo/infrastructure/repository/IdempotencyKeyRepository.java`
- `todo-service/src/main/java/com/microservices/todo/exception/IdempotencyKeyConflictException.java`
- `todo-service/src/main/java/com/microservices/todo/infrastructure/migration/V004_IdempotencyKeyIndexes.java`

**Arquivos alterados**:
- `todo-service/src/main/java/com/microservices/todo/controller/TodoController.java`
- `todo-service/src/main/java/com/microservices/todo/service/TodoService.java`
- `todo-service/src/main/java/com/microservices/todo/exception/GlobalExceptionHandler.java`

**Trade-off**:
- Cliente precisa cooperar (mandar header). Sem o header, POST volta a não ser idempotente. Aceitável: REST não exige idempotência em POST por contrato.
- Alternativa mais rígida (`required = true`) força todo cliente a mandar — não adotado, quebra integrações existentes.
- Claim antes de criar (Stripe-style): se o `create` falhar, claim é deletado pra cliente poder retentar. Alternativa "key fica presa até TTL expirar" é mais conservadora mas pior UX em dev.
- `DuplicateKeyException` **não funciona dentro de TX do Mongo** (aborta a TX). Por isso o claim acontece **fora** do `@Transactional` do `self.create(dto)` — mesma estratégia que o `audit-service` usa pra dedupe.

---

## PR 4 (opcional) — SQS FIFO

### 4.1 SQS Standard entrega a mesma mensagem 2x ao consumer ❌

**Problema**: filas Standard são at-least-once. Mesmo com outbox bem implementado, o broker pode reentregar (visibility timeout expirado, falha de ack, etc.).

**Solução A — FIFO + `messageDeduplicationId`** (broker resolve, janela 5min):
- [ ] Atualizar `localstack/init-aws.sh` para criar `*.fifo` em vez de filas Standard, com `FifoQueue=true` e `ContentBasedDeduplication=false`
- [ ] No `OutboxPublisher`, usar overload `SqsTemplate.send(to -> to.queue(...).payload(...).messageDeduplicationId(outboxEvent.getId().toString()).messageGroupId(outboxEvent.getAggregateId()))`
- [ ] No `notification-service.SqsConfig`, garantir que o listener aceita FIFO
- [ ] Atualizar nomes das filas em `SqsConfig.QUEUE_*` (`todo-created-queue.fifo` etc.)
- [ ] Testar: republicação do mesmo outboxEvent em < 5min → SQS descarta segundo envio

**Solução B (recomendada)**: aceitar at-least-once, contar com dedupe do consumer (PR 1.3) — sem trabalho adicional aqui.

**Decisão pendente**: ir com B (PR 1.3 já cobre o caso real). PR 4 só faz sentido se quiser dedupe no broker como defesa em profundidade.

---

## Resumo de dependências

```
PR 1 (DELETE, PUT no-op, consumer dedupe)  ←  pré-requisito real do PR 2
   │
   └─→  PR 2 (Outbox)  ←  pré-requisito do PR 3 (idempotency-key precisa de TX confiável)
            │
            └─→  PR 3 (Idempotency-Key)
                     │
                     └─→  PR 4 (FIFO, opcional)
```

PR 1 deve vir primeiro: cobre 3 dos 6 problemas com pouco código E destrava o PR 2 (outbox sem dedupe no consumer piora duplicação).

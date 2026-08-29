# Migração Postgres → MongoDB

Trocar Postgres por MongoDB nos dois serviços (`todo-service` e `notification-service`) **antes** de implementar o outbox. Pré-requisito do PR 2 (outbox.md) — o desenho atual usa `SELECT FOR UPDATE SKIP LOCKED`, que não existe em Mongo. Vai precisar ser reescrito com `findOneAndUpdate` (lease pattern).

Status: **CONCLUÍDO** em 2026-05-22. Stack rodando 100% em Mongo single-node replica set. Ver "Divergências do plano original" no final.

---

## Por que isso é uma migração não-trivial

| Camada | Mudança | Impacto |
|---|---|---|
| Driver | `spring-boot-starter-data-jpa` → `spring-boot-starter-data-mongodb` | `pom.xml` dos 2 serviços |
| Entidade | `@Entity` + `@Table` + `@Id` JPA → `@Document` + `@Id` Spring Data | Todas as entidades existentes |
| Repositório | `JpaRepository` → `MongoRepository` | Todos os repos + queries customizadas |
| Geração de ID | `@GeneratedValue(strategy = UUID)` → setado manualmente ou `ObjectId` | API contract não muda se mantermos UUID string |
| Transações | Implícitas no JPA | Exigem **replica set** no Mongo — confirmado: vamos de single-node replica set |
| Dedupe | `INSERT ... ON CONFLICT DO NOTHING` | `updateOne` com `$setOnInsert` + `upsert: true`, lê `matchedCount` pra detectar duplicata |
| Infra | `postgres:16-alpine` + `init-db.sh` | `mongo:7` com `--replSet rs0` + script de `rs.initiate()` |
| Migrações | `ddl-auto: update` (gambiarra) | Anti-pattern Mongo proíbe `auto-index-creation` em prod. Pra dev/POC vamos manter `auto-index-creation: true` com a anotação `@Indexed`; produção futura usaria Mongock (dívida). |
| `@PrePersist` | Lifecycle hook do JPA | Não existe em Mongo. Trocar por `@CreatedDate` + `EnableMongoAuditing`, ou setar no service/construtor. |
| Lock | `SELECT ... FOR UPDATE SKIP LOCKED` (planejado pro outbox) | Vira **lease pattern** com `findOneAndUpdate` atômico |

---

## Decisões já travadas

1. **Single-node replica set** — `mongo:7` com `--replSet rs0` + `rs.initiate()` no script de init. Suporta transações multi-documento (pré-requisito do outbox).
2. **Plano antes de implementar** (este documento).
3. **Migrar os 2 serviços juntos** — `todo-service` e `notification-service`. Sem ficar com banco heterogêneo.
4. **Manter `id` como string UUID** nas entidades — preserva o contrato HTTP atual (clientes recebem `"id":"eec6ff5b-..."`). Vai contra o default de `ObjectId` do Mongo, mas o anti-pattern só desencoraja a troca "sem motivo" — aqui o motivo é compatibilidade.
5. **Manter o database isolado por serviço**: `tododb` e `notificationdb` viram dois databases dentro da mesma instância Mongo. Estrutura simétrica à atual.

---

## Fluxo: antes vs depois

### Antes (Postgres)

```
Container postgres:16-alpine
  ├── database tododb         (todo_user)
  │     └── tabela todos
  └── database notificationdb (notification_user)
        └── tabela processed_messages

todo-service        ──jdbc──> postgres:5432/tododb
notification-service ──jdbc──> postgres:5432/notificationdb
```

### Depois (Mongo single-node replica set)

```
Container mongo:7 (--replSet rs0)
  ├── database tododb
  │     └── collection todos
  └── database notificationdb
        └── collection processed_messages

todo-service        ──mongo://mongo:27017/tododb?replicaSet=rs0
notification-service ──mongo://mongo:27017/notificationdb?replicaSet=rs0
```

---

## Estrutura nova de arquivos

```
mongo/
  init-replset.sh            <- script de rs.initiate(), montado em /docker-entrypoint-initdb.d/
docker-compose.yml           <- service "postgres" vira "mongo"
todo-service/
  pom.xml                    <- troca starter-data-jpa por starter-data-mongodb, remove postgresql
  src/main/resources/
    application.yml          <- remove datasource, adiciona spring.data.mongodb.uri
  src/main/java/.../
    TodoServiceApplication.java          <- adiciona @EnableMongoAuditing
    infrastructure/entity/Todo.java      <- @Document, @Id Spring Data, @CreatedDate
    infrastructure/repository/
      TodoRepository.java                <- extends MongoRepository<Todo, String>
notification-service/
  pom.xml                                <- troca starter
  src/main/resources/application.yml
  src/main/java/.../
    infrastructure/entity/ProcessedMessage.java
    infrastructure/repository/ProcessedMessageRepository.java   <- método tryInsert reescrito com MongoTemplate
postgres/                    <- REMOVER pasta inteira
```

---

## Tarefas (com checkbox)

### Fase 1 — Infra: container Mongo + replica set

- [x] Criar pasta `mongo/` e arquivo `mongo/init-replset.sh` rodando `rs.initiate({_id:"rs0", members:[{_id:0, host:"mongo:27017"}]})` (script idempotente, em container `mongo-setup` separado)
- [x] Editar `docker-compose.yml`: substituir `postgres` por `mongo:7`, comando `--replSet rs0 --bind_ip_all`, **porta `27018:27017`** (ver divergência #2), volume `mongo-data:/data/db`, healthcheck `rs.status().ok == 1`
- [x] Renomear volume `postgres-data` → `mongo-data`
- [x] Remover `depends_on: postgres` dos 2 serviços, adicionar `depends_on: mongo`
- [x] Validar `rs.status().ok` → 1

### Fase 2 — `todo-service` migração

- [x] **pom.xml**: removido `spring-boot-starter-data-jpa` + `postgresql`; adicionado `spring-boot-starter-data-mongodb`
- [x] **application.yml**: substituído por `spring.data.mongodb.uri` + `auto-index-creation: true`
- [x] **`Todo.java`**: `@Document`, `@Id` Spring Data, sem `@GeneratedValue`/`@Column`/`@PrePersist`. `@CreatedDate` foi tentado mas **revertido** — ver divergência #1
- [x] **`TodoRepository.java`**: extends `MongoRepository<Todo, String>`
- [x] **`TodoServiceApplication.java`**: `@EnableMongoAuditing` adicionado e depois **removido** — ver divergência #1
- [x] **`TodoService.java`**: gera `id` manualmente com `UUID.randomUUID().toString()` + seta `createdAt = LocalDateTime.now()` no service (divergência #1)
- [x] **`TodoMapper.java`**: mantido `@Mapping(target = "id", ignore = true)` e `createdAt` idem — service preenche os dois
- [x] **`GlobalExceptionHandler.java` + `TodoNotFoundException.java`**: criada exception custom (substitui `jakarta.persistence.EntityNotFoundException` que sumiu com JPA)
- [x] Testes manuais:
  - [x] POST cria doc na collection `todos` com `_id` igual ao UUID e `createdAt` populado
  - [x] GET retorna lista
  - [x] PUT idempotente (PR 1.2) — 4 PUTs (3 no-op + 1 real) → 1 evento `ATUALIZADO`
  - [x] DELETE silencioso (PR 1.1) — 3 DELETEs → 1 evento `DELETADO`

### Fase 3 — `notification-service` migração

- [x] **pom.xml**: trocados starters (JPA + Postgres → Mongo)
- [x] **application.yml**: `spring.data.mongodb.uri` apontando pra `notificationdb`
- [x] **`ProcessedMessage.java`**: `@Document(collection = "processed_messages")`, `@Id` Spring Data, `@Field("processed_at")` no LocalDateTime
- [x] **`ProcessedMessageRepository.java`**: Opção A implementada. Padrão Custom interface + Impl: `ProcessedMessageRepositoryCustom` + `ProcessedMessageRepositoryImpl` (com `MongoTemplate.upsert` + `$setOnInsert`). Repositório principal extends `MongoRepository` + `ProcessedMessageRepositoryCustom`
- [x] **`TodoEventListener.java`**: retorno de `tryInsert` mudou de `int` (era `INSERT 0 0` / `INSERT 0 1` do Postgres) para `boolean` (`true` = inseriu/novo, `false` = duplicata)
- [x] Testes:
  - [x] Fluxo CRUD end-to-end → eventos chegam no `notification-service`
  - [x] Replay manual via mongosh: `insertOne` com `_id` já existente bloqueado com `E11000` / `code=11000`
  - [x] Email enviado 1× por evento real

### Fase 4 — Limpeza

- [x] Pasta `postgres/` apagada (incluindo `init-db.sh`)
- [x] Volume Postgres antigo apagado via `docker-compose down -v`
- [x] `.env.example` verificado — não havia refs a Postgres
- [x] `README.md` atualizado: linha do `postgres` virou `mongo` na tabela de serviços
- [x] `idempotency.md` §1.3 com nota da migração (boolean em vez de int, `processed_messages` virou collection)
- [x] `outbox.md` com aviso de lock — `SKIP LOCKED` → `findOneAndUpdate` (lease pattern)

### Fase 5 — Verificação end-to-end

- [x] `docker-compose down -v` + `docker-compose up --build` → todos serviços healthy
- [x] `rs.status().ok` → 1
- [x] `show dbs` → mostra `tododb` e `notificationdb` após primeiros writes (Mongo descarta DB vazia)
- [x] Cenários do PR 1 reproduzidos:
  - 4 PUTs (3 no-op + 1 real) → 1 evento `ATUALIZADO`
  - 3 DELETEs no mesmo id → 1 evento `DELETADO`
  - Replay manual: `insertOne` com `_id` duplicado → `code=11000` (E11000)
- [x] Filas SQS zeradas (0 pending, 0 in-flight) ao fim do ciclo

---

## Decisões e trade-offs

### 1. Single-node replica set vs standalone
**Escolhido**: single-node replica set. Permite transações multi-documento (necessárias pro outbox no PR 2).

### 2. `_id` como UUID string vs `ObjectId`
**Escolhido**: UUID string. Mantém o contrato HTTP atual (clientes ainda recebem `"id":"eec6ff5b-..."`). Anti-pattern Mongo desencoraja a troca de `ObjectId` "sem motivo" — aqui o motivo é compatibilidade com a API existente, então OK.

**Custo**: perdemos a propriedade monotônica do `ObjectId` (que carrega timestamp). Pouco relevante aqui.

### 3. `@EnableMongoAuditing` + `@CreatedDate` vs setar manualmente
**Escolhido**: auditoria. Substitui o `@PrePersist` do JPA com o mínimo de fricção. Spring Data Mongo cuida do `createdAt` automaticamente.

### 4. Geração de UUID
**Escolhido**: setar no service (`todo.setId(UUID.randomUUID().toString())`) antes do `save`. Alternativa de listener `@EventListener(BeforeConvertEvent)` no Mongo é mais invisível — preferimos explícito.

### 5. `tryInsert` retorna `int` (PR 1.3) ou `boolean`
**Escolhido**: `boolean`. Em Mongo o retorno natural é "inseriu" sim/não — não tem o `INSERT 0 0` literal do Postgres pra preservar. Ajustar o caller.

### 6. `auto-index-creation: true` em dev
**Aceito por consistência** com o `ddl-auto: update` atual. Anti-pattern Mongo proíbe em produção. Dívida anotada: migrar pra Mongock quando virar produção.

### 7. Database por serviço (`tododb` / `notificationdb`)
**Mantido**. Mesma simetria do Postgres. Em Mongo é trivial — basta passar o database name na URI.

### 8. Driver: `spring-boot-starter-data-mongodb` (síncrono) vs reactive
**Escolhido**: síncrono. O resto do código é blocking (Spring MVC + JDBC mental model). Reactive seria um segundo grande paradigm shift — fora do escopo desta migração.

---

## Riscos conhecidos

| Risco | Severidade | Mitigação |
|---|---|---|
| Replica set não inicia (`rs.initiate()` falha por algum motivo) | Alta | Healthcheck `rs.status().ok` bloqueia services downstream até estar pronto. Logs claros do init script. |
| Esquecimento do `@EnableMongoAuditing` → `createdAt` fica null | Média | Verificação na fase 2 inclui checar o `createdAt` do documento. |
| `tryInsert` com `upsert` tem semântica sutil — `matchedCount` vs `modifiedCount` | Média | Documentar e testar com replay manual. |
| `auto-index-creation` em prod | Alta (se virar prod) | Dívida explícita: Mongock futuro. |
| Compatibilidade do `TodoMapper` (MapStruct) com novo modelo | Baixa | MapStruct gera código que não depende do ORM. Só atenção ao `@Id` que muda de pacote. |
| Self-invocation de `@Transactional` quando adicionarmos no outbox | Alta | Mesmo padrão do outbox.md: injetar `self`. Não muda com Mongo. |

---

## Pontos cegos / o que NÃO está nesta migração

- Não migra outras tabelas/coleções porque não existem outras.
- Não muda o contrato HTTP (DTOs, status codes, paths) — só camada de persistência.
- Não implementa o outbox — esse é o próximo PR, e o desenho dele será ajustado pra refletir Mongo (lease pattern em vez de SKIP LOCKED).
- Não introduz reactive (`spring-boot-starter-data-mongodb-reactive`) — fica blocking.
- Não introduz Mongock — fica dívida explícita.
- Não migra Eureka, Redis ou LocalStack — só Postgres → Mongo.

---

## Definição de pronto

PR fechado em 2026-05-22:

1. [x] Todos os checkboxes das fases 1-5 marcados
2. [x] `docker-compose up` em volume limpo sobe tudo healthy sem intervenção manual
3. [x] Fluxo CRUD + dedupe funciona end-to-end
4. [x] Pasta `postgres/` apagada
5. [x] `outbox.md` atualizado com nota sobre a mudança de estratégia de lock
6. [x] `idempotency.md` §1.3 atualizado com a nota de Mongo

---

## Divergências do plano original

Coisas que mudaram durante a implementação e fogem do desenho inicial.

### Divergência #1 — `@EnableMongoAuditing` + `@CreatedDate` foi revertido

**Plano original (decisão #3)**: usar `@EnableMongoAuditing` + `@CreatedDate` no campo `createdAt` da entidade `Todo`. Spring Data Mongo populaaria automaticamente no save.

**O que aconteceu na verificação**: campo `createdAt` saiu **null** no response E não foi gravado no documento Mongo. O callback de auditing não rodou — causa provável: interação com `@Builder` do Lombok, ou conflito de versão do Spring Data Mongo 4.3.x. Tentar diagnosticar a fundo não compensava o esforço.

**Solução adotada**: caí no plano alternativo já listado no trade-off #4 — **setar manualmente no service**:
```java
entity.setId(UUID.randomUUID().toString());
entity.setCreatedAt(LocalDateTime.now());
```
Removi `@EnableMongoAuditing` de `TodoServiceApplication` e `@CreatedDate` da entidade. Mais explícito, sem mágica.

**Consequência**: a entidade `Todo` ficou simétrica (id e createdAt setados manualmente no mesmo lugar), e a aplicação não depende mais do callback de auditing.

### Divergência #2 — Porta do host mudou de `27017` para `27018`

**Plano original**: expor Mongo do Docker em `localhost:27017`.

**O que aconteceu**: a máquina do usuário tinha **MongoDB nativo instalado no Windows** (rodando como serviço), também escutando em `127.0.0.1:27017`. O Compass conectava nessa instância nativa (que estava vazia) em vez do nosso Docker. Sintoma: as databases `tododb` e `notificationdb` não apareciam no Compass mesmo com dados gravados.

**Solução adotada**: trocar o mapeamento de porta no `docker-compose.yml` pra `27018:27017`. Dentro da rede Docker, services continuam usando `mongo:27017` (não muda nada). Pra clientes do host (Compass, mongosh local, app rodando fora do Docker), a porta agora é 27018.

**Mudanças**:
- `docker-compose.yml`: `ports: ["27018:27017"]` no service `mongo`
- `todo-service/application.yml`: default URI virou `mongodb://localhost:27018/tododb?...`
- `notification-service/application.yml`: idem para `notificationdb`
- Connection string do Compass: `mongodb://localhost:27018/?directConnection=true`

**Anti-pattern Mongo (`.spec/02-anti-patterns/mongo-db.md`) — verificação**: nada do que está lá foi violado. Continua: replica set ativo, transações disponíveis, sem hardcoded credentials sensíveis (dev only), índice via `auto-index-creation` (dívida explícita).

### Divergência #3 — `GlobalExceptionHandler` e exception custom (não estava no plano)

Ao remover JPA, `jakarta.persistence.EntityNotFoundException` deixou de existir. O `TodoService.getOrThrow` usava ela. Tinham duas opções:
- usar `java.util.NoSuchElementException` (menos semântica)
- criar exception de domínio

**Escolhido**: criar `com.microservices.todo.exception.TodoNotFoundException extends RuntimeException`, atualizar `GlobalExceptionHandler` pra mapear → 404. Mais semântica, alinhada com o anti-pattern §REST/DTOs ("Crie exceções de domínio próprias").

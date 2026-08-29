# Regras de Engenharia para IA — MongoDB

> Leia antes as [regras gerais](./general.md) e [`java-spring.md`](./java-spring.md). Este arquivo cobre **apenas** o que é específico do uso de Mongo **neste projeto** (Mongo 7, single-node replica set `rs0`, Spring Data MongoDB 4.x).

---

## Antes de Sugerir Código

- Stack fixa: **MongoDB 7** em **single-node replica set** (`rs0`). Transações multi-doc disponíveis (precisam de replica set, não funcionam em standalone).
- Driver: **Spring Data MongoDB** (sobre o driver Java 5.x). Não sugerir Mongoose/Prisma/Node.
- Não há sharding, não há Atlas. Não sugerir regras de shard key, read preference de cluster, Atlas Search.
- Schema/índices versionados via **Mongock** (ver [`03-patterns/mongock.md`](../03-patterns/mongock.md)). `spring.data.mongodb.auto-index-creation` está **desligado** e `@Indexed`/`@CompoundIndex` na entidade está **banido** — schema é responsabilidade exclusiva de `@ChangeUnit`.

---

## Modelagem

- **Mongo não é SQL.** Modele por **padrão de acesso**, não por entidade. Não normalize por reflexo.
- **Embed quando**: dados lidos juntos, cardinalidade limitada, mudam juntos, cabem em 16 MB.
- **Referencie quando**: compartilhados entre múltiplos pais, crescem sem limite, ou acessados independentemente.
- Sem **array ilimitado** dentro de documento — fragmenta e estoura 16 MB.
- Sem replicar dado sem ter resposta para "como sincronizar quando mudar?".
- `_id` por padrão é `ObjectId` — só troque por `String`/`UUID` com motivo (o projeto usa `String` UUID em `Todo` e `OutboxEvent` — escolha consciente, mantenha o padrão).
- Sem nome de campo iniciando com `.` ou `$` — quebra queries.
- Sem campo com tipo variável (`age: 30` num doc, `"30"` em outro). Padronize tipos.

---

## Schema e Validação

- `@Document(collection = "...")`, `@Field`, `@Indexed` na entidade.
- Validação de entrada com **Bean Validation** (`@Valid` no controller + `@NotBlank` etc. no DTO). Não confie no DTO chegar limpo.
- `Decimal128` (`BigDecimal` no Java) para dinheiro. **Nunca** `Double` para valor monetário.
- `LocalDateTime`/`Instant` para datas — Spring Data converte pra BSON `Date`. Não armazene timestamp como `String`.
- Sem coleção "saco de gato" misturando domínios.

---

## Índices

- **Toda query frequente precisa de índice.** Sem índice = `COLLSCAN` = morte em volume real.
- Verifique com `.explain("executionStats")`. Procure `IXSCAN`, não `COLLSCAN`.
- Regra **ESR** para índice composto: **E**quality → **S**ort → **R**ange. Ordem importa.
- Índice composto cobre prefixos: `{a:1, b:1, c:1}` serve `{a}`, `{a,b}`, `{a,b,c}` — não `{b}` ou `{c}`.
- Use `@Indexed` em campo único, `@CompoundIndex` na classe para composto.
- **Não indexe tudo.** Cada índice custa escrita e memória.
- Use **TTL index** (`expireAfterSeconds`) para dado temporário (ex.: futuro cleanup de `outbox_events` publicados) — não escreva job manual.
- **Unique index** para garantir unicidade — não confie em `findOne` antes de `insert` (race condition).

---

## Queries

- Sempre **projete os campos necessários**. Não traga o documento inteiro se vai usar 3 campos.
- Sem `findAll()` sem `Pageable` em coleção que cresce.
- Paginação por **range query** (`_id > lastId`) em volume grande — `skip` em offset alto é caro.
- Use `$in` em vez de loop. **Nunca** `for (id : ids) findById(id)` — N+1 clássico.
- `$regex` sem âncora de início (`^`) e sem índice = `COLLSCAN`.
- `$regex` com input do usuário sem escapar = **NoSQL injection / ReDoS**. Escape ou valide.
- Sem `$where` com JavaScript.
- **Ordene por campo indexado.** Sort sem índice em conjunto grande estoura memória.

---

## Transações

- Disponíveis porque o projeto usa replica set (`rs0`). Em standalone lança `MongoTransactionException` em runtime.
- Use apenas quando **realmente precisa de atomicidade entre documentos**. Operação em um único documento já é atômica.
- Mantenha transação **curta** — segura locks e degrada o cluster.
- **Nunca dispare SQS / HTTP / e-mail dentro da TX esperando rollback "desfazer" o side-effect.** Use o [pattern Outbox](../03-patterns/outbox.md) — exatamente o que o `TodoService` faz.
- Não use transação pra resolver problema de modelagem — se você precisa atualizar 5 coleções juntas o tempo todo, repense o schema.
- `@Transactional` na camada Service (regra do java-spring.md).

---

## Escritas / Updates

- **`updateOne` / `updateMany` com operadores** (`$set`, `$inc`, `$push`, `$pull`, `$addToSet`). Sem substituir o documento inteiro sem motivo.
- **`$inc` em vez de `find → +1 → save`** — atômico no servidor, sem race condition.
- **`findOneAndUpdate` com `upsert`** para "criar ou atualizar". Não faça `findOne` + `if/else` + `insert`.
- **`findAndModify` atômico** para lease pattern / claim de fila (ver `OutboxEventRepositoryImpl.claimNext` — modelo de referência neste projeto).
- **`bulkWrite`** para múltiplas operações — uma round-trip em vez de N.
- `$push` com `$slice` e `$sort` para manter array limitado.
- `arrayFilters` para atualizar elementos específicos dentro de array.
- **Write concern** apropriado: `w: "majority"` para dado crítico. `w: 0` só para telemetria descartável.

---

## Spring Data MongoDB

- `MongoTemplate` para queries complexas (ver `OutboxEventRepositoryImpl`); `MongoRepository` para CRUD básico (ver `TodoRepository`, `OutboxEventRepository`).
- **Custom repository pattern**: interface `XxxRepositoryCustom` + impl `XxxRepositoryImpl` + `XxxRepository extends MongoRepository, XxxRepositoryCustom` — Spring Data costura sozinho. Use quando precisar de `MongoTemplate` sem perder os métodos derivados.
- Sem `findAll()` sem `Pageable` em coleção grande.
- **`@Indexed` / `@CompoundIndex` na entidade é proibido** neste projeto. Schema é versionado via Mongock (`@ChangeUnit` em `infrastructure/migration/`). Ver [`03-patterns/mongock.md`](../03-patterns/mongock.md).
- **`@DBRef` é anti-padrão** — força round-trip extra. Use referência manual (`String`/`ObjectId`) + lookup explícito quando precisar.
- **Sem expor `@Document` direto no controller** — sempre DTO.
- `@Transactional` exige replica set (já é o caso). Em standalone lança em runtime.
- Convenção de método derivado: `findByXAndYOrderByZAsc` segue a gramática do Spring Data. Não invente assinatura — se for complexo, vá pra `@Query` ou `MongoTemplate`.

---

## Segurança (núcleo)

- **Connection string em variável de ambiente**, nunca commitada. `.env` no `.gitignore` (já está).
- **NoSQL Injection**: nunca passe input do usuário direto como filtro. Em particular:
  - Não aceite objeto cru do body que vira `filter` (`{ user: body.user }` onde `user` pode ser `{ $ne: null }`).
  - Tipifique: se espera `String`, force `String` no DTO.
- `$regex` com input não escapado = ReDoS + injection.
- Em produção real (não LocalStack): TLS obrigatório, usuário do app com role mínima (`readWrite` no DB), Mongo nunca exposto na internet.
- Não logue connection string, payload com PII, nem documento contendo senha/token.

---

## Top Anti-Padrões da IA em Mongo (neste projeto)

1. Modelar como SQL — uma coleção por tabela, normalizado, lookups em toda query.
2. Array sem limite de crescimento dentro de documento.
3. `find({})` sem projeção e sem `limit`.
4. Query frequente sem índice (`COLLSCAN` em produção).
5. `for (id : ids) findById(id)` — N+1 clássico.
6. `findById` + modificar em Java + `save` em vez de `updateOne` com `$set`/`$inc`.
7. `findOne` + `if/else` + `insert` em vez de `findOneAndUpdate` com `upsert`.
8. `$regex` com input do usuário sem escape (injection + ReDoS).
9. Aceitar objeto cru do body como filtro de query (NoSQL injection).
10. Transação para problema que é de modelagem.
11. Transação esperando `rollback` desfazer side-effect externo (SQS/HTTP/e-mail) — use Outbox.
12. `Double` para valor monetário (deveria ser `BigDecimal` → `Decimal128`).
13. `@DBRef` sem necessidade.
14. `auto-index-creation: true` ou `@Indexed`/`@CompoundIndex` na entidade — schema é responsabilidade exclusiva do Mongock (ver `03-patterns/mongock.md`).
15. Connection string com credencial hardcoded.
16. `skip` grande para paginação em vez de range query por `_id`.
17. `countDocuments` sem filtro indexado em coleção enorme.
18. Inventar assinatura de método de `MongoRepository` que não segue a gramática (`findByXAndYOrderByZ...`).

---

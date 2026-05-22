# 10. Armadilhas que pegam todo mundo

| Armadilha | Solução | Onde o projeto evita |
|---|---|---|
| `Double` pra valor monetário | Use `BigDecimal` → vira `Decimal128` no BSON | Projeto não tem campo monetário — caso hipotético |
| `$regex` com input do usuário sem escapar | NoSQL injection + ReDoS — escape ou valide | Não há queries por `$regex` no projeto hoje |
| `for (id : ids) findById(id)` em loop | N+1 clássico — use `$in: [...]` | [`TodoService.findAll`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java) usa `repository.findAll().stream()` — single query |
| `skip(grande_offset)` pra paginação | Range query: `find({_id: {$gt: lastId}}).limit(N)` | Projeto ainda sem paginação — dívida futura |
| `findAll()` sem `Pageable` em coleção que cresce | Bomba relógio — sempre paginação | `todos` ainda é coleção pequena, mas é dívida explícita |
| Aceitar objeto cru do body como filtro de query | `{ user: body.user }` onde body manda `{$ne: null}` — NoSQL injection | DTOs tipados ([`TodoRequestDTO`](../../todo-service/src/main/java/com/microservices/todo/dto/request/TodoRequestDTO.java), [`TodoUpdateDTO`](../../todo-service/src/main/java/com/microservices/todo/dto/request/TodoUpdateDTO.java)) — body é validado antes de tocar query |
| `@DBRef` "porque é mais limpo" | Round-trip extra silencioso — use ref manual + lookup explícito quando precisar | [`OutboxEvent.aggregate_id`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/entity/OutboxEvent.java) é ref manual por string, sem `@DBRef` |
| Confiar em `auto-index-creation: true` em produção | Use [Mongock](../../.spec/03-patterns/mongock.md) — neste projeto, é proibido | [`V001_BaselineIndexes`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/migration/V001_BaselineIndexes.java) versiona o índice; `@Indexed` foi removido de [`OutboxEvent`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/entity/OutboxEvent.java) |

Catálogo completo com mais 10 anti-patterns: [`.spec/02-anti-patterns/mongo-db.md`](../../.spec/02-anti-patterns/mongo-db.md) (seção "Top Anti-Padrões da IA em Mongo").

---

[← Anterior: Transações multi-doc](./09-transacoes.md) · [Índice](./README.md)

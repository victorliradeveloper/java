# 6. Operadores de update — nunca substitua o documento inteiro

| Operador | O que faz |
|---|---|
| `$set` | Define valor de campo |
| `$unset` | Remove campo |
| `$inc` | Incrementa (atômico no servidor) |
| `$push` | Adiciona ao final de array |
| `$pull` | Remove elementos de array por filtro |
| `$addToSet` | `$push` sem duplicar |
| `$setOnInsert` | Aplica só no `upsert` (insert), ignora no update |

**`$inc` é atômico no servidor** — evita o anti-pattern `find → +1 → save` que tem race condition entre threads.

**Exemplo real no projeto** — [`V002_BackfillTodoUpdatedAt`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/migration/V002_BackfillTodoUpdatedAt.java) usa `updateMulti` pra preencher `updatedAt` em todos os Todos antigos onde o campo era null:

```java
mongoTemplate.updateMulti(
    Query.query(where("updatedAt").isNull()),
    AggregationUpdate.update()
        .set(SetOperation.set("updatedAt").toValueOf(Fields.field("createdAt"))),
    "todos"
);
```

Outro exemplo: [`ProcessedMessageRepositoryImpl.tryInsert`](../../notification-service/src/main/java/com/microservices/notification/infrastructure/repository/ProcessedMessageRepositoryImpl.java) usa `$setOnInsert` (operador especial que só aplica em insert via upsert):

```java
Update update = new Update().setOnInsert("processed_at", LocalDateTime.now());
mongoTemplate.upsert(query, update, ProcessedMessage.class);
```

Em duplicata, `$setOnInsert` ignora o valor — preserva o `processed_at` original. Ver detalhes em [Tópico 7](./07-atomic-find-and-update.md).

---

[← Anterior: Índices](./05-indices.md) · [Índice](./README.md) · [Próximo: Atomic single-doc + findOneAndUpdate →](./07-atomic-find-and-update.md)

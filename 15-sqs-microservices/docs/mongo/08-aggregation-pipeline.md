# 8. Aggregation Pipeline

O "SQL do Mongo" pra queries complexas. Sequência de estágios, cada um transforma o documento que vem do anterior.

Estágios principais:

| Estágio | Equivalente SQL |
|---|---|
| `$match` | `WHERE` |
| `$group` | `GROUP BY` |
| `$project` | `SELECT` (escolhe/renomeia campos) |
| `$sort` | `ORDER BY` |
| `$limit` / `$skip` | `LIMIT` / `OFFSET` |
| `$lookup` | `JOIN` (use com moderação — round-trip caro) |
| `$unwind` | Explode array em N documentos |

**Também serve pra updates com referência de campo** — foi exatamente o que a [`V002_BackfillTodoUpdatedAt`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/migration/V002_BackfillTodoUpdatedAt.java) usou:

```java
AggregationUpdate update = AggregationUpdate.update()
    .set(SetOperation.set("updatedAt").toValueOf(Fields.field("createdAt")));
// $set: { updatedAt: "$createdAt" } — interpreta como referência ao campo
```

Sem `AggregationUpdate`, `"$createdAt"` seria tratado como string literal.

---

[← Anterior: Atomic single-doc + findOneAndUpdate](./07-atomic-find-and-update.md) · [Índice](./README.md) · [Próximo: Transações multi-doc →](./09-transacoes.md)

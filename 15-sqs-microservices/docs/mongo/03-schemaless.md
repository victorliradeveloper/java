# 3. "Schemaless" é mentira útil

Mongo aceita campos diferentes por documento, mas você ainda precisa de um schema **mental**. Anti-pattern fatal:

```javascript
// Documento A
{ "age": 30 }
// Documento B
{ "age": "30" }
// Documento C
{ "age": null }
```

Query `{ age: { $gte: 18 } }` retorna **só A** — silenciosamente. Mongo não avisa que tipos divergem.

**Regra**: trate `@Document` como contrato — exemplo real em [`OutboxEvent.java`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/entity/OutboxEvent.java), onde cada campo tem tipo explícito e `@Field` força o nome BSON:

```java
@Document(collection = "outbox_events")
public class OutboxEvent {
    @Id
    private String id;
    @Field("aggregate_id")          // BSON em snake_case, Java em camelCase
    private String aggregateId;
    @Field("aggregate_type")
    private String aggregateType;
    @Field("published_at")
    private LocalDateTime publishedAt;   // tipo fixo - nunca String, nunca Object
    ...
}
```

Mude com migração — ver [`V002_BackfillTodoUpdatedAt.java`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/migration/V002_BackfillTodoUpdatedAt.java), que adiciona `updatedAt` em toda a collection de forma controlada — não com "um campo a mais aqui e ali".

---

[← Anterior: `_id` e `ObjectId`](./02-id-e-objectid.md) · [Índice](./README.md) · [Próximo: Embed vs Reference →](./04-embed-vs-reference.md)

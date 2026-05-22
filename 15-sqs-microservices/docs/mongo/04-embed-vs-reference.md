# 4. Embed vs Reference — a decisão de modelagem mais importante

SQL normaliza por reflexo, Mongo embeda por reflexo.

| Cenário | Decisão |
|---|---|
| Dado lido junto, cardinalidade limitada, muda junto, cabe em 16MB | **Embed** |
| Compartilhado entre múltiplos pais, cresce sem limite, acessado independente | **Reference** (guarde só o `_id`, faça lookup explícito) |

**Exemplo real no projeto** — caso clássico de **reference**: [`OutboxEvent`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/entity/OutboxEvent.java) guarda o UUID do Todo (não o doc inteiro):

```java
@Document(collection = "outbox_events")
public class OutboxEvent {
    @Id
    private String id;
    @Field("aggregate_id")
    private String aggregateId;      // UUID do Todo (referencia manual)
    @Field("aggregate_type")
    private String aggregateType;    // "Todo"
    ...
}
```

Motivo da escolha: eventos da outbox são acessados independentemente pelo `OutboxPublisher` (lookup por `published_at`, não por agregado), sobrevivem ao delete do Todo original, e crescem sem limite. **Sem `@DBRef`** — referência manual por string, como o anti-pattern manda.

Exemplos hipotéticos no projeto:

- Se cada `Todo` tivesse **comentários** (poucos, sempre lidos junto): embed array.
- Se cada `Todo` tivesse **autor** (compartilhado entre vários, mudável): reference por `userId`.

Anti-pattern: array sem limite dentro de documento. 16MB de teto **por documento** estoura.

---

[← Anterior: "Schemaless" é mentira útil](./03-schemaless.md) · [Índice](./README.md) · [Próximo: Índices →](./05-indices.md)

# 2. `_id` é obrigatório, default é `ObjectId`

Todo documento tem `_id` único. Default é `ObjectId` (12 bytes contendo timestamp + máquina + contador), gerado pelo servidor se você não passar.

Neste projeto: `_id` é **UUID string** (decisão consciente — preserva contrato HTTP da API). Ver [`Todo.java`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/entity/Todo.java):

```java
@Document(collection = "todos")
public class Todo {
    @Id
    private String id;     // UUID, setado pelo TodoService
    ...
}
```

O UUID é gerado e setado em [`TodoService.create()`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java):

```java
Todo entity = mapper.toEntity(dto);
entity.setId(UUID.randomUUID().toString());   // _id explicito antes do save
entity.setCreatedAt(now);
entity.setUpdatedAt(now);
Todo todo = repository.save(entity);
```

Setar explícito antes de `save` é necessário porque, sem `@GeneratedValue` (que é JPA, não Mongo), Spring Data não preenche o `_id` sozinho — se ficar null, Mongo gera um `ObjectId` automaticamente, quebrando o contrato.

O índice em `_id` é **automático e único** — é a única garantia de unicidade que o Mongo te dá sem você pedir.

---

[← Anterior: Documento/Collection/Database](./01-documento-collection-database.md) · [Índice](./README.md) · [Próximo: "Schemaless" é mentira útil →](./03-schemaless.md)

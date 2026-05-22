# 9. Transações multi-doc — exceção, não regra

Existem (precisam de **replica set**, o que já temos — `rs0`). Mas:

- **Operação em um único documento já é atômica.** Não precisa de TX.
- TX é cara: segura locks, degrada cluster, tem teto de 16MB no log de operações.
- **Curtas, raras, last-resort.** Se você precisa de TX o tempo todo entre 5 coleções, o problema é a **modelagem**, não a falta de TX.

## Caso real no projeto — TX correta com Outbox

[`TodoService`](../../todo-service/src/main/java/com/microservices/todo/service/TodoService.java) é o exemplo canônico no projeto. Tem `@Transactional` nos 3 métodos de escrita (`create`, `update`, `delete`) e dispara eventos pra SQS — mas **nunca chama `SqsTemplate.send` dentro da TX**. Em vez disso, grava o evento na collection `outbox_events` (mesma TX do save do Todo) e deixa um scheduler publicar depois:

```java
@Transactional
public TodoResponseDTO create(TodoRequestDTO dto) {
    Todo entity = mapper.toEntity(dto);
    entity.setId(UUID.randomUUID().toString());
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    Todo todo = repository.save(entity);                    // ┐
    outboxService.record(                                   // │ commit atomico
            SqsConfig.QUEUE_CREATED,                        // │ no MESMO banco
            response.id(),                                  // │
            "CREATED",                                      // │
            TodoEvent.of(...)                               // ┘
    );
    return mapper.toResponse(todo);
}
```

**Por que isso resolve o problema**: se o `repository.save` ou o `outboxService.record` falhar, **ambos** dão rollback no Mongo. Se commit bem, **ambos** persistem. O publisher externo ([`OutboxPublisher`](../../todo-service/src/main/java/com/microservices/todo/outbox/OutboxPublisher.java)) lê depois e manda pro SQS — fora da TX, com retry próprio.

## O que NÃO fazer

```java
// RUIM — anti-pattern classico:
@Transactional
public void create(...) {
    Todo todo = repository.save(entity);
    sqsTemplate.send(QUEUE_CREATED, event);   // banco rola back -> email ja saiu
}
```

Banco aborta a TX, mas o email/evento já foi pro broker. Mensagem fantasma. Esse é exatamente o problema que o [pattern Outbox](../../.spec/03-patterns/outbox.md) resolve.

## Caveat do Mongock

Transação por migration tem armadilha extra: **DDL (`createIndex`, `createCollection`) não roda dentro de TX** (limitação do MongoDB). Por isso `mongock.transactional: false` global; só DML (data migration) habilita transação na anotação. Ver [Gotcha #1 em `mongock.md`](../../.spec/03-patterns/mongock.md#gotcha-1--ddl-não-roda-dentro-de-transação-no-mongodb) e a migration [`V002_BackfillTodoUpdatedAt`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/migration/V002_BackfillTodoUpdatedAt.java) que usa `transactional = true` por ser DML pura.

---

[← Anterior: Aggregation Pipeline](./08-aggregation-pipeline.md) · [Índice](./README.md) · [Próximo: Armadilhas →](./10-armadilhas.md)

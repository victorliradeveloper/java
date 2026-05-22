# 1. Documento, collection, database

| Conceito SQL | Equivalente Mongo |
|---|---|
| Linha (row) | Documento (BSON — JSON binário) |
| Tabela | Collection |
| Schema | Database |

Neste projeto:

| Container | Database | Collections |
|---|---|---|
| `todo-mongo` | `tododb` | [`todos`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/entity/Todo.java), [`outbox_events`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/entity/OutboxEvent.java), `mongockChangeLog`, `mongockLock` |
| `todo-mongo` | `notificationdb` | [`processed_messages`](../../notification-service/src/main/java/com/microservices/notification/infrastructure/entity/ProcessedMessage.java), `mongockChangeLog`, `mongockLock` |

Mongo cria a collection **on demand** no primeiro insert. Não precisa `CREATE TABLE`.

---

[← Índice](./README.md) · [Próximo: `_id` e `ObjectId` →](./02-id-e-objectid.md)

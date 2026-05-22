# 7. Atomic single-doc + `findOneAndUpdate` com `upsert`

**Tudo em um único documento é atômico no Mongo.** Você não precisa de transação pra atualizar um array dentro do mesmo doc.

## Anti-pattern: `findOne + if/else + insert`

```java
// RUIM — race condition entre threads/instancias:
Optional<X> existing = repo.findById(id);
if (existing.isEmpty()) {
    repo.insert(new X(id));      // duas threads podem chegar aqui ao mesmo tempo
} else {
    repo.save(existing.get().update(...));
}
```

Entre o `findById` e o `insert`, outra thread pode inserir o mesmo `_id`. Resultado: `DuplicateKeyException` (se tem unique index) ou doc duplicado (se não tem).

## Solução real no projeto — `upsert` com `$setOnInsert`

O `notification-service` precisa dedupe de mensagens SQS (consumer é at-least-once). A solução é exatamente esse padrão. Ver [`ProcessedMessageRepositoryImpl.tryInsert`](../../notification-service/src/main/java/com/microservices/notification/infrastructure/repository/ProcessedMessageRepositoryImpl.java):

```java
public boolean tryInsert(String messageId) {
    Query query = new Query(Criteria.where("_id").is(messageId));
    Update update = new Update().setOnInsert("processed_at", LocalDateTime.now());
    UpdateResult result = mongoTemplate.upsert(query, update, ProcessedMessage.class);
    // matchedCount == 0 → nao tinha doc com esse _id → inserimos (novo)
    // matchedCount  > 0 → ja existia → duplicata, nada gravado
    return result.getMatchedCount() == 0;
}
```

Como funciona:

1. **`upsert`** = "atualize se achar, insira se não achar" — atômico no servidor.
2. **`$setOnInsert`** = só aplica os campos no insert, ignora no update — preserva `processed_at` original em caso de duplicata.
3. **`matchedCount`** = quantos docs já existiam. 0 = inserimos novo, >0 = era duplicata.

Sem race condition, sem `try/catch DuplicateKeyException`, sem segunda round-trip.

## Padrão lease — outro caso de atomic single-doc

[`OutboxEventRepositoryImpl.claimNext`](../../todo-service/src/main/java/com/microservices/todo/infrastructure/repository/OutboxEventRepositoryImpl.java) usa `findAndModify` (não upsert) pra reivindicar um evento da outbox atomicamente. É a versão Mongo do `SELECT FOR UPDATE SKIP LOCKED` do Postgres:

```java
Query query = new Query()
        .addCriteria(new Criteria().andOperator(
                Criteria.where("published_at").is(null),
                leaseAvailable
        ))
        .with(Sort.by(Sort.Direction.ASC, "created_at"));

Update update = new Update()
        .set("processing_node", nodeId)
        .set("lease_expires_at", leaseExpiry);

OutboxEvent claimed = mongoTemplate.findAndModify(query, update, options, OutboxEvent.class);
```

Dois workers chamando ao mesmo tempo: só um pega o doc. Não precisa de lock externo, sessão, ou semáforo — atomicidade single-doc resolve.

---

[← Anterior: Operadores de update](./06-operadores-update.md) · [Índice](./README.md) · [Próximo: Aggregation Pipeline →](./08-aggregation-pipeline.md)

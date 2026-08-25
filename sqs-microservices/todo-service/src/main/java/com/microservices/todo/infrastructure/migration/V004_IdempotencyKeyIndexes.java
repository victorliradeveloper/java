package com.microservices.todo.infrastructure.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

// TTL index em expires_at: o Mongo apaga docs com expires_at < now automaticamente
// (background thread roda a cada 60s). Substitui job de cleanup manual.
//
// expireAfterSeconds=0 NAO significa "expira imediatamente"; significa "expira
// quando o campo (datetime) for menor que now". Eh o idiom canonico do MongoDB
// pra TTL baseado em valor de campo, e nao em delta a partir da criacao.
//
// O unique index em _id eh implicito do Mongo — nao precisa criar.
@ChangeUnit(id = "V004_idempotency_key_indexes", order = "004", author = "victor")
public class V004_IdempotencyKeyIndexes {

    private static final String COLLECTION = "idempotency_keys";
    private static final String EXPIRES_AT = "expires_at";

    @Execution
    public void execution(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(COLLECTION)
                .ensureIndex(new Index().on(EXPIRES_AT, Sort.Direction.ASC).expire(0));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(COLLECTION).dropIndex(EXPIRES_AT + "_1");
    }
}

package com.microservices.todo.infrastructure.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.IndexDefinition;

// Indice composto pra otimizar o claim do OutboxPublisher apos a introducao
// do backoff (campo next_attempt_at). claimNext filtra por:
//   published_at == null
//   AND (lease_expires_at == null OR lease_expires_at < now)
//   AND (next_attempt_at  == null OR next_attempt_at  <= now)
//   ORDER BY created_at ASC
//
// O indice (published_at, next_attempt_at, created_at) cobre:
//   - equality em published_at=null (parte E do ESR);
//   - range em next_attempt_at (parte R);
//   - sort por created_at (parte S — sub-otima vindo depois do range,
//     mas aceitavel pra o volume deste projeto; em volume alto, considerar
//     sortear por next_attempt_at e remover o sort por created_at).
//
// O indice antigo {published_at: 1} (V001) eh tecnicamente prefixo deste novo
// e poderia ser dropado, mas V001 fica intocada por imutabilidade de @ChangeUnit
// ja aplicada. Custo de manter os dois eh baixo (mesma collection, poucas writes).
@ChangeUnit(id = "V005_outbox_next_attempt_index", order = "005", author = "victor")
public class V005_OutboxNextAttemptIndex {

    private static final String COLLECTION = "outbox_events";
    private static final String INDEX_NAME = "published_at_1_next_attempt_at_1_created_at_1";

    @Execution
    public void execution(MongoTemplate mongoTemplate) {
        Document keys = new Document()
                .append("published_at", 1)
                .append("next_attempt_at", 1)
                .append("created_at", 1);
        IndexDefinition index = new CompoundIndexDefinition(keys).named(INDEX_NAME);
        mongoTemplate.indexOps(COLLECTION).ensureIndex(index);
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(COLLECTION).dropIndex(INDEX_NAME);
    }
}

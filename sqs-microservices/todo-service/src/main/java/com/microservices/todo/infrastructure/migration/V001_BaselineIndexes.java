package com.microservices.todo.infrastructure.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@ChangeUnit(id = "V001_baseline_indexes", order = "001", author = "victor")
public class V001_BaselineIndexes {

    private static final String OUTBOX_COLLECTION = "outbox_events";
    private static final String PUBLISHED_AT_FIELD = "published_at";

    @Execution
    public void execution(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(OUTBOX_COLLECTION)
                .ensureIndex(new Index().on(PUBLISHED_AT_FIELD, Sort.Direction.ASC));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(OUTBOX_COLLECTION)
                .dropIndex(PUBLISHED_AT_FIELD + "_1");
    }
}

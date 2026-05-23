package com.microservices.audit.infrastructure.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/**
 * Cria indices para queries comuns no audit log:
 *  - aggregate_id: "todas as mudancas de um Todo X" (timeline por entidade).
 *  - event_type + occurred_at: "todos os DELETED nos ultimos 30 dias", etc.
 *  - occurred_at desc: listagem cronologica reversa (default em dashboards).
 *
 * Sem indice em _id explicito porque MongoDB ja cria um _id_ unique automatico
 * — eh o que faz o dedupe natural via DuplicateKeyException funcionar.
 */
@ChangeUnit(id = "V001_baseline_indexes", order = "001", author = "victor")
public class V001_BaselineIndexes {

    private static final String AUDIT = "todo_audit_log";
    private static final String AGGREGATE_ID = "aggregate_id";
    private static final String EVENT_TYPE = "event_type";
    private static final String OCCURRED_AT = "occurred_at";

    @Execution
    public void execution(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(AUDIT)
                .ensureIndex(new Index().on(AGGREGATE_ID, Sort.Direction.ASC));
        mongoTemplate.indexOps(AUDIT)
                .ensureIndex(new Index()
                        .on(EVENT_TYPE, Sort.Direction.ASC)
                        .on(OCCURRED_AT, Sort.Direction.DESC));
        mongoTemplate.indexOps(AUDIT)
                .ensureIndex(new Index().on(OCCURRED_AT, Sort.Direction.DESC));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(AUDIT).dropIndex(AGGREGATE_ID + "_1");
        mongoTemplate.indexOps(AUDIT).dropIndex(EVENT_TYPE + "_1_" + OCCURRED_AT + "_-1");
        mongoTemplate.indexOps(AUDIT).dropIndex(OCCURRED_AT + "_-1");
    }
}

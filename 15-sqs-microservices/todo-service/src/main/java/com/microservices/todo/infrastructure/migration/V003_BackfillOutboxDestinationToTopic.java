package com.microservices.todo.infrastructure.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

// Migra outbox_events pendentes (publishedAt = null) que ainda tem destination
// apontando para as filas SQS antigas (pre fan-out) para o topic SNS unificado.
//
// Eventos ja publicados (publishedAt != null) ficam intactos como registro
// historico de onde foram parar — mudar isso reescreveria a verdade do passado.
//
// transactional=true: DML pura (updateMulti), elegivel para TX. O volume eh
// limitado a eventos pendentes (normalmente <1000 mesmo em sistemas ativos)
// — single-TX seguro.
@ChangeUnit(id = "V003_backfill_outbox_destination_to_topic", order = "003", author = "victor", transactional = true)
public class V003_BackfillOutboxDestinationToTopic {

    private static final String OUTBOX = "outbox_events";
    private static final String DESTINATION = "destination";
    private static final String PUBLISHED_AT = "published_at";
    private static final String TOPIC_TODO_EVENTS = "todo-events";

    private static final List<String> LEGACY_QUEUES = List.of(
            "todo-created-queue",
            "todo-updated-queue",
            "todo-deleted-queue"
    );

    @Execution
    public void execution(MongoTemplate mongoTemplate) {
        Query pendingWithLegacyDestination = Query.query(
                where(PUBLISHED_AT).isNull()
                        .and(DESTINATION).in(LEGACY_QUEUES)
        );
        Update toTopic = new Update().set(DESTINATION, TOPIC_TODO_EVENTS);
        mongoTemplate.updateMulti(pendingWithLegacyDestination, toTopic, OUTBOX);
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        // Rollback impossivel sem perda de informacao: nao da pra inferir qual
        // fila SQS legada cada evento usaria (action eh CREATED/UPDATED/DELETED,
        // mas o destination mapeava 1:1 — informacao redundante hoje, perdida
        // depois do update). No-op intencional: em dev, recriar a base.
    }
}

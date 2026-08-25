package com.microservices.todo.infrastructure.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.aggregation.SetOperation;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.springframework.data.mongodb.core.query.Criteria.where;

// Backfill de updatedAt em documentos pre-existentes.
// transactional=true: DML pura, todos os docs em uma TX. Nao escala para milhoes
// de docs (16MB de limite de TX no Mongo) - nesse caso, batch + multiplas TX.
// Para o tamanho deste projeto, single-TX eh seguro.
@ChangeUnit(id = "V002_backfill_todo_updated_at", order = "002", author = "victor", transactional = true)
public class V002_BackfillTodoUpdatedAt {

    private static final String TODOS = "todos";
    private static final String UPDATED_AT = "updatedAt";
    private static final String CREATED_AT = "createdAt";

    @Execution
    public void execution(MongoTemplate mongoTemplate) {
        // Aggregation pipeline update permite usar $createdAt como referencia
        // de campo. Update normal interpretaria "$createdAt" como string literal.
        AggregationUpdate update = AggregationUpdate.update()
                .set(SetOperation.set(UPDATED_AT).toValueOf(Fields.field(CREATED_AT)));

        mongoTemplate.updateMulti(
                Query.query(where(UPDATED_AT).isNull()),
                update,
                TODOS
        );
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        // Destrutivo: remove updatedAt de todos os docs. Nao distingue docs
        // backfilled de docs criados depois pela aplicacao. Use apenas em dev
        // para reverter; em prod, prefira criar V00X de correcao para a frente.
        mongoTemplate.updateMulti(new Query(), new Update().unset(UPDATED_AT), TODOS);
    }
}

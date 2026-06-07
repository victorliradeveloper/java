package com.microservices.todo.infrastructure.migration;

import com.microservices.todo.infrastructure.entity.Priority;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.springframework.data.mongodb.core.query.Criteria.where;

// Backfill de priority em documentos pre-existentes que nao tinham o campo.
// Default MEDIUM segue a mesma regra do TodoService.create quando o cliente
// omite priority no POST — consistencia entre docs antigos e novos.
@ChangeUnit(id = "V006_backfill_todo_priority", order = "006", author = "victor", transactional = true)
public class V006_BackfillTodoPriority {

    private static final String TODOS = "todos";
    private static final String PRIORITY = "priority";

    @Execution
    public void execution(MongoTemplate mongoTemplate) {
        mongoTemplate.updateMulti(
                Query.query(where(PRIORITY).exists(false)),
                new Update().set(PRIORITY, Priority.MEDIUM.name()),
                TODOS
        );
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        // Destrutivo: remove priority de todos os docs. Nao distingue docs
        // backfilled de docs criados depois pela aplicacao. Use apenas em dev
        // para reverter; em prod, prefira criar V00X de correcao para a frente.
        mongoTemplate.updateMulti(new Query(), new Update().unset(PRIORITY), TODOS);
    }
}

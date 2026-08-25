package com.microservices.todo.infrastructure.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@ChangeUnit(id = "V007_todos_priority_index", order = "007", author = "victor")
public class V007_TodosPriorityIndex {

    private static final String TODOS = "todos";
    private static final String PRIORITY = "priority";

    @Execution
    public void execution(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(TODOS)
                .ensureIndex(new Index().on(PRIORITY, Sort.Direction.ASC));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(TODOS).dropIndex(PRIORITY + "_1");
    }
}

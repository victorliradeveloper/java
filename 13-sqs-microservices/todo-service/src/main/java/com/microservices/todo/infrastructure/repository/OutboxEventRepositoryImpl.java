package com.microservices.todo.infrastructure.repository;

import com.microservices.todo.infrastructure.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<OutboxEvent> claimNext(String nodeId, Duration leaseDuration) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseExpiry = now.plus(leaseDuration);

        // Filtros do claim:
        // - publishedAt == null         → evento ainda nao saiu
        // - lease ausente ou expirado   → ninguem detem o lease
        // - nextAttemptAt ausente ou <= now → ja eh hora de tentar (respeita backoff)
        Criteria leaseAvailable = new Criteria().orOperator(
                Criteria.where("lease_expires_at").is(null),
                Criteria.where("lease_expires_at").lt(now)
        );
        Criteria backoffElapsed = new Criteria().orOperator(
                Criteria.where("next_attempt_at").is(null),
                Criteria.where("next_attempt_at").lte(now)
        );
        Query query = new Query()
                .addCriteria(new Criteria().andOperator(
                        Criteria.where("published_at").is(null),
                        leaseAvailable,
                        backoffElapsed
                ))
                .with(Sort.by(Sort.Direction.ASC, "created_at"));

        Update update = new Update()
                .set("processing_node", nodeId)
                .set("lease_expires_at", leaseExpiry);

        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        OutboxEvent claimed = mongoTemplate.findAndModify(query, update, options, OutboxEvent.class);
        return Optional.ofNullable(claimed);
    }
}

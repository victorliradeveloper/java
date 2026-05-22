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

        // publishedAt == null AND (leaseExpiresAt == null OR leaseExpiresAt < now)
        Criteria leaseAvailable = new Criteria().orOperator(
                Criteria.where("lease_expires_at").is(null),
                Criteria.where("lease_expires_at").lt(now)
        );
        Query query = new Query()
                .addCriteria(new Criteria().andOperator(
                        Criteria.where("published_at").is(null),
                        leaseAvailable
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

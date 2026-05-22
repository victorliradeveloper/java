package com.microservices.notification.infrastructure.repository;

import com.microservices.notification.infrastructure.entity.ProcessedMessage;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;

@RequiredArgsConstructor
public class ProcessedMessageRepositoryImpl implements ProcessedMessageRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public boolean tryInsert(String messageId) {
        Query query = new Query(Criteria.where("_id").is(messageId));
        Update update = new Update().setOnInsert("processed_at", LocalDateTime.now());
        UpdateResult result = mongoTemplate.upsert(query, update, ProcessedMessage.class);
        // matchedCount == 0 → nao tinha doc com esse _id → inserimos (novo)
        // matchedCount  > 0 → ja existia → duplicata, nada gravado
        return result.getMatchedCount() == 0;
    }
}

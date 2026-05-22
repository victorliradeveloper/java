package com.microservices.notification.infrastructure.repository;

import com.microservices.notification.infrastructure.entity.ProcessedMessage;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProcessedMessageRepository
        extends MongoRepository<ProcessedMessage, String>, ProcessedMessageRepositoryCustom {
}

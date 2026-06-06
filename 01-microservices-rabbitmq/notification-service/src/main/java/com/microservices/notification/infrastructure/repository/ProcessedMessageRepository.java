package com.microservices.notification.infrastructure.repository;

import com.microservices.notification.infrastructure.entity.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageRepository
        extends JpaRepository<ProcessedMessage, String>, ProcessedMessageRepositoryCustom {
}

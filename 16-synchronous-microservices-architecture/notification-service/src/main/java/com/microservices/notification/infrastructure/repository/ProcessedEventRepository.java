package com.microservices.notification.infrastructure.repository;

import com.microservices.notification.infrastructure.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository
        extends JpaRepository<ProcessedEvent, String>, ProcessedEventRepositoryCustom {
}

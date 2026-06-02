package com.webhook.order.repository;

import com.webhook.order.domain.ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, UUID> {
}

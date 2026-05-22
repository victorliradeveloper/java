package com.microservices.notification.infrastructure.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document(collection = "processed_messages")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedMessage {

    @Id
    private String messageId;

    @Field("processed_at")
    private LocalDateTime processedAt;
}

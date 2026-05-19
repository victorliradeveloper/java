package com.microservices.notification.config;

/**
 * Nomes das filas SQS consumidas pelo notification-service.
 * Devem bater com os nomes usados pelo todo-service (publisher) e
 * com os criados pelo init-script do LocalStack.
 */
public final class SqsConfig {

    public static final String QUEUE_CREATED = "todo-created-queue";
    public static final String QUEUE_UPDATED = "todo-updated-queue";
    public static final String QUEUE_DELETED = "todo-deleted-queue";

    private SqsConfig() {}
}

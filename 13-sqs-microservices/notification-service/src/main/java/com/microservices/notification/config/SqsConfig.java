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

    // DLQs (dead-letter queues). Cada fila principal tem uma DLQ correspondente
    // associada via RedrivePolicy (maxReceiveCount=3) no init-aws.sh.
    // Ver .spec/03-patterns/dlq.md.
    public static final String QUEUE_CREATED_DLQ = "todo-created-dlq";
    public static final String QUEUE_UPDATED_DLQ = "todo-updated-dlq";
    public static final String QUEUE_DELETED_DLQ = "todo-deleted-dlq";

    private SqsConfig() {}
}

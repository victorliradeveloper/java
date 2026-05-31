package com.microservices.audit.config;

/**
 * Nomes das filas SQS consumidas pelo audit-service.
 *
 * O audit consome uma unica fila (todo-audit-queue) que esta inscrita no
 * topic SNS todo-events sem FilterPolicy — recebe todos os eventos (CREATED,
 * UPDATED, DELETED) de todos os Todos. Provisionada por
 * localstack/init-aws.sh.
 */
public final class SqsConfig {

    public static final String QUEUE_AUDIT = "todo-audit-queue";
    public static final String QUEUE_AUDIT_DLQ = "todo-audit-dlq";

    private SqsConfig() {}
}

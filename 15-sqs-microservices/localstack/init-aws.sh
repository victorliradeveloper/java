#!/bin/bash
# Script executado pelo LocalStack ao ficar "ready".
# Cria as 3 filas SQS usadas pelo todo-service (publisher) e
# notification-service (consumer).

set -e

echo "[init-aws] Criando filas SQS..."

awslocal sqs create-queue --queue-name todo-created-queue
awslocal sqs create-queue --queue-name todo-updated-queue
awslocal sqs create-queue --queue-name todo-deleted-queue

echo "[init-aws] Filas criadas:"
awslocal sqs list-queues

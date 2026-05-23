#!/bin/bash
# Script executado pelo LocalStack ao ficar "ready".
# Cria:
#   - 3 filas principais (todo-created/updated/deleted-queue) consumidas
#     pelo notification-service.
#   - 3 DLQs correspondentes (sufixo -dlq).
#   - RedrivePolicy nas filas principais apontando pra cada DLQ
#     com maxReceiveCount=3 (3 tentativas e a mensagem vai pra DLQ).
#
# DLQ pattern: ver .spec/03-patterns/dlq.md

set -e

ACCOUNT_ID="000000000000"
REGION="us-east-1"
MAX_RECEIVE_COUNT=3

echo "[init-aws] Criando DLQs..."
awslocal sqs create-queue --queue-name todo-created-dlq
awslocal sqs create-queue --queue-name todo-updated-dlq
awslocal sqs create-queue --queue-name todo-deleted-dlq

echo "[init-aws] Criando filas principais..."
awslocal sqs create-queue --queue-name todo-created-queue
awslocal sqs create-queue --queue-name todo-updated-queue
awslocal sqs create-queue --queue-name todo-deleted-queue

# Associa cada fila principal a sua DLQ via RedrivePolicy.
# O valor de RedrivePolicy e ele proprio uma string JSON-encoded
# (formato da API SQS), por isso o duplo escape ao montar o payload.
configure_dlq() {
  local main_queue=$1
  local dlq_name=$2
  local dlq_arn="arn:aws:sqs:${REGION}:${ACCOUNT_ID}:${dlq_name}"
  local queue_url
  queue_url=$(awslocal sqs get-queue-url --queue-name "${main_queue}" --output text --query QueueUrl)

  local attributes
  attributes=$(cat <<EOF
{
  "RedrivePolicy": "{\"deadLetterTargetArn\":\"${dlq_arn}\",\"maxReceiveCount\":\"${MAX_RECEIVE_COUNT}\"}"
}
EOF
)

  awslocal sqs set-queue-attributes \
    --queue-url "${queue_url}" \
    --attributes "${attributes}" >/dev/null

  echo "[init-aws]   ${main_queue} -> ${dlq_name} (maxReceiveCount=${MAX_RECEIVE_COUNT})"
}

echo "[init-aws] Configurando RedrivePolicy..."
configure_dlq todo-created-queue todo-created-dlq
configure_dlq todo-updated-queue todo-updated-dlq
configure_dlq todo-deleted-queue todo-deleted-dlq

echo "[init-aws] Filas criadas:"
awslocal sqs list-queues

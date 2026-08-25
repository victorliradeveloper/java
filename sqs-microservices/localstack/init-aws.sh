#!/bin/bash
# Script executado pelo LocalStack ao ficar "ready".
#
# Provisiona o pipeline SNS + SQS fan-out:
#
#   todo-service ──► SNS topic (todo-events) ──┬─► todo-created-queue ──► notification-service
#                                              │   (filter: action=CREATED)
#                                              ├─► todo-updated-queue ──► notification-service
#                                              │   (filter: action=UPDATED)
#                                              ├─► todo-deleted-queue ──► notification-service
#                                              │   (filter: action=DELETED)
#                                              └─► todo-audit-queue   ──► audit-service
#                                                  (sem filtro — recebe tudo)
#
# Cada fila principal tem DLQ correspondente (sufixo -dlq) com RedrivePolicy
# maxReceiveCount=3.
#
# RawMessageDelivery=true em todas as subscriptions: o SQS recebe o payload
# bruto (sem envelope SNS), o que permite ao Spring Cloud AWS desserializar
# direto pro POJO TodoEvent no listener.
#
# Patterns: ver .spec/03-patterns/{dlq,outbox,fan-out}.md

set -e

ACCOUNT_ID="000000000000"
REGION="us-east-1"
MAX_RECEIVE_COUNT=3
TOPIC_NAME="todo-events"
TOPIC_ARN="arn:aws:sns:${REGION}:${ACCOUNT_ID}:${TOPIC_NAME}"

echo "[init-aws] Criando topic SNS..."
awslocal sns create-topic --name "${TOPIC_NAME}" >/dev/null

echo "[init-aws] Criando DLQs..."
awslocal sqs create-queue --queue-name todo-created-dlq >/dev/null
awslocal sqs create-queue --queue-name todo-updated-dlq >/dev/null
awslocal sqs create-queue --queue-name todo-deleted-dlq >/dev/null
awslocal sqs create-queue --queue-name todo-audit-dlq   >/dev/null

echo "[init-aws] Criando filas principais..."
awslocal sqs create-queue --queue-name todo-created-queue >/dev/null
awslocal sqs create-queue --queue-name todo-updated-queue >/dev/null
awslocal sqs create-queue --queue-name todo-deleted-queue >/dev/null
awslocal sqs create-queue --queue-name todo-audit-queue   >/dev/null

# ---- RedrivePolicy: principal -> DLQ ----------------------------------------

configure_dlq() {
  local main_queue=$1
  local dlq_name=$2
  local dlq_arn="arn:aws:sqs:${REGION}:${ACCOUNT_ID}:${dlq_name}"
  local queue_url
  queue_url=$(awslocal sqs get-queue-url --queue-name "${main_queue}" --output text --query QueueUrl)

  local attributes
  attributes=$(cat <<EOF
{
  "RedrivePolicy": "{\"deadLetterTargetArn\":\"${dlq_arn}\",\"maxReceiveCount\":\"${MAX_RECEIVE_COUNT}\"}",
  "ReceiveMessageWaitTimeSeconds": "20"
}
EOF
)

  awslocal sqs set-queue-attributes \
    --queue-url "${queue_url}" \
    --attributes "${attributes}" >/dev/null

  echo "[init-aws]   ${main_queue} -> ${dlq_name} (maxReceiveCount=${MAX_RECEIVE_COUNT}, longPolling=20s)"
}

echo "[init-aws] Configurando RedrivePolicy + long polling..."
configure_dlq todo-created-queue todo-created-dlq
configure_dlq todo-updated-queue todo-updated-dlq
configure_dlq todo-deleted-queue todo-deleted-dlq
configure_dlq todo-audit-queue   todo-audit-dlq

# ---- SNS subscriptions com FilterPolicy + RawMessageDelivery ----------------

subscribe_queue_to_topic() {
  local queue_name=$1
  local filter_policy=$2   # JSON string, vazio = sem filtro (recebe tudo)
  local queue_arn="arn:aws:sqs:${REGION}:${ACCOUNT_ID}:${queue_name}"

  local subscription_arn
  subscription_arn=$(awslocal sns subscribe \
    --topic-arn "${TOPIC_ARN}" \
    --protocol sqs \
    --notification-endpoint "${queue_arn}" \
    --output text --query SubscriptionArn)

  # RawMessageDelivery: entrega o body bruto na fila (sem envelope SNS).
  # Sem isso, o consumer receberia {"Type":"Notification","Message":"<payload>"...}
  # e precisaria de parsing extra.
  awslocal sns set-subscription-attributes \
    --subscription-arn "${subscription_arn}" \
    --attribute-name RawMessageDelivery \
    --attribute-value true

  if [ -n "${filter_policy}" ]; then
    awslocal sns set-subscription-attributes \
      --subscription-arn "${subscription_arn}" \
      --attribute-name FilterPolicy \
      --attribute-value "${filter_policy}"
    echo "[init-aws]   ${queue_name} <- ${TOPIC_NAME} (filter=${filter_policy})"
  else
    echo "[init-aws]   ${queue_name} <- ${TOPIC_NAME} (sem filtro)"
  fi
}

echo "[init-aws] Inscrevendo filas no topic ${TOPIC_NAME}..."
subscribe_queue_to_topic todo-created-queue '{"action":["CREATED"]}'
subscribe_queue_to_topic todo-updated-queue '{"action":["UPDATED"]}'
subscribe_queue_to_topic todo-deleted-queue '{"action":["DELETED"]}'
subscribe_queue_to_topic todo-audit-queue   ''

echo "[init-aws] Topics SNS:"
awslocal sns list-topics

echo "[init-aws] Filas SQS:"
awslocal sqs list-queues

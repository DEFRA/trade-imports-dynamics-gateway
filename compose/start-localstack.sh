#!/usr/bin/env bash
# LocalStack init script — creates the SNS→SQS notification pipeline for local and IT use.
# Runs once inside the LocalStack container after it is ready.
set -euo pipefail

DLQ_NAME="trade_imports_animals_eu_notifications_dlq.fifo"
QUEUE_NAME="trade_imports_animals_eu_notifications_gateway.fifo"
TOPIC_NAME="trade_imports_animals_eu_notifications.fifo"
REGION="eu-west-2"
ACCOUNT="000000000000"

echo "Creating DLQ: ${DLQ_NAME}"
awslocal sqs create-queue \
  --queue-name "${DLQ_NAME}" \
  --attributes FifoQueue=true,ContentBasedDeduplication=true \
  --region "${REGION}"

DLQ_ARN="arn:aws:sqs:${REGION}:${ACCOUNT}:${DLQ_NAME}"

REDRIVE_POLICY=$(printf '{"deadLetterTargetArn":"%s","maxReceiveCount":"3"}' "${DLQ_ARN}")

echo "Creating FIFO queue: ${QUEUE_NAME}"
awslocal sqs create-queue \
  --queue-name "${QUEUE_NAME}" \
  --attributes "FifoQueue=true,ContentBasedDeduplication=true,RedrivePolicy=${REDRIVE_POLICY}" \
  --region "${REGION}"

QUEUE_ARN="arn:aws:sqs:${REGION}:${ACCOUNT}:${QUEUE_NAME}"

echo "Creating SNS FIFO topic: ${TOPIC_NAME}"
awslocal sns create-topic \
  --name "${TOPIC_NAME}" \
  --attributes FifoTopic=true,ContentBasedDeduplication=true \
  --region "${REGION}"

TOPIC_ARN="arn:aws:sns:${REGION}:${ACCOUNT}:${TOPIC_NAME}"

echo "Subscribing queue to topic with raw message delivery"
awslocal sns subscribe \
  --topic-arn "${TOPIC_ARN}" \
  --protocol sqs \
  --notification-endpoint "${QUEUE_ARN}" \
  --attributes RawMessageDelivery=true \
  --region "${REGION}"

echo "LocalStack notification pipeline ready"

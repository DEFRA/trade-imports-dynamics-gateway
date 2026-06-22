#!/usr/bin/env bash
# Floci init script — creates the SNS→SQS notification pipeline for local and IT use.
# Staged by the workspace stack as setup-notification-pipeline.sh and run from the
# amazon/aws-cli floci-init sidecar (see backend compose/start-floci.sh for the pattern).
set -euo pipefail

ENDPOINT="${FLOCI_URL:-http://localhost:4566}"
export AWS_REGION="${AWS_REGION:-eu-west-2}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-$AWS_REGION}"
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"

aws() { command aws --endpoint-url="$ENDPOINT" --region "$AWS_REGION" "$@"; }

DLQ_NAME="trade_imports_animals_eu_notifications_gateway-deadletter.fifo"
QUEUE_NAME="trade_imports_animals_eu_notifications_gateway.fifo"
TOPIC_NAME="trade_imports_animals_eu_notifications.fifo"
ACCOUNT="000000000000"

# ContentBasedDeduplication=false matches the CDP platform default and real dev
# (verified: ...notifications_gateway.fifo has it false). With it off, producers must
# supply a MessageDeduplicationId per send — here the SNS topic (content-based) provides
# it on delivery to the subscribed FIFO queue, keeping local like-for-like with dev.
echo "Creating DLQ: ${DLQ_NAME}"
aws sqs create-queue \
  --queue-name "${DLQ_NAME}" \
  --attributes FifoQueue=true,ContentBasedDeduplication=false || true

DLQ_ARN="arn:aws:sqs:${AWS_REGION}:${ACCOUNT}:${DLQ_NAME}"

echo "Creating FIFO queue: ${QUEUE_NAME}"
aws sqs create-queue \
  --queue-name "${QUEUE_NAME}" \
  --attributes FifoQueue=true,ContentBasedDeduplication=false || true

echo "Applying RedrivePolicy to ${QUEUE_NAME}"
aws sqs set-queue-attributes \
  --queue-url "${ENDPOINT}/${ACCOUNT}/${QUEUE_NAME}" \
  --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"

QUEUE_ARN="arn:aws:sqs:${AWS_REGION}:${ACCOUNT}:${QUEUE_NAME}"

echo "Creating SNS FIFO topic: ${TOPIC_NAME}"
aws sns create-topic \
  --name "${TOPIC_NAME}" \
  --attributes FifoTopic=true,ContentBasedDeduplication=false || true

TOPIC_ARN="arn:aws:sns:${AWS_REGION}:${ACCOUNT}:${TOPIC_NAME}"

echo "Subscribing queue to topic with raw message delivery"
aws sns subscribe \
  --topic-arn "${TOPIC_ARN}" \
  --protocol sqs \
  --notification-endpoint "${QUEUE_ARN}" \
  --attributes RawMessageDelivery=true

echo "Floci notification pipeline ready"

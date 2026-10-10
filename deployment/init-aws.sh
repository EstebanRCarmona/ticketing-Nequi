#!/bin/bash
echo "=== Initializing LocalStack AWS Resources (5 Multi-Tables & SQS Queues) ==="

# 1. ticketing-events table with GSI gsi-events-by-date
awslocal dynamodb create-table \
    --table-name ticketing-events \
    --attribute-definitions \
        AttributeName=eventId,AttributeType=S \
        AttributeName=listingKey,AttributeType=S \
        AttributeName=eventDate,AttributeType=S \
    --key-schema AttributeName=eventId,KeyType=HASH \
    --global-secondary-indexes '[
        {
            "IndexName": "gsi-events-by-date",
            "KeySchema": [
                {"AttributeName": "listingKey", "KeyType": "HASH"},
                {"AttributeName": "eventDate", "KeyType": "RANGE"}
            ],
            "Projection": {"ProjectionType": "ALL"}
        }
    ]' \
    --billing-mode PAY_PER_REQUEST

# 2. ticketing-inventory table
awslocal dynamodb create-table \
    --table-name ticketing-inventory \
    --attribute-definitions AttributeName=eventId,AttributeType=S \
    --key-schema AttributeName=eventId,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST

# 3. ticketing-orders table with GSI gsi-orders-deadline
awslocal dynamodb create-table \
    --table-name ticketing-orders \
    --attribute-definitions \
        AttributeName=orderId,AttributeType=S \
        AttributeName=expiryShard,AttributeType=S \
        AttributeName=deadlineAt,AttributeType=N \
    --key-schema AttributeName=orderId,KeyType=HASH \
    --global-secondary-indexes '[
        {
            "IndexName": "gsi-orders-deadline",
            "KeySchema": [
                {"AttributeName": "expiryShard", "KeyType": "HASH"},
                {"AttributeName": "deadlineAt", "KeyType": "RANGE"}
            ],
            "Projection": {"ProjectionType": "KEYS_ONLY"}
        }
    ]' \
    --billing-mode PAY_PER_REQUEST

# 4. ticketing-order-history table (composite key orderId + sequence)
awslocal dynamodb create-table \
    --table-name ticketing-order-history \
    --attribute-definitions \
        AttributeName=orderId,AttributeType=S \
        AttributeName=sequence,AttributeType=N \
    --key-schema \
        AttributeName=orderId,KeyType=HASH \
        AttributeName=sequence,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST

# 5. ticketing-idempotency table
awslocal dynamodb create-table \
    --table-name ticketing-idempotency \
    --attribute-definitions AttributeName=idempotencyKey,AttributeType=S \
    --key-schema AttributeName=idempotencyKey,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST

# 6. SQS Queues and DLQs
awslocal sqs create-queue --queue-name order-processing-dlq
awslocal sqs create-queue --queue-name order-expiration-dlq

PROCESSING_DLQ_ARN=$(awslocal sqs get-queue-attributes \
    --queue-url http://localhost:4566/000000000000/order-processing-dlq \
    --attribute-names QueueArn \
    --query 'Attributes.QueueArn' --output text)

EXPIRATION_DLQ_ARN=$(awslocal sqs get-queue-attributes \
    --queue-url http://localhost:4566/000000000000/order-expiration-dlq \
    --attribute-names QueueArn \
    --query 'Attributes.QueueArn' --output text)

awslocal sqs create-queue \
    --queue-name order-processing-queue \
    --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$PROCESSING_DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\",\"VisibilityTimeout\":\"30\"}"

awslocal sqs create-queue \
    --queue-name order-expiration-queue \
    --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$EXPIRATION_DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\",\"VisibilityTimeout\":\"30\"}"

echo "=== LocalStack AWS Resources Initialization Completed ==="

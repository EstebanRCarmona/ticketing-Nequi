terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# ── DLQ: ORDER PROCESSING ────────────────────────────────────────────────────
resource "aws_sqs_queue" "order_processing_dlq" {
  name                      = "${local.prefix}-order-processing-dlq"
  message_retention_seconds = 1209600 # 14 días
  tags                      = local.common_tags
}

# ── QUEUE: ORDER PROCESSING ──────────────────────────────────────────────────
resource "aws_sqs_queue" "order_processing" {
  name                       = "${local.prefix}-order-processing-queue"
  visibility_timeout_seconds = var.visibility_timeout_seconds
  message_retention_seconds  = var.message_retention_seconds

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.order_processing_dlq.arn
    maxReceiveCount     = var.max_receive_count
  })

  tags = local.common_tags
}

# ── DLQ: ORDER EXPIRATION ────────────────────────────────────────────────────
resource "aws_sqs_queue" "order_expiration_dlq" {
  name                      = "${local.prefix}-order-expiration-dlq"
  message_retention_seconds = 1209600
  tags                      = local.common_tags
}

# ── QUEUE: ORDER EXPIRATION ──────────────────────────────────────────────────
resource "aws_sqs_queue" "order_expiration" {
  name                       = "${local.prefix}-order-expiration-queue"
  visibility_timeout_seconds = var.visibility_timeout_seconds
  message_retention_seconds  = var.message_retention_seconds

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.order_expiration_dlq.arn
    maxReceiveCount     = var.max_receive_count
  })

  tags = local.common_tags
}

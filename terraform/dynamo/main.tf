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

# ── EVENTS ──────────────────────────────────────────────────────────────────
resource "aws_dynamodb_table" "events" {
  name         = "${local.prefix}-events"
  billing_mode = var.billing_mode
  hash_key     = "eventId"

  attribute {
    name = "eventId"
    type = "S"
  }

  attribute {
    name = "listingKey"
    type = "S"
  }

  attribute {
    name = "createdAt"
    type = "S"
  }

  global_secondary_index {
    name            = "gsi-events-listing"
    hash_key        = "listingKey"
    range_key       = "createdAt"
    projection_type = "ALL"
  }

  tags = local.common_tags
}

# ── INVENTORY ────────────────────────────────────────────────────────────────
resource "aws_dynamodb_table" "inventory" {
  name         = "${local.prefix}-inventory"
  billing_mode = var.billing_mode
  hash_key     = "eventId"

  attribute {
    name = "eventId"
    type = "S"
  }

  tags = local.common_tags
}

# ── ORDERS ───────────────────────────────────────────────────────────────────
resource "aws_dynamodb_table" "orders" {
  name         = "${local.prefix}-orders"
  billing_mode = var.billing_mode
  hash_key     = "orderId"

  attribute {
    name = "orderId"
    type = "S"
  }

  attribute {
    name = "expiryShard"
    type = "S"
  }

  attribute {
    name = "deadlineAt"
    type = "N"
  }

  global_secondary_index {
    name            = "gsi-orders-deadline"
    hash_key        = "expiryShard"
    range_key       = "deadlineAt"
    projection_type = "INCLUDE"
    non_key_attributes = [
      "orderId", "status", "version", "expiresAt"
    ]
  }

  tags = local.common_tags
}

# ── ORDER HISTORY ─────────────────────────────────────────────────────────────
resource "aws_dynamodb_table" "order_history" {
  name         = "${local.prefix}-order-history"
  billing_mode = var.billing_mode
  hash_key     = "orderId"
  range_key    = "sequence"

  attribute {
    name = "orderId"
    type = "S"
  }

  attribute {
    name = "sequence"
    type = "N"
  }

  tags = local.common_tags
}

# ── IDEMPOTENCY ───────────────────────────────────────────────────────────────
resource "aws_dynamodb_table" "idempotency" {
  name         = "${local.prefix}-idempotency"
  billing_mode = var.billing_mode
  hash_key     = "idempotencyKey"

  attribute {
    name = "idempotencyKey"
    type = "S"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  tags = local.common_tags
}

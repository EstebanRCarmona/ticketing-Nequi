locals {
  app_name           = "app-ticketing-nequi"
  prefix             = "ticketing-${var.env}"
  vpc_id             = var.create_vpc ? aws_vpc.local[0].id : var.vpc_id
  vpc_cidr           = var.create_vpc ? aws_vpc.local[0].cidr_block : data.aws_vpc.selected[0].cidr_block
  private_subnet_ids = var.create_vpc ? aws_subnet.local_private[*].id : var.private_subnet_ids

  common_tags = merge(var.tags, {
    Environment = var.env
    Project     = local.app_name
    ManagedBy   = "terraform"
  })

  ecr_image_uri = "${var.aws_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/${local.prefix}-ecr:${var.ecr_image_tag}"

  container_environment = [
    { name = "SERVER_PORT", value = tostring(var.container_port) },
    { name = "AWS_REGION", value = var.aws_region },
    { name = "AWS_DYNAMODB_ENDPOINT", value = "https://dynamodb.${var.aws_region}.amazonaws.com" },
    { name = "AWS_SQS_ENDPOINT", value = "https://sqs.${var.aws_region}.amazonaws.com" },
    { name = "TABLE_EVENTS", value = var.table_events_name },
    { name = "TABLE_INVENTORY", value = var.table_inventory_name },
    { name = "TABLE_SEATS", value = var.table_seats_name },
    { name = "TABLE_ORDERS", value = var.table_orders_name },
    { name = "TABLE_ORDER_HISTORY", value = var.table_order_history_name },
    { name = "TABLE_IDEMPOTENCY", value = var.table_idempotency_name },
    { name = "AWS_SQS_PROCESSING_QUEUE_URL", value = var.sqs_processing_queue_url },
    { name = "AWS_SQS_EXPIRATION_QUEUE_URL", value = var.sqs_expiration_queue_url },
    { name = "AWS_SQS_POLL_INTERVAL_MS", value = tostring(var.sqs_poll_interval_ms) },
    { name = "EXPIRED_RESERVATIONS_INTERVAL_MS", value = tostring(var.expired_reservations_interval_ms) },
  ]

  task_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:Query",
          "dynamodb:BatchWriteItem",
          "dynamodb:TransactWriteItems"
        ]
        Resource = [
          var.table_events_arn,
          var.table_inventory_arn,
          var.table_seats_arn,
          "${var.table_seats_arn}/index/*",
          var.table_orders_arn,
          "${var.table_orders_arn}/index/*",
          var.table_order_history_arn,
          var.table_idempotency_arn
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "sqs:SendMessage",
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes"
        ]
        Resource = [
          var.sqs_processing_queue_arn,
          var.sqs_expiration_queue_arn
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:${var.aws_region}:${var.aws_account_id}:log-group:/ecs/${local.prefix}:*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken",
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage"
        ]
        Resource = "*"
      }
    ]
  })
}

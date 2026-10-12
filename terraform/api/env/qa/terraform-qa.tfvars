env            = "qa"
aws_region     = "us-east-1"
aws_account_id = "000000000000"
floci_endpoint = "http://localhost:4566"

create_vpc                 = true
local_vpc_cidr             = "10.41.0.0/16"
local_private_subnet_cidrs = ["10.41.1.0/24", "10.41.2.0/24"]
private_subnet_ids         = []
public_subnet_ids          = []

force_delete_ecr = false
ecr_image_tag    = "latest"
desired_count    = 1
min_capacity     = 1
max_capacity     = 2
waf_rate_limit   = 1000

table_events_name        = "ticketing-qa-events"
table_inventory_name     = "ticketing-qa-inventory"
table_seats_name         = "ticketing-qa-seats"
table_orders_name        = "ticketing-qa-orders"
table_order_history_name = "ticketing-qa-order-history"
table_idempotency_name   = "ticketing-qa-idempotency"
table_events_arn         = "arn:aws:dynamodb:us-east-1:000000000000:table/ticketing-qa-events"
table_inventory_arn      = "arn:aws:dynamodb:us-east-1:000000000000:table/ticketing-qa-inventory"
table_seats_arn          = "arn:aws:dynamodb:us-east-1:000000000000:table/ticketing-qa-seats"
table_orders_arn         = "arn:aws:dynamodb:us-east-1:000000000000:table/ticketing-qa-orders"
table_order_history_arn  = "arn:aws:dynamodb:us-east-1:000000000000:table/ticketing-qa-order-history"
table_idempotency_arn    = "arn:aws:dynamodb:us-east-1:000000000000:table/ticketing-qa-idempotency"
sqs_processing_queue_url = "http://localhost:4566/000000000000/ticketing-qa-order-processing-queue"
sqs_expiration_queue_url = "http://localhost:4566/000000000000/ticketing-qa-order-expiration-queue"
sqs_processing_queue_arn = "arn:aws:sqs:us-east-1:000000000000:ticketing-qa-order-processing-queue"
sqs_expiration_queue_arn = "arn:aws:sqs:us-east-1:000000000000:ticketing-qa-order-expiration-queue"

env            = "dev"
aws_region     = "us-east-1"
aws_account_id = "000000000000"
floci_endpoint = "http://localhost:4566"

create_vpc                 = true
local_vpc_cidr             = "10.40.0.0/16"
local_private_subnet_cidrs = ["10.40.1.0/24", "10.40.2.0/24"]
private_subnet_ids         = []
public_subnet_ids          = []

force_delete_ecr = true
ecr_image_tag    = "latest"
desired_count    = 1
min_capacity     = 1
max_capacity     = 2
waf_rate_limit   = 1000

# Names/ARNs align with the outputs of terraform/dynamo and terraform/sqs.
table_events_name        = "ticketing-dev-events"
table_inventory_name     = "ticketing-dev-inventory"
table_seats_name         = "ticketing-dev-seats"
table_orders_name        = "ticketing-dev-orders"
table_order_history_name = "ticketing-dev-order-history"
table_idempotency_name   = "ticketing-dev-idempotency"
table_events_arn         = "arn:aws:dynamodb:us-east-1:000000000000:table/ticketing-dev-events"
table_inventory_arn      = "arn:aws:dynamodb:us-east-1:000000000000:table/ticketing-dev-inventory"
table_seats_arn          = "arn:aws:dynamodb:us-east-1:000000000000:table/ticketing-dev-seats"
table_orders_arn         = "arn:aws:dynamodb:us-east-1:000000000000:table/ticketing-dev-orders"
table_order_history_arn  = "arn:aws:dynamodb:us-east-1:000000000000:table/ticketing-dev-order-history"
table_idempotency_arn    = "arn:aws:dynamodb:us-east-1:000000000000:table/ticketing-dev-idempotency"
sqs_processing_queue_url = "http://localhost:4566/000000000000/ticketing-dev-order-processing-queue"
sqs_expiration_queue_url = "http://localhost:4566/000000000000/ticketing-dev-order-expiration-queue"
sqs_processing_queue_arn = "arn:aws:sqs:us-east-1:000000000000:ticketing-dev-order-processing-queue"
sqs_expiration_queue_arn = "arn:aws:sqs:us-east-1:000000000000:ticketing-dev-order-expiration-queue"

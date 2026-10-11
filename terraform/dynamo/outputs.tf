output "events_table_name" {
  value = aws_dynamodb_table.events.name
}

output "inventory_table_name" {
  value = aws_dynamodb_table.inventory.name
}

output "orders_table_name" {
  value = aws_dynamodb_table.orders.name
}

output "order_history_table_name" {
  value = aws_dynamodb_table.order_history.name
}

output "idempotency_table_name" {
  value = aws_dynamodb_table.idempotency.name
}

output "orders_table_arn" {
  value = aws_dynamodb_table.orders.arn
}

output "inventory_table_arn" {
  value = aws_dynamodb_table.inventory.arn
}

output "order_history_table_arn" {
  value = aws_dynamodb_table.order_history.arn
}

output "idempotency_table_arn" {
  value = aws_dynamodb_table.idempotency.arn
}

output "events_table_arn" {
  value = aws_dynamodb_table.events.arn
}

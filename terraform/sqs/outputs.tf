output "order_processing_queue_url" {
  value = aws_sqs_queue.order_processing.url
}

output "order_processing_queue_arn" {
  value = aws_sqs_queue.order_processing.arn
}

output "order_expiration_queue_url" {
  value = aws_sqs_queue.order_expiration.url
}

output "order_expiration_queue_arn" {
  value = aws_sqs_queue.order_expiration.arn
}

output "order_processing_dlq_arn" {
  value = aws_sqs_queue.order_processing_dlq.arn
}

output "order_expiration_dlq_arn" {
  value = aws_sqs_queue.order_expiration_dlq.arn
}

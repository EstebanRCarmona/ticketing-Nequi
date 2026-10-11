output "ecr_repository_url" {
  description = "URL del repositorio ECR"
  value       = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  description = "Nombre del cluster ECS"
  value       = aws_ecs_cluster.app.name
}

output "ecs_service_name" {
  description = "Nombre del servicio ECS"
  value       = aws_ecs_service.app.name
}

output "alb_dns_name" {
  description = "DNS del Application Load Balancer"
  value       = aws_lb.app.dns_name
}

output "api_gateway_invoke_url" {
  description = "URL de invocación del API Gateway"
  value       = "${aws_api_gateway_stage.app.invoke_url}/api/v1"
}

output "api_gateway_id" {
  description = "ID del API Gateway"
  value       = aws_api_gateway_rest_api.app.id
}

output "cloudwatch_log_group" {
  description = "Nombre del log group de CloudWatch"
  value       = aws_cloudwatch_log_group.app.name
}

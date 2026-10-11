variable "env" {
  description = "Ambiente de despliegue: dev, qa, pdn"
  type        = string
  validation {
    condition     = contains(["dev", "qa", "pdn"], var.env)
    error_message = "Solo se permite: dev, qa, pdn."
  }
}

variable "aws_region" {
  description = "Región AWS"
  type        = string
  default     = "us-east-1"
}

variable "aws_account_id" {
  description = "ID de la cuenta AWS"
  type        = string
}

# ── VPC / RED ────────────────────────────────────────────────────────────────
variable "vpc_id" {
  description = "ID del VPC donde se desplegará el servicio"
  type        = string
}

variable "private_subnet_ids" {
  description = "IDs de subnets privadas para ECS tasks"
  type        = list(string)
}

variable "public_subnet_ids" {
  description = "IDs de subnets públicas para el ALB"
  type        = list(string)
}

# ── ECR ──────────────────────────────────────────────────────────────────────
variable "ecr_image_tag" {
  description = "Tag de la imagen Docker a desplegar"
  type        = string
  default     = "latest"
}

variable "force_delete_ecr" {
  description = "Eliminar el repositorio ECR aunque tenga imágenes"
  type        = bool
  default     = false
}

# ── ECS ──────────────────────────────────────────────────────────────────────
variable "container_port" {
  description = "Puerto en el que escucha el contenedor"
  type        = number
  default     = 8080
}

variable "container_cpu" {
  description = "CPU asignada al task (unidades de CPU de Fargate)"
  type        = number
  default     = 512
}

variable "container_memory" {
  description = "Memoria asignada al task en MB"
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "Número de tasks deseadas"
  type        = number
  default     = 1
}

variable "min_capacity" {
  description = "Mínimo de tasks para auto scaling"
  type        = number
  default     = 1
}

variable "max_capacity" {
  description = "Máximo de tasks para auto scaling"
  type        = number
  default     = 3
}

variable "cpu_scale_up_threshold" {
  description = "% de CPU para escalar hacia arriba"
  type        = number
  default     = 60
}

variable "memory_scale_up_threshold" {
  description = "% de memoria para escalar hacia arriba"
  type        = number
  default     = 70
}

variable "alb_request_count_target" {
  description = "Número objetivo de requests por target en el ALB para auto scaling dinámico"
  type        = number
  default     = 1000
}

variable "health_check_path" {
  description = "Path del health check del contenedor"
  type        = string
  default     = "/actuator/health"
}

# ── DYNAMO / SQS (referencias de otros módulos) ───────────────────────────────
variable "table_events_name" {
  description = "Nombre de la tabla DynamoDB events"
  type        = string
}

variable "table_inventory_name" {
  description = "Nombre de la tabla DynamoDB inventory"
  type        = string
}

variable "table_orders_name" {
  description = "Nombre de la tabla DynamoDB orders"
  type        = string
}

variable "table_order_history_name" {
  description = "Nombre de la tabla DynamoDB order-history"
  type        = string
}

variable "table_idempotency_name" {
  description = "Nombre de la tabla DynamoDB idempotency"
  type        = string
}

variable "table_orders_arn" {
  description = "ARN de la tabla DynamoDB orders"
  type        = string
}

variable "table_inventory_arn" {
  description = "ARN de la tabla DynamoDB inventory"
  type        = string
}

variable "table_order_history_arn" {
  description = "ARN de la tabla DynamoDB order-history"
  type        = string
}

variable "table_idempotency_arn" {
  description = "ARN de la tabla DynamoDB idempotency"
  type        = string
}

variable "table_events_arn" {
  description = "ARN de la tabla DynamoDB events"
  type        = string
}

variable "sqs_processing_queue_url" {
  description = "URL de la cola SQS de procesamiento"
  type        = string
}

variable "sqs_expiration_queue_url" {
  description = "URL de la cola SQS de expiración"
  type        = string
}

variable "sqs_processing_queue_arn" {
  description = "ARN de la cola SQS de procesamiento"
  type        = string
}

variable "sqs_expiration_queue_arn" {
  description = "ARN de la cola SQS de expiración"
  type        = string
}

# ── APP CONFIG ────────────────────────────────────────────────────────────────
variable "sqs_poll_interval_ms" {
  description = "Intervalo de polling SQS en ms"
  type        = number
  default     = 5000
}

variable "expired_reservations_interval_ms" {
  description = "Intervalo del scheduler de expiración en ms"
  type        = number
  default     = 60000
}

variable "tags" {
  description = "Tags adicionales para los recursos"
  type        = map(string)
  default     = {}
}

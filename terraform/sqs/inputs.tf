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

variable "message_retention_seconds" {
  description = "Tiempo de retención de mensajes en segundos"
  type        = number
  default     = 86400
}

variable "visibility_timeout_seconds" {
  description = "Tiempo de visibilidad del mensaje en segundos"
  type        = number
  default     = 30
}

variable "max_receive_count" {
  description = "Número máximo de recepciones antes de enviar a DLQ"
  type        = number
  default     = 3
}

variable "tags" {
  description = "Tags adicionales para los recursos"
  type        = map(string)
  default     = {}
}

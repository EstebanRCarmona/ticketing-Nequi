locals {
  prefix = "ticketing-${var.env}"

  common_tags = merge(var.tags, {
    Environment = var.env
    Project     = "app-ticketing-nequi"
    ManagedBy   = "terraform"
  })
}

data "aws_vpc" "selected" {
  count = var.create_vpc ? 0 : 1
  id    = var.vpc_id
}

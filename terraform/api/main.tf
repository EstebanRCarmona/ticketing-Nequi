terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# ── ECR ───────────────────────────────────────────────────────────────────────
resource "aws_ecr_repository" "app" {
  name                 = "${local.prefix}-ecr"
  image_tag_mutability = "MUTABLE"
  force_delete         = var.force_delete_ecr

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = local.common_tags
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Mantener solo las últimas 10 imágenes"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

# ── IAM ───────────────────────────────────────────────────────────────────────
resource "aws_iam_role" "ecs_task_execution" {
  name = "${local.prefix}-ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "ecs_task" {
  name = "${local.prefix}-ecs-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy" "ecs_task" {
  name   = "${local.prefix}-ecs-task-policy"
  role   = aws_iam_role.ecs_task.id
  policy = local.task_policy
}

# ── CLOUDWATCH LOGS ───────────────────────────────────────────────────────────
resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${local.prefix}"
  retention_in_days = 30
  tags              = local.common_tags
}

# ── SECURITY GROUPS ───────────────────────────────────────────────────────────
resource "aws_security_group" "alb" {
  name        = "${local.prefix}-alb-sg"
  description = "Security group para el ALB interno"
  vpc_id      = var.vpc_id

  ingress {
    description = "Allow HTTP traffic from VPC (API Gateway VPC Link)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = [data.aws_vpc.selected.cidr_block]
  }

  ingress {
    description = "Allow HTTPS traffic from VPC"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [data.aws_vpc.selected.cidr_block]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.common_tags
}

resource "aws_security_group" "ecs" {
  name        = "${local.prefix}-ecs-sg"
  description = "Security group para las tasks ECS"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.common_tags
}

# ── ALB ───────────────────────────────────────────────────────────────────────
resource "aws_lb" "app" {
  name               = "${local.prefix}-alb"
  internal           = true
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.private_subnet_ids

  tags = local.common_tags
}

resource "aws_lb_target_group" "app" {
  name        = "${local.prefix}-tg"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = var.health_check_path
    protocol            = "HTTP"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = local.common_tags
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

# ── ECS CLUSTER ───────────────────────────────────────────────────────────────
resource "aws_ecs_cluster" "app" {
  name = "${local.prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = local.common_tags
}

# ── ECS TASK DEFINITION ───────────────────────────────────────────────────────
resource "aws_ecs_task_definition" "app" {
  family                   = "${local.prefix}-task"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.container_cpu
  memory                   = var.container_memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name      = local.app_name
    image     = local.ecr_image_uri
    essential = true

    portMappings = [{
      containerPort = var.container_port
      protocol      = "tcp"
    }]

    environment = local.container_environment

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.app.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "curl -f http://localhost:${var.container_port}${var.health_check_path} || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
  }])

  tags = local.common_tags
}

# ── ECS SERVICE ───────────────────────────────────────────────────────────────
resource "aws_ecs_service" "app" {
  name            = "${local.prefix}-service"
  cluster         = aws_ecs_cluster.app.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = local.app_name
    container_port   = var.container_port
  }

  depends_on = [aws_lb_listener.http]

  tags = local.common_tags
}

# ── AUTO SCALING ──────────────────────────────────────────────────────────────
resource "aws_appautoscaling_target" "ecs" {
  max_capacity       = var.max_capacity
  min_capacity       = var.min_capacity
  resource_id        = "service/${aws_ecs_cluster.app.name}/${aws_ecs_service.app.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "cpu_scale_up" {
  name               = "${local.prefix}-cpu-scale-up"
  policy_type        = "StepScaling"
  resource_id        = aws_appautoscaling_target.ecs.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs.scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs.service_namespace

  step_scaling_policy_configuration {
    adjustment_type         = "ChangeInCapacity"
    cooldown                = 60
    metric_aggregation_type = "Average"

    step_adjustment {
      metric_interval_lower_bound = 0
      scaling_adjustment          = 1
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "cpu_high" {
  alarm_name          = "${local.prefix}-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = var.cpu_scale_up_threshold

  dimensions = {
    ClusterName = aws_ecs_cluster.app.name
    ServiceName = aws_ecs_service.app.name
  }

  alarm_actions = [aws_appautoscaling_policy.cpu_scale_up.arn]
}

resource "aws_appautoscaling_policy" "memory_scale_up" {
  name               = "${local.prefix}-memory-scale-up"
  policy_type        = "StepScaling"
  resource_id        = aws_appautoscaling_target.ecs.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs.scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs.service_namespace

  step_scaling_policy_configuration {
    adjustment_type         = "ChangeInCapacity"
    cooldown                = 60
    metric_aggregation_type = "Average"

    step_adjustment {
      metric_interval_lower_bound = 0
      scaling_adjustment          = 1
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "memory_high" {
  alarm_name          = "${local.prefix}-memory-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "MemoryUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = var.memory_scale_up_threshold

  dimensions = {
    ClusterName = aws_ecs_cluster.app.name
    ServiceName = aws_ecs_service.app.name
  }

  alarm_actions = [aws_appautoscaling_policy.memory_scale_up.arn]
}

resource "aws_appautoscaling_policy" "alb_request_count" {
  name               = "${local.prefix}-alb-requests-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs.scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ALBRequestCountPerTarget"
      resource_label         = "${aws_lb.app.arn_suffix}/${aws_lb_target_group.app.arn_suffix}"
    }

    target_value       = var.alb_request_count_target
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
  }
}

# ── API GATEWAY ───────────────────────────────────────────────────────────────
resource "aws_api_gateway_rest_api" "app" {
  name        = "${local.prefix}-api"
  description = "API Gateway para App Ticketing Nequi - ${var.env}"

  endpoint_configuration {
    types = ["REGIONAL"]
  }

  tags = local.common_tags
}

# VPC Link para conectar API GW con el ALB interno
resource "aws_api_gateway_vpc_link" "app" {
  name        = "${local.prefix}-vpc-link"
  target_arns = [aws_lb.app.arn]
  tags        = local.common_tags
}

# /api
resource "aws_api_gateway_resource" "api" {
  rest_api_id = aws_api_gateway_rest_api.app.id
  parent_id   = aws_api_gateway_rest_api.app.root_resource_id
  path_part   = "api"
}

# /api/v1
resource "aws_api_gateway_resource" "v1" {
  rest_api_id = aws_api_gateway_rest_api.app.id
  parent_id   = aws_api_gateway_resource.api.id
  path_part   = "v1"
}

# /api/v1/events
resource "aws_api_gateway_resource" "events" {
  rest_api_id = aws_api_gateway_rest_api.app.id
  parent_id   = aws_api_gateway_resource.v1.id
  path_part   = "events"
}

# /api/v1/events/{eventId}
resource "aws_api_gateway_resource" "event_id" {
  rest_api_id = aws_api_gateway_rest_api.app.id
  parent_id   = aws_api_gateway_resource.events.id
  path_part   = "{eventId}"
}

# /api/v1/events/{eventId}/complimentary
resource "aws_api_gateway_resource" "complimentary" {
  rest_api_id = aws_api_gateway_rest_api.app.id
  parent_id   = aws_api_gateway_resource.event_id.id
  path_part   = "complimentary"
}

# /api/v1/orders
resource "aws_api_gateway_resource" "orders" {
  rest_api_id = aws_api_gateway_rest_api.app.id
  parent_id   = aws_api_gateway_resource.v1.id
  path_part   = "orders"
}

# /api/v1/orders/{orderId}
resource "aws_api_gateway_resource" "order_id" {
  rest_api_id = aws_api_gateway_rest_api.app.id
  parent_id   = aws_api_gateway_resource.orders.id
  path_part   = "{orderId}"
}

# /api/v1/orders/{orderId}/confirm
resource "aws_api_gateway_resource" "confirm" {
  rest_api_id = aws_api_gateway_rest_api.app.id
  parent_id   = aws_api_gateway_resource.order_id.id
  path_part   = "confirm"
}

# /api/v1/orders/{orderId}/cancel
resource "aws_api_gateway_resource" "cancel" {
  rest_api_id = aws_api_gateway_rest_api.app.id
  parent_id   = aws_api_gateway_resource.order_id.id
  path_part   = "cancel"
}

# /api/v1/orders/{orderId}/history
resource "aws_api_gateway_resource" "history" {
  rest_api_id = aws_api_gateway_rest_api.app.id
  parent_id   = aws_api_gateway_resource.order_id.id
  path_part   = "history"
}

locals {
  alb_base_uri = "http://${aws_lb.app.dns_name}"

  # Mapa de todos los métodos: resource_id -> [http_method, integration_uri]
  api_methods = {
    post_events        = { resource = aws_api_gateway_resource.events,        method = "POST", uri = "${local.alb_base_uri}/api/v1/events" }
    get_events         = { resource = aws_api_gateway_resource.events,        method = "GET",  uri = "${local.alb_base_uri}/api/v1/events" }
    get_event_by_id    = { resource = aws_api_gateway_resource.event_id,      method = "GET",  uri = "${local.alb_base_uri}/api/v1/events/{eventId}" }
    post_complimentary = { resource = aws_api_gateway_resource.complimentary, method = "POST", uri = "${local.alb_base_uri}/api/v1/events/{eventId}/complimentary" }
    post_orders        = { resource = aws_api_gateway_resource.orders,        method = "POST", uri = "${local.alb_base_uri}/api/v1/orders" }
    get_order_by_id    = { resource = aws_api_gateway_resource.order_id,      method = "GET",  uri = "${local.alb_base_uri}/api/v1/orders/{orderId}" }
    post_confirm       = { resource = aws_api_gateway_resource.confirm,       method = "POST", uri = "${local.alb_base_uri}/api/v1/orders/{orderId}/confirm" }
    post_cancel        = { resource = aws_api_gateway_resource.cancel,        method = "POST", uri = "${local.alb_base_uri}/api/v1/orders/{orderId}/cancel" }
    get_history        = { resource = aws_api_gateway_resource.history,       method = "GET",  uri = "${local.alb_base_uri}/api/v1/orders/{orderId}/history" }
  }
}

resource "aws_api_gateway_method" "methods" {
  for_each = local.api_methods

  rest_api_id   = aws_api_gateway_rest_api.app.id
  resource_id   = each.value.resource.id
  http_method   = each.value.method
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "integrations" {
  for_each = local.api_methods

  rest_api_id             = aws_api_gateway_rest_api.app.id
  resource_id             = each.value.resource.id
  http_method             = aws_api_gateway_method.methods[each.key].http_method
  type                    = "HTTP_PROXY"
  integration_http_method = each.value.method
  uri                     = each.value.uri
  connection_type         = "VPC_LINK"
  connection_id           = aws_api_gateway_vpc_link.app.id
}

resource "aws_api_gateway_deployment" "app" {
  rest_api_id = aws_api_gateway_rest_api.app.id

  depends_on = [aws_api_gateway_integration.integrations]

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_api_gateway_stage" "app" {
  deployment_id = aws_api_gateway_deployment.app.id
  rest_api_id   = aws_api_gateway_rest_api.app.id
  stage_name    = var.env

  tags = local.common_tags
}

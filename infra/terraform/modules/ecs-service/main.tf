resource "aws_ecs_task_definition" "api" {
  family                   = "${var.name_prefix}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = var.execution_role_arn

  container_definitions = jsonencode([
    {
      name      = "api"
      image     = var.initial_image_uri
      essential = true

      portMappings = [
        {
          containerPort = var.app_port
          hostPort      = var.app_port
          protocol      = "tcp"
        }
      ]

      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
        { name = "APP_SCHEDULER_ENABLED", value = "false" },
        { name = "APP_DEMO_SEED_ENABLED", value = "false" },
        { name = "JWT_ACCESS_SECRET", value = var.jwt_access_secret },
        { name = "SPRING_DATASOURCE_URL", value = var.datasource_url },
        { name = "SPRING_DATASOURCE_USERNAME", value = var.datasource_username },
        { name = "SPRING_DATASOURCE_PASSWORD", value = var.datasource_password },
        { name = "SPRING_DATA_REDIS_HOST", value = var.redis_host },
        { name = "SPRING_DATA_REDIS_PORT", value = tostring(var.redis_port) }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = var.api_log_group_name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "api"
        }
      }
    }
  ])

  tags = var.common_tags
}

resource "aws_ecs_service" "api" {
  name                              = "${var.name_prefix}-api-service"
  cluster                           = var.cluster_arn
  task_definition                   = aws_ecs_task_definition.api.arn
  desired_count                     = var.api_desired_count
  launch_type                       = "FARGATE"
  enable_execute_command            = false
  health_check_grace_period_seconds = 60

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.api_security_group_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = var.target_group_arn
    container_name   = "api"
    container_port   = var.app_port
  }

  lifecycle {
    ignore_changes = [task_definition]
  }

  tags = var.common_tags
}

resource "aws_ecs_task_definition" "worker" {
  family                   = "${var.name_prefix}-worker"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = var.execution_role_arn

  container_definitions = jsonencode([
    {
      name      = "worker"
      image     = var.initial_image_uri
      essential = true

      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
        { name = "APP_SCHEDULER_ENABLED", value = "true" },
        { name = "APP_DEMO_SEED_ENABLED", value = "true" },
        { name = "JWT_ACCESS_SECRET", value = var.jwt_access_secret },
        { name = "SPRING_DATASOURCE_URL", value = var.datasource_url },
        { name = "SPRING_DATASOURCE_USERNAME", value = var.datasource_username },
        { name = "SPRING_DATASOURCE_PASSWORD", value = var.datasource_password },
        { name = "SPRING_DATA_REDIS_HOST", value = var.redis_host },
        { name = "SPRING_DATA_REDIS_PORT", value = tostring(var.redis_port) }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = var.worker_log_group_name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "worker"
        }
      }
    }
  ])

  tags = var.common_tags
}

resource "aws_ecs_service" "worker" {
  name                   = "${var.name_prefix}-worker-service"
  cluster                = var.cluster_arn
  task_definition        = aws_ecs_task_definition.worker.arn
  desired_count          = var.worker_desired_count
  launch_type            = "FARGATE"
  enable_execute_command = false

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.worker_security_group_id]
    assign_public_ip = false
  }

  lifecycle {
    ignore_changes = [task_definition]
  }

  tags = var.common_tags
}

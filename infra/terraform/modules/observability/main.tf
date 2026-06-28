resource "aws_cloudwatch_log_group" "api" {
  name              = "/${var.project_name}/${var.environment}/api"
  retention_in_days = 14

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-api-logs"
  })
}

resource "aws_cloudwatch_log_group" "worker" {
  name              = "/${var.project_name}/${var.environment}/worker"
  retention_in_days = 14

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-worker-logs"
  })
}

resource "aws_cloudwatch_log_group" "ecs_exec" {
  name              = "/${var.project_name}/${var.environment}/ecs-exec"
  retention_in_days = 14

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-ecs-exec-logs"
  })
}

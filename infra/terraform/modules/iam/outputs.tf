output "ecs_task_execution_role_arn" {
  description = "ECS task execution role ARN."
  value       = aws_iam_role.ecs_task_execution.arn
}

output "ecs_task_execution_role_name" {
  description = "ECS task execution role name."
  value       = aws_iam_role.ecs_task_execution.name
}

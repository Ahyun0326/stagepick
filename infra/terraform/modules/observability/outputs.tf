output "api_log_group_name" {
  description = "API service CloudWatch log group name."
  value       = aws_cloudwatch_log_group.api.name
}

output "worker_log_group_name" {
  description = "Worker service CloudWatch log group name."
  value       = aws_cloudwatch_log_group.worker.name
}

output "ecs_exec_log_group_name" {
  description = "ECS Exec CloudWatch log group name."
  value       = aws_cloudwatch_log_group.ecs_exec.name
}

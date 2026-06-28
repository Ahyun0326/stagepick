output "api_service_name" {
  description = "ECS API service name."
  value       = aws_ecs_service.api.name
}

output "worker_service_name" {
  description = "ECS worker service name."
  value       = aws_ecs_service.worker.name
}

output "api_task_definition_family" {
  description = "ECS API task definition family."
  value       = aws_ecs_task_definition.api.family
}

output "worker_task_definition_family" {
  description = "ECS worker task definition family."
  value       = aws_ecs_task_definition.worker.family
}

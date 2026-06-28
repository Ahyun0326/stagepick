output "alb_security_group_id" {
  description = "ALB security group ID."
  value       = aws_security_group.alb_sg.id
}

output "ecs_api_security_group_id" {
  description = "ECS API security group ID."
  value       = aws_security_group.ecs_api_sg.id
}

output "ecs_worker_security_group_id" {
  description = "ECS worker security group ID."
  value       = aws_security_group.ecs_worker_sg.id
}

output "vpc_endpoint_security_group_id" {
  description = "VPC endpoint security group ID."
  value       = aws_security_group.vpc_endpoint_sg.id
}

output "rds_security_group_id" {
  description = "RDS security group ID."
  value       = aws_security_group.rds_sg.id
}

output "redis_security_group_id" {
  description = "Redis security group ID."
  value       = aws_security_group.redis_sg.id
}

output "bastion_security_group_id" {
  description = "Bastion security group ID."
  value       = aws_security_group.bastion_sg.id
}

output "ecr_repository_name" {
  description = "ECR repository name."
  value       = module.registry.repository_name
}

output "ecr_repository_url" {
  description = "ECR repository URL."
  value       = module.registry.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = module.ecs_cluster.cluster_name
}

output "ecs_cluster_arn" {
  description = "ECS cluster ARN."
  value       = module.ecs_cluster.cluster_arn
}

output "api_log_group_name" {
  description = "API service CloudWatch log group name."
  value       = module.observability.api_log_group_name
}

output "worker_log_group_name" {
  description = "Worker service CloudWatch log group name."
  value       = module.observability.worker_log_group_name
}

output "vpc_id" {
  description = "VPC ID."
  value       = module.network.vpc_id
}

output "public_subnet_ids" {
  description = "Public subnet IDs."
  value       = module.network.public_subnet_ids
}

output "private_subnet_ids" {
  description = "Private subnet IDs."
  value       = module.network.private_subnet_ids
}

output "private_route_table_id" {
  description = "Private route table ID."
  value       = module.network.private_route_table_id
}

output "alb_security_group_id" {
  value = module.security.alb_security_group_id
}

output "alb_dns_name" {
  description = "ALB DNS name."
  value       = module.load_balancer.alb_dns_name
}

output "alb_arn" {
  description = "ALB ARN."
  value       = module.load_balancer.alb_arn
}

output "api_target_group_arn" {
  description = "API target group ARN."
  value       = module.load_balancer.target_group_arn
}

output "route53_name_servers" {
  description = "Route 53 name servers to register at the domain registrar."
  value       = module.domain.name_servers
}

output "route53_hosted_zone_id" {
  description = "Route 53 hosted zone ID."
  value       = module.domain.hosted_zone_id
}

output "acm_certificate_arn" {
  description = "Validated ACM certificate ARN."
  value       = module.domain.acm_certificate_arn
}

output "api_record_fqdn" {
  description = "API domain alias record FQDN."
  value       = module.dns_record.api_record_fqdn
}

output "api_service_url" {
  description = "API HTTPS service URL."
  value       = "https://${var.api_subdomain}.${var.domain_name}"
}

output "ecs_api_service_name" {
  description = "ECS API service name."
  value       = module.ecs_service.api_service_name
}

output "ecs_worker_service_name" {
  description = "ECS worker service name."
  value       = module.ecs_service.worker_service_name
}

output "ecs_api_task_definition_family" {
  description = "ECS API task definition family."
  value       = module.ecs_service.api_task_definition_family
}

output "ecs_worker_task_definition_family" {
  description = "ECS worker task definition family."
  value       = module.ecs_service.worker_task_definition_family
}

output "bastion_instance_id" {
  description = "Bastion EC2 instance ID."
  value       = module.bastion.instance_id
}

output "bastion_public_ip" {
  description = "Bastion public IP address for SSH tunnel access."
  value       = module.bastion.public_ip
}

output "bastion_public_dns" {
  description = "Bastion public DNS name for SSH tunnel access."
  value       = module.bastion.public_dns
}

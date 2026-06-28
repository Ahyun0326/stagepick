# Variables will be added when load balancer resources are implemented.
variable "alb_security_group_id" {
  description = "Security Group ID attached to the ALB."
  type        = string
}

variable "public_subnet_ids" {
  description = "Public subnet IDs."
  type        = list(string)
}

variable "common_tags" {
  description = "Common tags applied to load balancer resources."
  type        = map(string)
}

variable "name_prefix" {
  description = "Common resource name prefix."
  type        = string
}

variable "vpc_id" {
  description = "VPC ID for the target group."
  type        = string
}

variable "app_port" {
  description = "Application port for ECS tasks."
  type        = number
}

variable "certificate_arn" {
  description = "ACM certificate ARN for HTTPS listener."
  type        = string
}
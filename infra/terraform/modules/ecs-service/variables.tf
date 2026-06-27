variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "aws_region" {
  description = "AWS region where ECS tasks run."
  type        = string
}

variable "cluster_arn" {
  description = "ECS cluster ARN."
  type        = string
}

variable "cluster_name" {
  description = "ECS cluster name."
  type        = string
}

variable "execution_role_arn" {
  description = "ECS task execution role ARN."
  type        = string
}

variable "initial_image_uri" {
  description = "Initial image URI used only for bootstrapping ECS task definitions."
  type        = string
}

variable "app_port" {
  description = "Application container port."
  type        = number
}

variable "task_cpu" {
  description = "Fargate task CPU units."
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "Fargate task memory MiB."
  type        = number
  default     = 1024
}

variable "api_desired_count" {
  description = "Desired task count for API service."
  type        = number
  default     = 1
}

variable "worker_desired_count" {
  description = "Desired task count for worker service."
  type        = number
  default     = 1
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for ECS tasks."
  type        = list(string)
}

variable "api_security_group_id" {
  description = "Security group ID for API tasks."
  type        = string
}

variable "worker_security_group_id" {
  description = "Security group ID for worker tasks."
  type        = string
}

variable "target_group_arn" {
  description = "ALB target group ARN for API service."
  type        = string
}

variable "api_log_group_name" {
  description = "CloudWatch log group name for API service."
  type        = string
}

variable "worker_log_group_name" {
  description = "CloudWatch log group name for worker service."
  type        = string
}

variable "jwt_access_secret" {
  description = "JWT access token signing secret."
  type        = string
  sensitive   = true
}

variable "datasource_url" {
  description = "Spring datasource JDBC URL."
  type        = string
}

variable "datasource_username" {
  description = "Spring datasource username."
  type        = string
}

variable "datasource_password" {
  description = "Spring datasource password."
  type        = string
  sensitive   = true
}

variable "redis_host" {
  description = "Spring Redis host."
  type        = string
}

variable "redis_port" {
  description = "Spring Redis port."
  type        = number
  default     = 6379
}

variable "common_tags" {
  description = "Common tags applied to ECS service resources."
  type        = map(string)
}

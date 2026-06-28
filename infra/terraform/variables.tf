variable "aws_region" {
  description = "AWS region where resources are created."
  type        = string
  default     = "ap-northeast-2"
}

variable "aws_profile" {
  description = "AWS CLI profile used by the provider."
  type        = string
  default     = "ys-admin"
}

variable "project_name" {
  description = "Project name used for resource names and tags."
  type        = string
  default     = "concerts"
}

variable "environment" {
  description = "Deployment environment name."
  type        = string
  default     = "prod"
}

variable "ecr_repository_name" {
  description = "ECR repository name for the backend API image."
  type        = string
  default     = "concerts-api"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets."
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets."
  type        = list(string)
  default     = ["10.0.11.0/24", "10.0.12.0/24"]
}

variable "app_port" {
  description = "Spring Boot application port exposed by ECS tasks."
  type        = number
  default     = 8080
}

variable "domain_name" {
  description = "Root domain name managed by Route 53."
  type        = string
  default     = "stagepick.cloud"
}

variable "api_subdomain" {
  description = "Subdomain used by the backend API."
  type        = string
  default     = "api"
}

variable "initial_image_tag" {
  description = "Initial image tag used only for bootstrapping ECS task definitions."
  type        = string
  default     = "latest"
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

variable "jwt_access_secret" {
  description = "JWT access token signing secret."
  type        = string
  sensitive   = true
}

variable "db_name" {
  description = "Initial database name."
  type        = string
  sensitive   = true
}

variable "db_username" {
  description = "Database master username."
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "Database master password."
  type        = string
  sensitive   = true
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "RDS allocated storage in GB."
  type        = number
  default     = 20
}

variable "redis_node_type" {
  description = "ElastiCache Redis node type."
  type        = string
  default     = "cache.t4g.micro"
}

variable "bastion_allowed_ssh_cidr" {
  description = "Admin public IP CIDR allowed to SSH into the bastion host."
  type        = string
  sensitive   = true
}

variable "bastion_key_name" {
  description = "Existing EC2 key pair name for bastion SSH access."
  type        = string
  sensitive   = true
}

variable "bastion_instance_type" {
  description = "Bastion EC2 instance type."
  type        = string
  default     = "t3.micro"
}

# Variables will be added when datastore resources are implemented.
variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for datastore resources."
  type        = list(string)
}

variable "rds_security_group_id" {
  description = "Security group ID for RDS."
  type        = string
}

variable "redis_security_group_id" {
  description = "Security group ID for Redis."
  type        = string
}

variable "db_name" {
  description = "Initial database name."
  type        = string
}

variable "db_username" {
  description = "Database master username."
  type        = string
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

variable "common_tags" {
  description = "Common tags."
  type        = map(string)
}
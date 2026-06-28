variable "name_prefix" {
  description = "Common resource name prefix."
  type        = string
}

variable "ecs_exec_log_group_name" {
  description = "CloudWatch log group name for ECS Exec."
  type        = string
}

variable "common_tags" {
  description = "Common tags applied to ECS cluster resources."
  type        = map(string)
}

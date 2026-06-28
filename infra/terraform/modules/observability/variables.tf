variable "project_name" {
  description = "Project name used in log group paths."
  type        = string
}

variable "environment" {
  description = "Deployment environment name."
  type        = string
}

variable "name_prefix" {
  description = "Common resource name prefix."
  type        = string
}

variable "common_tags" {
  description = "Common tags applied to observability resources."
  type        = map(string)
}

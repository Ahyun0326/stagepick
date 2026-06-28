variable "aws_region" {
  description = "AWS region where global domain resources are created."
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
  default     = "global"
}

variable "domain_name" {
  description = "Root domain name managed by Route 53."
  type        = string
  default     = "stagepick.cloud"
}

variable "vercel_cname_value" {
  description = "Vercel CNAME target for www frontend domain."
  type        = string
}

variable "enable_frontend_apex_record" {
  description = "Whether to create apex A record for Vercel."
  type        = bool
  default     = true
}

variable "vercel_apex_a_record" {
  description = "Vercel apex A record value."
  type        = string
}

variable "name_prefix" {
  description = "Common resource name prefix."
  type        = string
}

variable "vpc_id" {
  description = "VPC ID for security groups."
  type        = string
}

variable "app_port" {
  description = "Application container port."
  type        = number
}

variable "bastion_allowed_ssh_cidr" {
  description = "CIDR block allowed to SSH into the bastion host."
  type        = string
}

variable "common_tags" {
  description = "Common tags applied to security resources."
  type        = map(string)
}

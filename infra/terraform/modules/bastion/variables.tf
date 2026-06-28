variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "public_subnet_id" {
  description = "Public subnet ID where the bastion host runs."
  type        = string
}

variable "security_group_id" {
  description = "Security group ID attached to the bastion host."
  type        = string
}

variable "key_name" {
  description = "Existing EC2 key pair name used for SSH access."
  type        = string
}

variable "instance_type" {
  description = "Bastion EC2 instance type."
  type        = string
  default     = "t3.micro"
}

variable "common_tags" {
  description = "Common tags applied to bastion resources."
  type        = map(string)
}

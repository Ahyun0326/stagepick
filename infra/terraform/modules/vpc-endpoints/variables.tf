variable "aws_region" {
  description = "AWS region for endpoint service names."
  type        = string
}

variable "name_prefix" {
  description = "Common resource name prefix."
  type        = string
}

variable "vpc_id" {
  description = "VPC ID for endpoints."
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for interface endpoints."
  type        = list(string)
}

variable "private_route_table_id" {
  description = "Private route table ID for the S3 gateway endpoint."
  type        = string
}

variable "vpc_endpoint_security_group" {
  description = "Security group ID attached to interface endpoints."
  type        = string
}

variable "common_tags" {
  description = "Common tags applied to VPC endpoint resources."
  type        = map(string)
}

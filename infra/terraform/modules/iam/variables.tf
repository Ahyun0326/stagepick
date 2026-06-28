variable "name_prefix" {
  description = "Common resource name prefix."
  type        = string
}

variable "common_tags" {
  description = "Common tags applied to IAM resources."
  type        = map(string)
}

variable "name_prefix" {
  description = "Resource name prefix."
  type        = string
}

variable "domain_name" {
  description = "Root domain name."
  type        = string
}

variable "common_tags" {
  description = "Common resource tags."
  type        = map(string)
}

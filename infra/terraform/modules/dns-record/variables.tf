variable "hosted_zone_id" {
  description = "Route 53 hosted zone ID."
  type        = string
}

variable "domain_name" {
  description = "Root domain name."
  type        = string
}

variable "alb_dns_name" {
  description = "ALB DNS name."
  type        = string
}

variable "alb_zone_id" {
  description = "ALB hosted zone ID."
  type        = string
}

variable "api_subdomain" {
  description = "Subdomain used by the backend API."
  type        = string
}

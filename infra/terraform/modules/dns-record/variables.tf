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

variable "frontend_domain_name" {
  description = "Frontend domain connected to Vercel."
  type        = string
  default     = "www.stagepick.cloud"
}

variable "vercel_cname_value" {
  description = "Vercel CNAME target for frontend domain."
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
output "route53_name_servers" {
  description = "Route 53 name servers to register at Gabia."
  value       = module.domain.name_servers
}

output "route53_hosted_zone_id" {
  description = "Route 53 hosted zone ID."
  value       = module.domain.hosted_zone_id
}

output "acm_certificate_arn" {
  description = "Validated ACM certificate ARN."
  value       = module.domain.acm_certificate_arn
}

output "frontend_www_fqdn" {
  description = "Frontend www record FQDN."
  value       = aws_route53_record.frontend_www.fqdn
}

output "frontend_apex_fqdn" {
  description = "Frontend apex record FQDN."
  value       = one(aws_route53_record.frontend_apex[*].fqdn)
}

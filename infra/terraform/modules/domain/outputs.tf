output "hosted_zone_id" {
  description = "Route 53 hosted zone ID."
  value       = aws_route53_zone.main.zone_id
}

output "name_servers" {
  description = "Route 53 name servers to register at Gabia."
  value       = aws_route53_zone.main.name_servers
}

output "acm_certificate_arn" {
  description = "Validated ACM certificate ARN."
  value       = aws_acm_certificate_validation.main.certificate_arn
}
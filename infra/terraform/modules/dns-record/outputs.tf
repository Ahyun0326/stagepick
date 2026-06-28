output "api_record_fqdn" {
  description = "API domain alias record FQDN."
  value       = aws_route53_record.api_alias.fqdn
}
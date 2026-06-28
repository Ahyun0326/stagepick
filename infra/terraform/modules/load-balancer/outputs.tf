# Outputs will be added when load balancer resources are implemented.
output "target_group_arn" {
  description = "API target group ARN."
  value       = aws_lb_target_group.api.arn
}

output "alb_dns_name" {
  description = "ALB DNS name."
  value       = aws_lb.api.dns_name
}

output "alb_zone_id" {
  description = "ALB hosted zone ID for Route 53 alias records."
  value       = aws_lb.api.zone_id
}

output "alb_arn" {
  description = "ALB ARN."
  value       = aws_lb.api.arn
}
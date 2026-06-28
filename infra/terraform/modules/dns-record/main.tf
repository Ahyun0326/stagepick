resource "aws_route53_record" "api_alias" {
  zone_id = var.hosted_zone_id
  name    = "${var.api_subdomain}.${var.domain_name}"
  type    = "A"

  alias {
    name                   = var.alb_dns_name
    zone_id                = var.alb_zone_id
    evaluate_target_health = true
  }
}

resource "aws_route53_record" "frontend_www" {
  zone_id = var.hosted_zone_id
  name    = var.frontend_domain_name
  type    = "CNAME"
  ttl     = 300
  records = [var.vercel_cname_value]
}

resource "aws_route53_record" "frontend_apex" {
  count = var.enable_frontend_apex_record ? 1 : 0

  zone_id = var.hosted_zone_id
  name    = var.domain_name
  type    = "A"
  ttl     = 300
  records = [var.vercel_apex_a_record]
}
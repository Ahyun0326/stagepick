module "domain" {
  source = "../modules/domain"

  name_prefix = local.name_prefix
  domain_name = var.domain_name
  common_tags = local.common_tags
}

resource "aws_route53_record" "frontend_www" {
  zone_id = module.domain.hosted_zone_id
  name    = "www.${var.domain_name}"
  type    = "CNAME"
  ttl     = 300
  records = [var.vercel_cname_value]
}

resource "aws_route53_record" "frontend_apex" {
  count = var.enable_frontend_apex_record ? 1 : 0

  zone_id = module.domain.hosted_zone_id
  name    = var.domain_name
  type    = "A"
  ttl     = 300
  records = [var.vercel_apex_a_record]
}

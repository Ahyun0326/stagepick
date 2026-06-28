# Route 53 Hosted Zone + ACM certificate DNS validation

# Route 53 Hosted Zone 생성
resource "aws_route53_zone" "main" {
  name = var.domain_name

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-hosted-zone"
  })
}

# ACM 인증서 요청
resource "aws_acm_certificate" "main" {
  domain_name       = var.domain_name
  validation_method = "DNS"

  # 루트 도메인은 domain_name이 인증하고, api/www/admin 같은 1단계 하위 도메인은 wildcard가 인증
  subject_alternative_names = [
    "*.${var.domain_name}"
  ]

  lifecycle {
    create_before_destroy = true
  }

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-certificate"
  })
}

resource "aws_route53_record" "certificate_validation" {
  zone_id = aws_route53_zone.main.zone_id
  # 루트 도메인과 wildcard 도메인은 같은 ACM 검증 CNAME을 사용하므로 첫 번째 검증 옵션만 등록
  name    = tolist(aws_acm_certificate.main.domain_validation_options)[0].resource_record_name
  type    = tolist(aws_acm_certificate.main.domain_validation_options)[0].resource_record_type
  ttl     = 60
  records = [tolist(aws_acm_certificate.main.domain_validation_options)[0].resource_record_value]
}

# ACM 검증 완료 리소스 생성
resource "aws_acm_certificate_validation" "main" {
  certificate_arn         = aws_acm_certificate.main.arn
  validation_record_fqdns = [aws_route53_record.certificate_validation.fqdn]
}

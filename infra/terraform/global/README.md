# Terraform Global Infrastructure

이 디렉터리는 비용 절감을 위해 prod 인프라를 destroy해도 유지해야 하는 전역 리소스를 관리한다.

## 포함 리소스

- Route 53 Hosted Zone
- ACM Certificate
- ACM DNS validation record
- Vercel frontend DNS record

## 제외 리소스

- API ALB
- `api.stagepick.cloud` Alias record
- ECS
- RDS
- Redis
- Bastion
- VPC

## 명령어

```bash
terraform -chdir=infra/terraform/global init
terraform -chdir=infra/terraform/global validate
terraform -chdir=infra/terraform/global plan -var-file=envs/prod/terraform.tfvars
terraform -chdir=infra/terraform/global apply -var-file=envs/prod/terraform.tfvars
```

## Output

prod root module에 필요한 값은 global output에서 확인한다.

```bash
terraform -chdir=infra/terraform/global output route53_hosted_zone_id
terraform -chdir=infra/terraform/global output acm_certificate_arn
terraform -chdir=infra/terraform/global output route53_name_servers
```

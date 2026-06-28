# Terraform Infrastructure

이 디렉터리는 `prod` 환경을 위한 Terraform root module이다.

Route 53 Hosted Zone, ACM certificate, Vercel frontend DNS record처럼 계속 유지해야 하는 리소스는 `infra/terraform/global`에서 관리한다.

## 구조

```text
infra/terraform/
  main.tf                  # 로컬 모듈 조립
  variables.tf             # 공통 입력값
  outputs.tf               # 주요 출력값
  envs/prod/               # prod 환경 변수 예시
  global/                  # Route 53, ACM, frontend DNS 같은 유지 리소스
  modules/                 # 용도별 로컬 모듈
```

## 모듈 역할

- `network`: VPC, subnet, route table, internet gateway
- `security`: ALB/ECS/VPC Endpoint 보안그룹
- `vpc-endpoints`: ECR, CloudWatch Logs, S3 endpoint
- `registry`: ECR repository
- `observability`: CloudWatch log group
- `iam`: ECS task execution role
- `ecs-cluster`: ECS Fargate cluster
- `load-balancer`: ALB, Target Group, HTTP redirect, HTTPS Listener
- `domain`: Route 53 Hosted Zone, ACM certificate, DNS validation. `global` root에서 사용한다.
- `dns-record`: `api.stagepick.cloud` Alias record
- `ecs-service`: ECS API/Worker service
- `datastore`: RDS, ElastiCache

## 명령어

```bash
terraform -chdir=infra/terraform fmt -recursive -check -diff
terraform -chdir=infra/terraform validate
terraform -chdir=infra/terraform plan -var-file=envs/prod/terraform.tfvars.example
terraform -chdir=infra/terraform apply -var-file=envs/prod/terraform.tfvars.example
```

## 현재 도메인 구성

- Hosted Zone: `stagepick.cloud` (`global` root에서 관리)
- API Alias: `api.stagepick.cloud`
- ACM Certificate: `stagepick.cloud`, `*.stagepick.cloud` (`global` root에서 관리)
- HTTP 80 요청은 ALB에서 HTTPS 443으로 리다이렉트한다.

## 유용한 Output

```bash
terraform -chdir=infra/terraform output api_service_url
```

global root output:

```bash
terraform -chdir=infra/terraform/global output route53_name_servers
terraform -chdir=infra/terraform/global output route53_hosted_zone_id
terraform -chdir=infra/terraform/global output acm_certificate_arn
```

## 운영 가이드

- 장기 미사용 시 `infra/terraform/global`은 유지하고, `infra/terraform` prod root만 destroy한다.

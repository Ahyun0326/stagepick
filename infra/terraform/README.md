# Terraform Infrastructure

이 디렉터리는 `prod` 환경을 위한 Terraform root module이다.

## 구조

```text
infra/terraform/
  main.tf                  # 로컬 모듈 조립
  variables.tf             # 공통 입력값
  outputs.tf               # 주요 출력값
  envs/prod/               # prod 환경 변수 예시
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
- `domain`: Route 53 Hosted Zone, ACM certificate, DNS validation
- `dns-record`: `api.stagepick.cloud` Alias record
- `ecs-service`: ECS API/Worker service 구성 예정
- `datastore`: RDS, ElastiCache 구성 예정

## 명령어

```bash
terraform -chdir=infra/terraform fmt -recursive -check -diff
terraform -chdir=infra/terraform validate
terraform -chdir=infra/terraform plan -var-file=envs/prod/terraform.tfvars.example
terraform -chdir=infra/terraform apply -var-file=envs/prod/terraform.tfvars.example
```

## 현재 도메인 구성

- Hosted Zone: `stagepick.cloud`
- API Alias: `api.stagepick.cloud`
- ACM Certificate: `stagepick.cloud`, `*.stagepick.cloud`
- HTTP 80 요청은 ALB에서 HTTPS 443으로 리다이렉트한다.

## 유용한 Output

```bash
terraform -chdir=infra/terraform output route53_name_servers
terraform -chdir=infra/terraform output api_service_url
```

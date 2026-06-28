#!/usr/bin/env bash
set -euo pipefail

# 로컬 수동 배포와 CI/CD image push에서 함께 사용할 수 있는 공통 스크립트다.
#
# 로컬에서는 ECR_URI를 생략하면 Terraform output에서 ECR repository URL을 조회한다.
# CI/CD에서는 Terraform을 실행하지 않도록 ECR_URI와 IMAGE_TAG를 외부에서 주입해 실행한다.

# 필요한 변수 준비
AWS_REGION=${AWS_REGION:-ap-northeast-2}
TERRAFORM_DIR=${TERRAFORM_DIR:-infra/terraform}
IMAGE_NAME=${IMAGE_NAME:-concerts-api}
IMAGE_TAG=${IMAGE_TAG:-$(git rev-parse --short HEAD)}
PLATFORM=${PLATFORM:-linux/amd64}

AWS_CMD=(aws)

if [ -n "${AWS_PROFILE:-}" ]; then
  AWS_CMD+=(--profile "$AWS_PROFILE")
fi

# ECR_URI가 외부에서 주입되면 그 값을 사용한다.
# 값이 없으면 로컬 Terraform state의 output에서 ECR repository URL을 조회한다.
# CI/CD에서는 GitHub Variables 등으로 ECR_URI를 직접 넘겨 Terraform output 의존을 피한다.

ECR_URI=${ECR_URI:-$(terraform -chdir="$TERRAFORM_DIR" output -raw ecr_repository_url)}
ECR_REGISTRY=$(echo "$ECR_URI" | cut -d/ -f1)

# ECR 로그인
"${AWS_CMD[@]}" ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

# Docker 이미지 빌드
docker buildx build \
  --platform "$PLATFORM" \
  -t "$IMAGE_NAME:$IMAGE_TAG" \
  --load \
  .

# ECR용 태그 추가
docker tag "$IMAGE_NAME:$IMAGE_TAG" "$ECR_URI:$IMAGE_TAG"
docker tag "$IMAGE_NAME:$IMAGE_TAG" "$ECR_URI:latest"

# Docker push
docker push "$ECR_URI:$IMAGE_TAG"
docker push "$ECR_URI:latest"

# 최종 이미지 URI 출력
echo "Pushed image:"
echo "$ECR_URI:$IMAGE_TAG"

#!/usr/bin/env bash
set -euo pipefail

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

# ECR URI 조회
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

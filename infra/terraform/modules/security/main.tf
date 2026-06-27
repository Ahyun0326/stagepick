resource "aws_security_group" "alb_sg" {
  name        = "${var.name_prefix}-alb-sg"
  description = "Security group for public ALB"
  vpc_id      = var.vpc_id

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-alb-sg"
  })
}

# HTTP 요청은 ALB listener에서 HTTPS로 리다이렉트하기 위해 허용
resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb_sg.id
  description       = "Allow HTTP traffic for HTTPS redirect"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

# 실제 외부 API 요청은 HTTPS로만 받는다
resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  security_group_id = aws_security_group.alb_sg.id
  description       = "Allow HTTPS traffic from internet"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

# ALB가 private subnet의 ECS API task로 애플리케이션 트래픽을 전달
resource "aws_vpc_security_group_egress_rule" "alb_to_ecs_api" {
  security_group_id = aws_security_group.alb_sg.id
  description       = "Forward traffic to ECS API service"

  referenced_security_group_id = aws_security_group.ecs_api_sg.id
  from_port                    = var.app_port
  to_port                      = var.app_port
  ip_protocol                  = "tcp"
}

resource "aws_security_group" "ecs_api_sg" {
  name        = "${var.name_prefix}-ecs-api-sg"
  description = "Security group for ECS API service"
  vpc_id      = var.vpc_id

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-ecs-api-sg"
  })
}

# ECS API task는 ALB에서 전달된 애플리케이션 포트만 허용
resource "aws_vpc_security_group_ingress_rule" "ecs_api_from_alb" {
  security_group_id = aws_security_group.ecs_api_sg.id
  description       = "Allow application traffic from ALB"

  referenced_security_group_id = aws_security_group.alb_sg.id
  from_port                    = var.app_port
  to_port                      = var.app_port
  ip_protocol                  = "tcp"
}

# 초기 구성에서는 VPC Endpoint, RDS, Redis 접근을 위해 outbound를 열어둠
# 이후 RDS/Redis 보안그룹이 확정되면 대상 SG별 egress로 좁힐 수 있다.
resource "aws_vpc_security_group_egress_rule" "ecs_api_all_outbound" {
  security_group_id = aws_security_group.ecs_api_sg.id
  description       = "Allow outbound traffic from ECS API service"

  cidr_ipv4   = "0.0.0.0/0"
  ip_protocol = "-1"
}

resource "aws_security_group" "ecs_worker_sg" {
  name        = "${var.name_prefix}-ecs-worker-sg"
  description = "Security group for ECS worker service"
  vpc_id      = var.vpc_id

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-ecs-worker-sg"
  })
}

# Worker task는 외부 요청을 직접 받지 않고 스케줄러/백그라운드 작업만 수행
# 따라서 ingress rule은 만들지 않는다.
resource "aws_vpc_security_group_egress_rule" "ecs_worker_all_outbound" {
  security_group_id = aws_security_group.ecs_worker_sg.id
  description       = "Allow outbound traffic from ECS worker service"

  cidr_ipv4   = "0.0.0.0/0"
  ip_protocol = "-1"
}

resource "aws_security_group" "vpc_endpoint_sg" {
  name        = "${var.name_prefix}-vpce-sg"
  description = "Security group for interface VPC endpoints"
  vpc_id      = var.vpc_id

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-vpce-sg"
  })
}

# Interface VPC Endpoint는 private subnet의 ECS task가 AWS API에 접근할 때 사용
resource "aws_vpc_security_group_ingress_rule" "vpc_endpoint_from_ecs_api" {
  security_group_id = aws_security_group.vpc_endpoint_sg.id
  description       = "Allow HTTPS from ECS API service"

  referenced_security_group_id = aws_security_group.ecs_api_sg.id
  from_port                    = 443
  to_port                      = 443
  ip_protocol                  = "tcp"
}

# Worker도 ECR image pull, CloudWatch Logs 전송 등을 위해 VPC Endpoint HTTPS 접근이 필요
resource "aws_vpc_security_group_ingress_rule" "vpc_endpoint_from_ecs_worker" {
  security_group_id = aws_security_group.vpc_endpoint_sg.id
  description       = "Allow HTTPS from ECS worker service"

  referenced_security_group_id = aws_security_group.ecs_worker_sg.id
  from_port                    = 443
  to_port                      = 443
  ip_protocol                  = "tcp"
}

resource "aws_security_group" "rds_sg" {
  name        = "${var.name_prefix}-rds-sg"
  description = "Security group for RDS MySQL"
  vpc_id      = var.vpc_id

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-rds-sg"
  })
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_ecs_api" {
  security_group_id            = aws_security_group.rds_sg.id
  description                  = "Allow MySQL from ECS API service"
  referenced_security_group_id = aws_security_group.ecs_api_sg.id
  from_port                    = 3306
  to_port                      = 3306
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_ecs_worker" {
  security_group_id            = aws_security_group.rds_sg.id
  description                  = "Allow MySQL from ECS worker service"
  referenced_security_group_id = aws_security_group.ecs_worker_sg.id
  from_port                    = 3306
  to_port                      = 3306
  ip_protocol                  = "tcp"
}

resource "aws_security_group" "redis_sg" {
  name        = "${var.name_prefix}-redis-sg"
  description = "Security group for Redis"
  vpc_id      = var.vpc_id

  tags = merge(var.common_tags, {
    Name = "${var.name_prefix}-redis-sg"
  })
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_ecs_api" {
  security_group_id            = aws_security_group.redis_sg.id
  description                  = "Allow Redis from ECS API service"
  referenced_security_group_id = aws_security_group.ecs_api_sg.id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_ecs_worker" {
  security_group_id            = aws_security_group.redis_sg.id
  description                  = "Allow Redis from ECS worker service"
  referenced_security_group_id = aws_security_group.ecs_worker_sg.id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
}
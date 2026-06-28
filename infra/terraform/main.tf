module "registry" {
  source = "./modules/registry"

  ecr_repository_name = var.ecr_repository_name
  common_tags         = local.common_tags
}

module "observability" {
  source = "./modules/observability"

  project_name = var.project_name
  environment  = var.environment
  name_prefix  = local.name_prefix
  common_tags  = local.common_tags
}

module "iam" {
  source = "./modules/iam"

  name_prefix = local.name_prefix
  common_tags = local.common_tags
}

module "ecs_cluster" {
  source = "./modules/ecs-cluster"

  name_prefix             = local.name_prefix
  ecs_exec_log_group_name = module.observability.ecs_exec_log_group_name
  common_tags             = local.common_tags
}

module "network" {
  source = "./modules/network"

  name_prefix          = local.name_prefix
  vpc_cidr             = var.vpc_cidr
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
  common_tags          = local.common_tags
}

module "security" {
  source = "./modules/security"

  name_prefix              = local.name_prefix
  vpc_id                   = module.network.vpc_id
  app_port                 = var.app_port
  bastion_allowed_ssh_cidr = var.bastion_allowed_ssh_cidr
  common_tags              = local.common_tags
}

module "vpc_endpoints" {
  source = "./modules/vpc-endpoints"

  aws_region                  = var.aws_region
  name_prefix                 = local.name_prefix
  vpc_id                      = module.network.vpc_id
  private_subnet_ids          = slice(module.network.private_subnet_ids, 0, 1)
  private_route_table_id      = module.network.private_route_table_id
  vpc_endpoint_security_group = module.security.vpc_endpoint_security_group_id
  common_tags                 = local.common_tags
}

module "load_balancer" {
  source = "./modules/load-balancer"

  name_prefix           = local.name_prefix
  public_subnet_ids     = module.network.public_subnet_ids
  alb_security_group_id = module.security.alb_security_group_id
  vpc_id                = module.network.vpc_id
  app_port              = var.app_port
  common_tags           = local.common_tags
  certificate_arn       = var.acm_certificate_arn
}

module "dns_record" {
  source = "./modules/dns-record"

  hosted_zone_id = var.hosted_zone_id
  domain_name    = var.domain_name
  api_subdomain  = var.api_subdomain
  alb_dns_name   = module.load_balancer.alb_dns_name
  alb_zone_id    = module.load_balancer.alb_zone_id
}

module "ecs_service" {
  source = "./modules/ecs-service"

  name_prefix              = local.name_prefix
  aws_region               = var.aws_region
  cluster_arn              = module.ecs_cluster.cluster_arn
  cluster_name             = module.ecs_cluster.cluster_name
  execution_role_arn       = module.iam.ecs_task_execution_role_arn
  initial_image_uri        = local.initial_image_uri
  app_port                 = var.app_port
  task_cpu                 = var.task_cpu
  task_memory              = var.task_memory
  api_desired_count        = var.api_desired_count
  worker_desired_count     = var.worker_desired_count
  private_subnet_ids       = slice(module.network.private_subnet_ids, 0, 1)
  api_security_group_id    = module.security.ecs_api_security_group_id
  worker_security_group_id = module.security.ecs_worker_security_group_id
  target_group_arn         = module.load_balancer.target_group_arn
  api_log_group_name       = module.observability.api_log_group_name
  worker_log_group_name    = module.observability.worker_log_group_name
  jwt_access_secret        = var.jwt_access_secret
  datasource_url           = "jdbc:mysql://${module.datastore.rds_endpoint}:${module.datastore.rds_port}/${var.db_name}?characterEncoding=UTF-8&serverTimezone=UTC"
  datasource_username      = var.db_username
  datasource_password      = var.db_password
  redis_host               = module.datastore.redis_primary_endpoint
  redis_port               = module.datastore.redis_port
  common_tags              = local.common_tags
}

module "datastore" {
  source = "./modules/datastore"

  name_prefix             = local.name_prefix
  private_subnet_ids      = module.network.private_subnet_ids
  rds_security_group_id   = module.security.rds_security_group_id
  redis_security_group_id = module.security.redis_security_group_id

  db_name              = var.db_name
  db_username          = var.db_username
  db_password          = var.db_password
  db_instance_class    = var.db_instance_class
  db_allocated_storage = var.db_allocated_storage
  redis_node_type      = var.redis_node_type

  common_tags = local.common_tags
}

module "bastion" {
  source = "./modules/bastion"

  name_prefix       = local.name_prefix
  public_subnet_id  = module.network.public_subnet_ids[0]
  security_group_id = module.security.bastion_security_group_id
  key_name          = var.bastion_key_name
  instance_type     = var.bastion_instance_type
  common_tags       = local.common_tags
}

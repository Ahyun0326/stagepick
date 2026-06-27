# Outputs will be added when datastore resources are implemented.
output "rds_endpoint" {
  description = "RDS endpoint address."
  value       = aws_db_instance.mysql.address
}

output "rds_port" {
  description = "RDS port."
  value       = aws_db_instance.mysql.port
}

output "redis_primary_endpoint" {
  description = "Redis primary endpoint address."
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "redis_port" {
  description = "Redis port."
  value       = aws_elasticache_replication_group.redis.port
}
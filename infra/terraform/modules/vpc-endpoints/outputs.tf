output "ecr_api_endpoint_id" {
  description = "ECR API VPC endpoint ID."
  value       = aws_vpc_endpoint.ecr_api.id
}

output "ecr_dkr_endpoint_id" {
  description = "ECR Docker VPC endpoint ID."
  value       = aws_vpc_endpoint.ecr_dkr.id
}

output "logs_endpoint_id" {
  description = "CloudWatch Logs VPC endpoint ID."
  value       = aws_vpc_endpoint.logs.id
}

output "s3_endpoint_id" {
  description = "S3 gateway VPC endpoint ID."
  value       = aws_vpc_endpoint.s3.id
}

output "ecr_repository_name" {
  value = aws_ecr_repository.api.name
}

output "ecr_repository_url" {
  value = aws_ecr_repository.api.repository_url
}
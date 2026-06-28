variable "ecr_repository_name" {
  description = "ECR repository name for the backend API image."
  type        = string
}

variable "common_tags" {
  description = "Common tags applied to registry resources."
  type        = map(string)
}

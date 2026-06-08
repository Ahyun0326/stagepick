variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "aws_profile" {
  type    = string
  default = "ys-admin"
}

variable "project_name" {
  type    = string
  default = "concerts"
}

variable "environment" {
  type    = string
  default = "prod"
}

variable "ecr_repository_name" {
  type    = string
  default = "concerts-api"
}
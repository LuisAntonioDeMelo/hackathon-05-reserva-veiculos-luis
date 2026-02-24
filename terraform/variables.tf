variable "project_name" {
  description = "Prefix for resource names"
  type        = string
  default     = "vehicle-platform"
}

variable "aws_region" {
  description = "AWS region used by Terraform"
  type        = string
  default     = "us-east-1"
}

variable "localstack_endpoint" {
  description = "LocalStack edge endpoint"
  type        = string
  default     = "http://localhost:4566"
}

variable "aws_endpoint_override" {
  description = "Endpoint used by Lambda SDK clients (must be reachable from Lambda runtime)"
  type        = string
  default     = "http://localhost.localstack.cloud:4566"
}

variable "client_data_encryption_key" {
  description = "Base64 AES-256 key used to encrypt client sensitive data in application layer"
  type        = string
  sensitive   = true
}

variable "lambda_artifact_path" {
  description = "Path to Lambda Java artifact (JAR)"
  type        = string
  default     = "../hackaton-projeto-5/target/function.jar"
}

variable "lambda_timeout_seconds" {
  description = "Lambda timeout in seconds"
  type        = number
  default     = 30
}

variable "lambda_memory_mb" {
  description = "Lambda memory in MB"
  type        = number
  default     = 512
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention period"
  type        = number
  default     = 14
}

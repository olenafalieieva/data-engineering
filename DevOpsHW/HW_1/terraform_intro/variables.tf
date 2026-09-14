variable "region" {
  description = "AWS region"
  type        = string
  default     = "us-east-2"
}

variable "aws_profile" {
  description = "AWS profile to use"
  type        = string
  default     = "terraform_de"
}

variable "s3_bucket_name" {
  description = "S3 bucket name (must be globally unique)"
  type        = string
  default     = "test-bucket-olenka"
}

variable "db_allocated_storage" {
  description = "RDS allocated storage in GB"
  type        = number
  default     = 10
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

variable "db_name" {
  description = "RDS database name"
  type        = string
  default     = "terraform_test_db"
}

variable "db_username" {
  description = "RDS database admin username"
  type        = string
  default     = "db_odmin"
  sensitive   = true
}

variable "db_password" {
  description = "RDS database admin password"
  type        = string
  sensitive   = true
}
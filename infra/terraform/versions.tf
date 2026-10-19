terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Estado remoto con bloqueo (descomentar en un entorno real):
  # backend "s3" {
  #   bucket       = "my-terraform-state"
  #   key          = "orders-service/terraform.tfstate"
  #   region       = "us-east-1"
  #   encrypt      = true
  #   use_lockfile = true
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "orders-service"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

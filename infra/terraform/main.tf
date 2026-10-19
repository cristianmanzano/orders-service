locals {
  name = "orders-service-${var.environment}"
}

# ---------------------------------------------------------------------------
# DynamoDB: traza de auditoría de pedidos
# ---------------------------------------------------------------------------
resource "aws_dynamodb_table" "order_audit" {
  name         = "order_audit_${var.environment}"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "orderId"
  range_key    = "sk"

  attribute {
    name = "orderId"
    type = "S"
  }

  attribute {
    name = "sk"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }

  deletion_protection_enabled = var.environment == "prod"
}

# ---------------------------------------------------------------------------
# ECR: registro de imágenes con escaneo de vulnerabilidades al subir
# ---------------------------------------------------------------------------
resource "aws_ecr_repository" "orders_service" {
  name                 = local.name
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "KMS"
  }
}

resource "aws_ecr_lifecycle_policy" "orders_service" {
  repository = aws_ecr_repository.orders_service.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Conservar solo las últimas 20 imágenes"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 20
      }
      action = { type = "expire" }
    }]
  })
}

# ---------------------------------------------------------------------------
# IAM (IRSA): el pod asume este rol mediante su ServiceAccount, sin claves estáticas.
# Principio de mínimo privilegio: solo las acciones que usa el servicio y solo sobre su tabla.
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.eks_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.eks_oidc_provider_url}:sub"
      values   = ["system:serviceaccount:${var.k8s_namespace}:orders-service"]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.eks_oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "orders_service" {
  name               = local.name
  assume_role_policy = data.aws_iam_policy_document.assume_role.json
}

data "aws_iam_policy_document" "dynamodb_access" {
  statement {
    actions   = ["dynamodb:PutItem", "dynamodb:Query", "dynamodb:DescribeTable"]
    resources = [aws_dynamodb_table.order_audit.arn]
  }
}

resource "aws_iam_role_policy" "dynamodb_access" {
  name   = "dynamodb-order-audit"
  role   = aws_iam_role.orders_service.id
  policy = data.aws_iam_policy_document.dynamodb_access.json
}

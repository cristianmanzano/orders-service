output "dynamodb_table_name" {
  description = "Nombre de la tabla de auditoría (variable DYNAMODB_AUDIT_TABLE)"
  value       = aws_dynamodb_table.order_audit.name
}

output "ecr_repository_url" {
  description = "URL del repositorio ECR para publicar la imagen"
  value       = aws_ecr_repository.orders_service.repository_url
}

output "service_role_arn" {
  description = "ARN del rol IAM para anotar el ServiceAccount (eks.amazonaws.com/role-arn)"
  value       = aws_iam_role.orders_service.arn
}

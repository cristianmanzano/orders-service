variable "aws_region" {
  description = "Región de AWS donde se despliegan los recursos"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Nombre del entorno (dev, staging, prod)"
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment debe ser dev, staging o prod."
  }
}

variable "eks_oidc_provider_arn" {
  description = "ARN del proveedor OIDC del clúster EKS (para IRSA)"
  type        = string
}

variable "eks_oidc_provider_url" {
  description = "URL del proveedor OIDC del clúster EKS, sin https://"
  type        = string
}

variable "k8s_namespace" {
  description = "Namespace de Kubernetes donde corre el servicio"
  type        = string
  default     = "orders"
}

output "api_node_public_ip" {
  description = "EC2 public IP for the API NodePort after Kubernetes deployment."
  value       = aws_instance.app.public_ip
}

output "api_url" {
  description = "API URL after the Kubernetes Service is deployed and the demo CIDR is allowed."
  value       = "http://${aws_instance.app.public_ip}:30080"
}

output "rds_endpoint" {
  description = "Private RDS endpoint; reachable only from the application VPC."
  value       = aws_db_instance.demo.address
}

output "rds_master_secret_arn" {
  description = "Secrets Manager ARN for the RDS-generated master credentials."
  value       = aws_db_instance.demo.master_user_secret[0].secret_arn
}

output "ecr_repository_url" {
  description = "Push the versioned application image to this ECR repository."
  value       = aws_ecr_repository.app.repository_url
}

output "ec2_instance_id" {
  description = "Use this instance ID with AWS Systems Manager to administer K3s."
  value       = aws_instance.app.id
}

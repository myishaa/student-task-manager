variable "aws_region" {
  description = "AWS region in which the demo environment will be created."
  type        = string
}

variable "project_name" {
  description = "Prefix used for resource names and tags."
  type        = string
  default     = "student-task-manager"
}

variable "ec2_instance_type" {
  description = "EC2 size for the single-node K3s demo cluster. Review memory capacity and regional pricing before apply."
  type        = string
  default     = "t3.small"
}

variable "db_instance_class" {
  description = "RDS instance size for the demo database."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_name" {
  description = "Initial MySQL database name."
  type        = string
  default     = "student_task_manager"
}

variable "db_username" {
  description = "RDS master username. RDS stores the generated password in Secrets Manager."
  type        = string
  default     = "task_manager"
}

variable "demo_allowed_cidrs" {
  description = "IPv4 CIDRs allowed to reach the API NodePort on EC2. Use the team's public IP with /32; an empty list keeps it closed."
  type        = set(string)
  default     = []
}

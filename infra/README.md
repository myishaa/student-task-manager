# AWS infrastructure

Terraform provisions a single EC2 host running K3s, a private RDS MySQL database, an ECR repository, and the minimum networking/IAM resources for the demo. It does not provision a multi-node cluster or production high availability.

## Before applying

- Install Terraform 1.6+ and sign in with the AWS CLI profile for the intended account.
- Before using Terraform, check the active account and confirm it matches the AWS Console account you intend to use:

  ```sh
  aws sts get-caller-identity --profile default --region ap-southeast-2
  ```

- Use the verified profile and Sydney region explicitly for Terraform commands; do not rely on an implicit default profile or region.
- Review current EC2, RDS, storage, network, and registry pricing for that region.
- The default keeps the API NodePort closed to the internet. Leave `demo_allowed_cidrs` empty and use the Systems Manager port-forward below for the demo; only set a team `/32` CIDR if direct public access is needed.
- The EC2 default is `t3.medium` and the RDS default is `db.t4g.micro`. These defaults are a starting point, not a cost guarantee.
- EC2 uses standard CPU-credit mode to prevent surplus-credit charges; sustained load can be throttled at the instance baseline. RDS storage is fixed at 20 GB to avoid automatic storage growth. Sustained RDS T4g CPU use can still incur surplus-credit charges.
- The RDS password is generated and managed by AWS Secrets Manager. Terraform outputs only its secret ARN. Terraform state still contains infrastructure metadata; keep it private and never commit state files.

## Plan and apply

Run these commands from `infra/`. The first command downloads the AWS provider and creates local Terraform metadata, which are ignored by Git.

```sh
AWS_PROFILE=default AWS_REGION=ap-southeast-2 terraform init
terraform fmt -check
terraform validate
AWS_PROFILE=default AWS_REGION=ap-southeast-2 terraform plan \
  -var='aws_region=ap-southeast-2' \
  -var='demo_allowed_cidrs=["YOUR_PUBLIC_IP/32"]'
```

Review the plan and AWS estimate. Applying creates cloud resources and uses AWS Free Plan credits; get explicit approval before running `terraform apply`. Do not upgrade the Free Plan or activate paid-only services without approval. To remove an approved demo environment, inspect `terraform plan -destroy` and get approval before running `terraform destroy`. Database deletion skips a final snapshot in this short-lived demo configuration.

## After provisioning

1. Use Systems Manager Session Manager with the instance ID output; inbound SSH is intentionally not opened.
2. Retrieve the RDS username/password from the Secrets Manager ARN output and create the Kubernetes Secret described in [`../k8s/README.md`](../k8s/README.md). Do not paste the secret into source files or terminal transcripts.
3. Log in to ECR, build the image for the EC2 host architecture (`linux/amd64`), and push a versioned image tag. Create a Kubernetes ECR pull secret before deploying the workload.
4. Deploy the Kubernetes manifests and verify the API through an SSM port-forward. This avoids opening the NodePort to the internet:

   ```sh
   aws ssm start-session \
     --target "$(terraform output -raw ec2_instance_id)" \
     --document-name AWS-StartPortForwardingSession \
     --parameters '{"portNumber":["30080"],"localPortNumber":["30080"]}' \
     --profile default --region ap-southeast-2
   ```

   While the session is running, open <http://localhost:30080>.

## Security and scope

- RDS is private and accepts port 3306 only from the EC2 security group.
- The instance has no inbound SSH rule; Systems Manager provides administration access.
- The API NodePort is closed unless one or more demo CIDRs are configured.
- The RDS master secret is managed by AWS Secrets Manager, but long-lived Terraform state and AWS account credentials still require protection.
- A single EC2 host and RDS instance remain failure points. Destroy demo resources after the evaluation to stop ongoing charges.

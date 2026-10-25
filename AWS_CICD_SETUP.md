# Manual GitHub Actions deployment setup

The normal CI workflow continues to test/package the app and validate Terraform. The additional AWS release workflow is manual-only: it runs only when someone starts it from GitHub Actions and selects DEPLOY. It does not deploy on a push. The release job uses GitHub OIDC and Systems Manager; it does not store AWS access keys in GitHub.

## One-time account and repository setup

1. Confirm the target AWS account is 957123506131 in ap-southeast-2, and that the ECR repository and EC2 K3s instance still exist. The current project notes list instance i-0cd822e9d1ba2d0dc; verify it in the AWS console before saving it in GitHub.
2. In IAM, configure GitHub as an OIDC identity provider for token.actions.githubusercontent.com with audience sts.amazonaws.com if the provider is not already present.
3. Create an IAM role whose trust policy allows only this repository and the GitHub Actions environment named aws-demo. Use the exact subject repo:myishaa/student-task-manager:environment:aws-demo and audience sts.amazonaws.com. Do not use a wildcard subject.
4. Scope the role's ECR permissions to the student-task-manager repository: ecr:BatchCheckLayerAvailability, ecr:CompleteLayerUpload, ecr:InitiateLayerUpload, ecr:PutImage, ecr:UploadLayerPart, and ecr:DescribeRepositories. ecr:GetAuthorizationToken requires Resource "*". Scope ssm:SendCommand to the AWS-RunShellScript document in ap-southeast-2 and the verified EC2 instance; allow only the command-result read actions needed by the workflow. The EC2 instance profile must retain its existing SSM managed-instance and ECR read permissions.
5. In GitHub, open Settings → Environments and create aws-demo. Add a required reviewer if the repository plan offers that control. This puts a human approval gate in front of the AWS job.
6. In Settings → Secrets and variables → Actions → Variables, add:
   - AWS_DEPLOY_ROLE_ARN: the role ARN from step 3.
   - AWS_DEPLOY_INSTANCE_ID: the verified EC2 instance ID.
7. Confirm branch main contains this workflow. GitHub exposes workflow_dispatch only for workflows on the repository's default branch.

The AWS CLI credentials were unavailable when this workflow was prepared, so the AWS role, trust relationship, permissions, GitHub environment, and variables have not been created or checked here. No image has been published and no deployment has been triggered.

## Run a release

1. Open the repository's Actions tab and select Manual AWS image publish and deploy.
2. Choose the branch/commit to release and select DEPLOY. SKIP is the default and performs no AWS work.
3. Review the run. It verifies account 957123506131, builds an amd64 image tagged with the full commit SHA, pushes that tag to ECR, refreshes the short-lived ECR pull secret through SSM, updates the Deployment image, and waits for rollout readiness.
4. If the action fails, inspect the failing step and Systems Manager command output. Do not repeatedly rerun a partial deployment without checking the Deployment image and pod state.

This workflow is a prepared manual deployment path. It does not replace human approval for an actual release and it does not create AWS infrastructure.

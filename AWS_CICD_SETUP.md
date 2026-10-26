# Manual GitHub Actions deployment setup

The standard CI workflow tests/packages the app and validates Terraform. The AWS release workflow is manual-only: it runs when someone starts it in GitHub Actions and selects `DEPLOY`. A push does not deploy. It uses short-lived GitHub OIDC credentials and Systems Manager; it does not store AWS access keys in GitHub.

## Current status

Read-only AWS checks confirmed account `957123506131`, region `ap-southeast-2`, and running K3s instance `i-0cd822e9d1ba2d0dc`. The IAM role `student-task-manager-github-actions` does not exist. AWS Organizations explicitly denied `iam:CreateOpenIDConnectProvider` and `iam:GetOpenIDConnectProvider` under SCP `p-dz3d6hmi`; the CLI identity is `AccountFullAccessRole`. I cannot finish the AWS side from this member account until its Organizations administrator allows the required IAM setup actions or creates these resources for the project. No GitHub release or image deployment has been triggered.

The exact trust and permissions documents are prepared in [`infra/aws/github-actions-trust.json`](infra/aws/github-actions-trust.json) and [`infra/aws/github-actions-deploy-policy.json`](infra/aws/github-actions-deploy-policy.json). The policy grants image push only to the project ECR repository and Run Command only against the verified demo instance.

## AWS administrator setup

Use account `957123506131`. IAM is global; set the CLI's default region to `ap-southeast-2` for consistency.

1. Ask the AWS Organizations administrator to allow the project account to create/read the GitHub OIDC provider and role. The observed explicit deny is on `iam:CreateOpenIDConnectProvider`; review the SCP before changing it. The account administrator can also perform these commands in the project account.
2. Create the provider if it is not already present:

   ```sh
   aws iam create-open-id-connect-provider \
     --url https://token.actions.githubusercontent.com \
     --client-id-list sts.amazonaws.com \
     --tags Key=Project,Value=student-task-manager
   ```

3. Create the role and attach the prepared inline policy:

   ```sh
   aws iam create-role \
     --role-name student-task-manager-github-actions \
     --description "Manual GitHub Actions release for Student Task Manager" \
     --max-session-duration 3600 \
     --assume-role-policy-document file://infra/aws/github-actions-trust.json \
     --tags Key=Project,Value=student-task-manager

   aws iam put-role-policy \
     --role-name student-task-manager-github-actions \
     --policy-name student-task-manager-manual-release \
     --policy-document file://infra/aws/github-actions-deploy-policy.json
   ```

   If the provider already exists, skip its create command. If the role already exists, review its trust and permissions before updating it.
4. Record the returned role ARN. Do not grant broader access or change the trust subject to a wildcard.

## GitHub repository setup

1. In repository Settings → Environments, create `aws-demo`. Restrict deployments to the `main` branch and add a required reviewer if available. The environment name is part of the AWS trust subject.
2. Under Settings → Secrets and variables → Actions → Variables, add:
   - `AWS_DEPLOY_ROLE_ARN`: the role ARN from AWS.
   - `AWS_DEPLOY_INSTANCE_ID`: `i-0cd822e9d1ba2d0dc`.
3. Confirm the workflow is on the repository's default branch. GitHub exposes `workflow_dispatch` only for workflows on that branch.

The local GitHub CLI is currently signed out, so repository environment settings and variables are not configured from this workspace. Once the GitHub connection is available, these are the remaining repository-side settings.

## Run a release

1. Open the repository's Actions tab and select Manual AWS image publish and deploy.
2. Choose the branch/commit to release and select `DEPLOY`. `SKIP` is the default and performs no AWS work.
3. Review the run. It verifies account 957123506131, builds an amd64 image tagged with the full commit SHA, pushes that tag to ECR, refreshes the short-lived ECR pull secret through SSM, updates the Deployment image, and waits for rollout readiness.
4. If the action fails, inspect the failing step and Systems Manager command output. Do not repeatedly rerun a partial deployment without checking the Deployment image and pod state.

This workflow does not create infrastructure or deploy on a push. The SSM permission is limited to one demo host, but it still allows the workflow to execute shell commands on that host; keep the `aws-demo` environment's branch and reviewer controls enabled.

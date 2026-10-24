# Kubernetes deployment

These manifests target the single-node K3s cluster described in the project plan. They prepare the app deployment; Terraform, the cluster, ECR image, and RDS database must exist first. The image and RDS endpoint below are placeholders until those resources are provisioned.

## Prepare configuration

Build and push the app image to ECR with the `linux/amd64` platform. From your laptop, get the non-secret values that you will enter in the SSM shell:

```sh
terraform -chdir=infra output -raw ecr_repository_url
terraform -chdir=infra output -raw rds_endpoint
```

From the repository root, authenticate Docker to ECR and push a commit-tagged image. The platform flag builds for the EC2 host, even when running Docker on an Apple Silicon laptop:

```sh
export AWS_PROFILE=default AWS_REGION=ap-southeast-2
ECR_URI="$(terraform -chdir=infra output -raw ecr_repository_url)"
ECR_REGISTRY="${ECR_URI%%/*}"
IMAGE_TAG="$(git rev-parse --short HEAD)"
aws ecr get-login-password --profile "$AWS_PROFILE" --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"
docker build --platform linux/amd64 --tag "$ECR_URI:$IMAGE_TAG" .
docker push "$ECR_URI:$IMAGE_TAG"
```

Use `$ECR_URI:$IMAGE_TAG` as `APP_IMAGE` in the SSM shell.

The EC2 host runs K3s and the NodePort is closed by default. Open an SSM shell with `aws ssm start-session --target "$(terraform -chdir=infra output -raw ec2_instance_id)" --profile default --region ap-southeast-2`; run the commands below in that shell. Set `APP_IMAGE` to the pushed ECR image URI including tag and `RDS_ENDPOINT` to the RDS endpoint printed above. The public repository's raw manifest URLs are used so no inbound SSH or file transfer is needed.

Create the namespace and runtime configuration. The password is supplied as a Kubernetes Secret and is not stored in Git:

```sh
sudo k3s kubectl apply -f https://raw.githubusercontent.com/myishaa/student-task-manager/feature/aws-kubernetes-deployment/k8s/namespace.yaml
sudo k3s kubectl -n student-task-manager create configmap task-manager-config \
  --from-literal=DB_URL="jdbc:mysql://${RDS_ENDPOINT}:3306/student_task_manager?useSSL=true&serverTimezone=UTC" \
  --from-literal=DB_USERNAME=task_manager \
  --dry-run=client -o yaml | sudo k3s kubectl apply -f -
read -s -p 'RDS password (hidden): ' DB_PASSWORD; echo
SECRET_FILE="$(mktemp)"
chmod 600 "$SECRET_FILE"
trap 'rm -f "$SECRET_FILE"' EXIT
printf '%s' "$DB_PASSWORD" > "$SECRET_FILE"
unset DB_PASSWORD
sudo k3s kubectl -n student-task-manager create secret generic task-manager-db \
  --from-file=DB_PASSWORD="$SECRET_FILE" \
  --dry-run=client -o yaml | sudo k3s kubectl apply -f -
```

Enter the RDS password at the hidden prompt; retrieve it from the RDS master secret in AWS Secrets Manager, and do not paste it into chat or a manifest. Create an ECR pull secret using a short-lived ECR login token before applying the Deployment. The EC2 instance role has read-only ECR access. This snippet writes the Docker config to a temporary mode-600 file and removes it after use:

```sh
sudo -E python3 - <<'PY'
import base64
import json
import os
import subprocess
import tempfile

region = "ap-southeast-2"
registry = os.environ["APP_IMAGE"].split("/", 1)[0]
token = subprocess.check_output(
    ["aws", "ecr", "get-login-password", "--region", region], text=True
).strip()
docker_config = {
    "auths": {
        registry: {
            "username": "AWS",
            "password": token,
            "auth": base64.b64encode(f"AWS:{token}".encode()).decode(),
        }
    }
}
with tempfile.NamedTemporaryFile(mode="w", prefix="ecr-auth-", delete=False) as secret_file:
    os.chmod(secret_file.name, 0o600)
    json.dump(docker_config, secret_file)
try:
    manifest = subprocess.check_output([
        "k3s", "kubectl", "-n", "student-task-manager", "create", "secret", "generic",
        "ecr-pull", f"--from-file=.dockerconfigjson={secret_file.name}",
        "--type=kubernetes.io/dockerconfigjson", "--dry-run=client", "-o", "yaml",
    ], text=True)
    subprocess.run(["k3s", "kubectl", "apply", "-f", "-"], input=manifest, text=True, check=True)
finally:
    os.unlink(secret_file.name)
PY
```

ECR login tokens expire after 12 hours; refresh the secret before a later image rollout.

## Deploy

Replace the image placeholder in `deployment.yaml` with the exact ECR URI, then apply the workload:

```sh
curl -fsSL https://raw.githubusercontent.com/myishaa/student-task-manager/feature/aws-kubernetes-deployment/k8s/deployment.yaml \
  | sed "s#REPLACE_WITH_ECR_URI/student-task-manager:latest#${APP_IMAGE}#" \
  | sudo k3s kubectl apply -f -
sudo k3s kubectl apply -f https://raw.githubusercontent.com/myishaa/student-task-manager/feature/aws-kubernetes-deployment/k8s/service.yaml
sudo k3s kubectl apply -f https://raw.githubusercontent.com/myishaa/student-task-manager/feature/aws-kubernetes-deployment/k8s/hpa.yaml
sudo k3s kubectl -n student-task-manager rollout status deployment/task-manager-api
sudo k3s kubectl -n student-task-manager get pods,service,hpa
```

Metrics Server must be available for CPU-based autoscaling. On K3s, verify it with `sudo k3s kubectl top pods -n student-task-manager` and `sudo k3s kubectl describe hpa task-manager-api -n student-task-manager`.

The NodePort uses port `30080` and is closed to public traffic by default. To open the app locally without changing the security group, start an SSM port-forward session as shown in [`../infra/README.md`](../infra/README.md). If public access is required, allow only the demo team's `/32` CIDR in the EC2 security group. RDS should remain private and accept MySQL traffic only from the EC2 instance security group.

To remove the app without deleting the database, run:

```sh
sudo k3s kubectl delete -f https://raw.githubusercontent.com/myishaa/student-task-manager/feature/aws-kubernetes-deployment/k8s/hpa.yaml
sudo k3s kubectl delete -f https://raw.githubusercontent.com/myishaa/student-task-manager/feature/aws-kubernetes-deployment/k8s/service.yaml
sudo k3s kubectl delete -f https://raw.githubusercontent.com/myishaa/student-task-manager/feature/aws-kubernetes-deployment/k8s/deployment.yaml
```

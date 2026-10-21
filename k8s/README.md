# Kubernetes deployment

These manifests target the single-node K3s cluster described in the project plan. They prepare the app deployment; Terraform, the cluster, ECR image, and RDS database must exist first. The image and RDS endpoint below are placeholders until those resources are provisioned.

## Prepare configuration

Build and push the app image to ECR, then export the returned image URI and private RDS endpoint in your terminal:

```sh
export APP_IMAGE="<account-id>.dkr.ecr.<region>.amazonaws.com/student-task-manager:<tag>"
export RDS_ENDPOINT="<rds-endpoint>"
export DB_PASSWORD="<database-password>"
```

Create the namespace and runtime configuration. The password is supplied as a Kubernetes Secret and is not stored in Git:

```sh
kubectl apply -f k8s/namespace.yaml
kubectl -n student-task-manager create configmap task-manager-config \
  --from-literal=DB_URL="jdbc:mysql://${RDS_ENDPOINT}:3306/student_task_manager?useSSL=true&serverTimezone=UTC" \
  --from-literal=DB_USERNAME=task_manager \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n student-task-manager create secret generic task-manager-db \
  --from-literal=DB_PASSWORD="${DB_PASSWORD}" \
  --dry-run=client -o yaml | kubectl apply -f -
```

## Deploy

Replace the image placeholder in `deployment.yaml` with the exact ECR URI, then apply the workload:

```sh
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml
kubectl -n student-task-manager rollout status deployment/task-manager-api
kubectl -n student-task-manager get pods,service,hpa
```

Metrics Server must be available for CPU-based autoscaling. On K3s, verify it with `kubectl top pods -n student-task-manager` and `kubectl describe hpa task-manager-api -n student-task-manager`.

The NodePort uses port `30080`. Allow it only from the demo team's IP range in the EC2 security group. RDS should remain private and accept MySQL traffic only from the EC2 instance security group.

To remove the app without deleting the database, run:

```sh
kubectl delete -f k8s/hpa.yaml -f k8s/service.yaml -f k8s/deployment.yaml
```

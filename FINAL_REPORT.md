# Auto-Scaling and Self-Healing Cloud-Native Student Task Manager

## Abstract

This project packages a Spring Boot task-management API and browser dashboard with Docker, provisions a small AWS environment with Terraform, and runs the API on Kubernetes (K3s) on an EC2 host. A Horizontal Pod Autoscaler (HPA) adjusts API pod replicas in response to CPU demand. A live load run demonstrated scaling from one to two pods and back to one; deleting an API pod demonstrated Kubernetes replacement after the ECR pull credential was refreshed.

## Objective and architecture

The objective is to demonstrate container deployment, infrastructure automation, pod autoscaling, and pod recovery. The deployed path is:

```text
Browser → Kubernetes NodePort Service → Spring Boot API pod(s) → private RDS MySQL
                         K3s on one EC2 host
Terraform provisions the VPC, EC2, RDS, ECR, IAM, and network rules
Docker builds the API image; ECR stores the image
Systems Manager provides a secure shell and local port-forward without public SSH
```

The implemented application is a Student Task Manager with create, list, read, edit, complete, and delete operations. The planning synopsis described a student-records API; the implementation adapted the application domain and added a dashboard while retaining the cloud-native scaling and recovery objective.

## Implementation

- Spring Boot exposes `/api/tasks` and health endpoints. MySQL stores task data.
- Docker packages the application. Amazon ECR stores the image used by K3s.
- Terraform defines one EC2 host running K3s, private RDS MySQL, ECR, networking, and scoped access.
- Kubernetes runs one baseline API replica with startup, liveness, and readiness probes. The HPA targets 60% CPU and allows one to two replicas.
- Prometheus scrapes Spring Boot Actuator and kube-state-metrics. Grafana provides a private six-panel dashboard for API traffic/errors/latency, replica health, JVM heap, and process CPU.
- GitHub Actions tests and packages the application and validates Terraform. It does not currently publish an image to ECR or deploy it to the cluster.

## Verification and results

The detailed evidence and deployed UI screenshots are in [`evidence/DEMO_EVIDENCE.md`](evidence/DEMO_EVIDENCE.md). The reproducible sequence is in [`DEMO_RUNBOOK.md`](DEMO_RUNBOOK.md).

| Check | Result |
|---|---|
| Deployed readiness and UI | Readiness `UP`; UI HTTP 200 through SSM port-forward |
| Task persistence | A saved task appeared in the deployed UI and API response after page load |
| k6 load run | 30 maximum virtual users; 5,699 GET requests; 0% failures; p95 403.98 ms |
| HPA scale-up | CPU exceeded the 60% target; replicas increased from 1 to 2; both pods became Ready |
| HPA scale-down | After load stopped and the stabilization period elapsed, replicas returned to 1 |
| Pod replacement | Deleting a pod caused the Deployment to create a replacement that reached `1/1 Running` |
| Liveness recovery | A controlled Java-process pause triggered the liveness probe; Kubernetes restarted the container and the readiness endpoint returned `UP` |
| Live monitoring | Prometheus API, kube-state-metrics, and application targets all reported `up`; Grafana datasource health was `OK`; the six-panel dashboard loaded |
| CI validation | Application test/package/container build and Terraform format/validation jobs passed in [GitHub Actions run 33](https://github.com/myishaa/student-task-manager/actions/runs/40094738509) |

The HPA observed CPU as high as 319% of the pod CPU request during the load run. Its replica maximum was two, so it could not add more capacity than the single-node demo permits.

## Limitations and remaining work

- The cluster has one EC2 node. Pod recovery does not protect against EC2 host failure.
- The HPA is configured for one to two pods, below the original plan's two-to-four range, to fit the low-cost demo host.
- The self-healing exercise first encountered `ImagePullBackOff` because the ECR pull secret had expired. Refreshing the secret allowed the replacement to start. ECR tokens expire after 12 hours, so this demo currently needs periodic secret refresh.
- Prometheus keeps six hours of metrics in temporary pod storage; a Prometheus pod recreation loses its history. Grafana's database is temporary and its dashboard is recreated from the Kubernetes manifests.
- GitHub Actions validates and packages the app but does not automatically push and deploy it.
- Pod autoscaling changes pod capacity, not the fixed EC2 or RDS size. No AWS billing savings are claimed from this test.

## Conclusion

The deployed prototype demonstrates a browser-backed task application, containerized delivery, Terraform-defined AWS infrastructure, Kubernetes pod-level recovery, and CPU-based pod scaling. The live test observed both HPA scale-up and scale-down with no failed GET requests at the tested load. The results apply to this single-node demo and do not establish multi-node or host-level high availability.

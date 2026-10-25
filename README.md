# Student Task Manager

A stateless Spring Boot REST API for student tasks, backed by MySQL. The AWS demo runs on a single EC2 host with K3s and a private RDS database. Prometheus and Grafana monitor API and Kubernetes metrics inside the cluster. The repository's GitHub Actions workflow validates Terraform and tests/packages the application; automated AWS image publishing/deployment is not configured yet.

## Run locally

Requirements: Docker with Compose.

```sh
docker compose up --build
```

Open the student task manager at <http://localhost:8080>. The dashboard lets you add, edit, complete, filter, and delete tasks. The REST API is also available at <http://localhost:8080/api/tasks>. Stop the app with `Ctrl+C`; run `docker compose down` to stop and remove the containers. The named MySQL volume keeps local task data between restarts. Remove it with `docker compose down -v` when you want a clean database.

The default database password is only for local development. Set `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD` in your shell or a local `.env` file for local overrides. Never commit real credentials.

## API

The browser dashboard is served by Spring Boot from `src/main/resources/static/index.html`, so it uses the same origin as the API and needs no separate frontend service.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/tasks` | Create a task; status defaults to `TODO` |
| `GET` | `/api/tasks` | List tasks, optionally filter with `?status=IN_PROGRESS` |
| `GET` | `/api/tasks/{id}` | Get one task |
| `PUT` | `/api/tasks/{id}` | Replace task fields |
| `PATCH` | `/api/tasks/{id}/status` | Change task status |
| `DELETE` | `/api/tasks/{id}` | Delete a task |
| `GET` | `/actuator/health` | Liveness/readiness health |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

Task fields: `title` (required, up to 160 characters), `description` (optional, up to 2,000 characters), `dueDate` (optional ISO date), `priority` (`LOW`, `MEDIUM`, or `HIGH`), and `status` (`TODO`, `IN_PROGRESS`, or `DONE`).

Example:

```sh
curl -X POST http://localhost:8080/api/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title":"Review cloud computing notes","description":"Prepare for Friday","dueDate":"2026-10-24","priority":"HIGH"}'
```

## Run without Docker

Use Java 21, Maven 3.6.3+, and a reachable MySQL database. Configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, then run:

```sh
mvn spring-boot:run
```

## Kubernetes

The manifests define a one-replica baseline API Deployment, NodePort Service, health probes, resource requests, and a CPU-based Horizontal Pod Autoscaler configured for one to two replicas. The private Prometheus/Grafana setup and six-panel dashboard are in [`k8s/monitoring/`](k8s/monitoring/); use the SSM port-forward steps in [`DEMO_RUNBOOK.md`](DEMO_RUNBOOK.md) to view Grafana without exposing it publicly. Prometheus keeps six hours of metrics in temporary pod storage. See [`k8s/README.md`](k8s/README.md) for the ECR/RDS configuration steps. The app manifests require a provisioned cluster, RDS database, and ECR image first.

Terraform for the demo VPC, K3s EC2 host, private RDS database, ECR repository, and access rules is in [`infra/README.md`](infra/README.md). Review current regional pricing and the plan before applying it; AWS resources incur charges.

## Load test

Install [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) and start the app locally or point the script at a running deployment. The test creates a temporary task, reads it, marks it complete, then deletes it in each iteration:

```sh
k6 run load-tests/tasks.js
```

For a remote deployment or a different load level, set `BASE_URL` and `MAX_VUS` (the default is five virtual users):

```sh
BASE_URL=http://YOUR_DEMO_HOST:30080 MAX_VUS=5 k6 run load-tests/tasks.js
```

The run ramps up for 30 seconds, holds the configured user count for one minute, and ramps down for 30 seconds. It fails its thresholds if more than 1% of requests fail or p95 latency reaches 500 ms. Run load tests only against an environment you control, start with a low `MAX_VUS`, and do not treat a passing local result as proof that AWS autoscaling works; verify the HPA and available node capacity separately.

For the AWS presentation, see [`DEMO_RUNBOOK.md`](DEMO_RUNBOOK.md) for the SSM sessions, HPA observation steps, pod recovery demonstration, and a bounded read-only workload in [`load-tests/autoscaling.js`](load-tests/autoscaling.js). Recorded live results and UI screenshots are in [`evidence/DEMO_EVIDENCE.md`](evidence/DEMO_EVIDENCE.md). The implementation report is in [`FINAL_REPORT.md`](FINAL_REPORT.md).

## Design notes

- Each API instance is stateless and connects to the shared database.
- Hibernate creates/updates the local schema for the prototype. Replace this with versioned migrations before a production deployment.
- No user authentication or per-student task ownership is implemented in this first milestone.
- Database credentials supplied in Compose are development-only examples.

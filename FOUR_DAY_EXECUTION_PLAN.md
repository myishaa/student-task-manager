# CCD project: four-day execution plan

Based on: `CCD_Mini_Project_Synopsis copy.pdf` (all four pages).

**Project:** Auto-Scaling and Self-Healing Cloud-Native Application using Kubernetes, Docker, Terraform and AWS.

**Execution window:** Day 1–Day 4. If starting on 21 October 2026, finish on 24 October 2026. Shift the dates together if starting later.

**Planning assumption:** All three team members can contribute approximately 7–8 focused hours per day, AWS and GitHub access are available on Day 1, and this is a demonstrable academic prototype. The folder currently contains the synopsis only; implementation starts from scratch. The schedule is tight and depends on parallel ownership and daily integration.

## Recorded implementation status — 25 October 2026

The deployed prototype has evidence for the core pod-level scaling and recovery outcomes. See [`FINAL_REPORT.md`](FINAL_REPORT.md), [`DEMO_RUNBOOK.md`](DEMO_RUNBOOK.md), and [`evidence/DEMO_EVIDENCE.md`](evidence/DEMO_EVIDENCE.md).

- **Verified:** deployed task UI/API with RDS-backed tasks; Docker image in ECR; Terraform-managed AWS/K3s environment; GitHub Actions CI validation; HPA scale-up from one to two pods and scale-down to one under a 30-VU k6 run; Deployment replacement after pod deletion; liveness-probe-triggered container restart.
- **Scope adjustments:** the planned student-records API became a Student Task Manager UI/API. The cost-constrained deployment uses one EC2 node and an HPA range of one to two replicas, below the original two-to-four target.
- **Implemented after the original four-day window:** private Prometheus/Grafana monitoring with a six-panel API and Kubernetes dashboard. **Still unimplemented:** automatic image publishing/deployment from GitHub Actions. The single EC2 host does not recover from host failure.

## 1. Scope and architecture

Build one small student-records service with `POST /students`, `GET /students`, and `GET /students/{id}`. Use Spring Boot, MySQL, and stateless request handling. Omit a frontend, authentication system, extra business features, multi-node infrastructure, and production high availability from this four-day implementation.

Architecture:

```text
Developer push → GitHub Actions → build/test → Docker image → Amazon ECR
                                      ↓ deployment
                               AWS Systems Manager
                                      ↓
Users / k6 → Kubernetes Service → Spring Boot Pods (2–4) → RDS MySQL
                                  on one EC2 instance
                                      ↑
                     HPA + Metrics Server + health probes

Application / Kubernetes metrics → Prometheus → Grafana
Terraform → EC2, RDS, ECR, networking, security groups, IAM
```

Use a single-node K3s installation on EC2 to keep Kubernetes setup manageable. Use a Kubernetes Service with NodePort for the demo, avoiding ingress, DNS, and certificate work. Restrict demo access to the team's source IPs. Keep RDS private and allow database connections only from the application EC2 security group. Access Grafana through a tunnel or port forwarding.

Start with two application replicas so one Pod can be removed while another serves requests. Set HPA bounds to 2–4 replicas and an initial CPU utilization target of 60%; tune after observing the workload. Define CPU requests because CPU-based HPA depends on them. Confirm Metrics Server works before testing HPA.

Select EC2 capacity after checking that four application Pods, Kubernetes, and the monitoring stack fit in memory and CPU. Do not assume the smallest instance or AWS free-tier allowances will support this stack. Keep Prometheus retention short and install only the monitoring components needed for the demo.

**Reliability boundary:** This design demonstrates recovery from application Pod failure. A single EC2 node and a single database remain failure points; node and database failure recovery are outside this prototype's demonstration.

## 2. Ownership

These assignments are a starting allocation; swap names if existing skills suggest a better fit.

| Team member | Primary ownership | Required handoff |
|---|---|---|
| Myisha Siddiqui | Spring Boot, database schema, application tests, Docker | Runnable image and documented endpoints by end of Day 1 |
| Manan Bhasin | Terraform, AWS networking/IAM, EC2/K3s, RDS | Reachable cluster and database by midday Day 2 |
| Rishabh Raj | GitHub Actions, Kubernetes manifests, monitoring, k6 | Initial deployment manifests on Day 1; automated deployment on Day 2 |
| Everyone | Integration, troubleshooting, evidence, report, rehearsal | A working integrated build at each daily checkpoint |

Use one repository, short-lived branches, and small reviewed merges. Share the image name, application port, environment variable names, health URLs, database name, and Service name during the first hour. Keep generated credentials and Terraform state out of Git.

## 3. Day 1 — Build the application and prepare infrastructure

**Goal:** A tested container locally, with infrastructure and deployment code ready for integration.

| Time block | Step-by-step work | Owner |
|---|---|---|
| Hour 1 | Create the repository and task board. Confirm AWS credentials, region, permissions, spending limit, GitHub Actions access, Docker, Java/build tool, Terraform, AWS CLI, and kubectl. Agree endpoint contracts and environment variables. | Everyone |
| Hours 2–4 | Implement the student entity, repository, service/controller, request validation, and error responses. Connect to local MySQL using Docker Compose. Add Actuator health and Prometheus metrics. | Myisha |
| Hours 2–4 | Write Terraform for VPC/subnets, EC2, private RDS MySQL, security groups, ECR, and the IAM access needed for image pulls and Systems Manager. Add K3s bootstrap configuration. | Manan |
| Hours 2–4 | Prepare Deployment, Service, Secret template, health probes, resource requests/limits, and HPA manifests. Draft the GitHub Actions test/build/push workflow. | Rishabh |
| Hours 5–6 | Test create/list/read operations and persistence. Build a multi-stage Docker image and run it against MySQL. Confirm configuration comes from environment variables. | Myisha |
| Hours 5–6 | Run Terraform formatting and validation, inspect the plan, and start provisioning within the agreed budget. Check the node architecture matches the container image architecture. | Manan |
| Hours 5–6 | Prepare k6 scripts for smoke, steady-load, and traffic-spike scenarios. Check Spring Actuator metric names and prepare dashboard queries. | Rishabh |
| Hours 7–8 | Merge the initial implementation, run the container together, review Terraform and manifests, and record setup instructions. Resolve application/image blockers before ending the day. | Everyone |

**Day 1 completion gate**

- A student can be created and retrieved through the containerized API.
- Records survive an application restart while the database remains running.
- Tests cover API behavior, invalid input, and database interaction.
- Terraform validates, its plan is reviewed, and provisioning has started or completed.
- Kubernetes manifests and the initial workflow are committed.

**Expected files:** `app/`, `Dockerfile`, `compose.yaml`, `infra/`, `k8s/`, `.github/workflows/`, `load-tests/`, `.gitignore`, `README.md`.

## 4. Day 2 — Complete cloud deployment and CI/CD

**Goal:** A code push automatically updates the application running on AWS.

| Time block | Step-by-step work | Owner |
|---|---|---|
| Hours 1–2 | Finish EC2/RDS provisioning. Verify K3s node readiness, Systems Manager connectivity, private database connectivity, and ECR image-pull access. | Manan |
| Hours 1–2 | Finish application fixes and expose separate liveness/readiness health groups. Liveness should check application health; readiness should prevent traffic reaching a Pod before it can serve requests. | Myisha |
| Hours 1–2 | Finish the workflow: test, build, tag with commit SHA, authenticate to AWS using GitHub OIDC, and push to ECR. | Rishabh |
| Hours 3–4 | Create runtime database Secrets securely, deploy two replicas, and expose the Service. Configure registry authentication for new image pulls and confirm it remains usable after credentials refresh. | Manan + Rishabh |
| Hours 3–4 | Verify API operations through the cloud endpoint and persistence through application replacement. Confirm the database schema is initialized consistently. | Myisha |
| Hours 5–6 | Add deployment through Systems Manager to run the cluster update on EC2. Use the SHA-tagged image and wait for rollout success. Restrict concurrent deployments. | Rishabh + Manan |
| Hours 5–6 | Configure rolling updates, suitable probe startup timings, and resource requests/limits. Confirm a new Pod becomes ready before an old one is removed. | Myisha + Rishabh |
| Hours 7–8 | Push a small visible change, observe the automated rollout, verify the running image SHA, and rehearse rollback to the prior image. Install or verify Metrics Server and the HPA. | Everyone |

**Day 2 completion gate**

- Terraform-created EC2 and RDS support a working cloud API.
- Two application Pods are ready and share the same persistent database.
- A GitHub push successfully tests, builds, pushes, and deploys an image.
- Deployment failure is visible in the workflow; rollback is documented and exercised.
- `kubectl top pods` returns CPU/memory metrics and HPA shows valid CPU readings.

**Evidence:** successful Actions run, cloud API response, ready Pods, deployed image SHA, and Terraform resource summary. Exclude credentials and sensitive Terraform output from screenshots.

## 5. Day 3 — Demonstrate scaling, recovery, and monitoring

**Goal:** Obtain repeatable evidence for the project's main claims.

| Time block | Step-by-step work | Owner |
|---|---|---|
| Hours 1–2 | Deploy a minimal Prometheus/Grafana setup. Scrape application metrics and collect Pod CPU and desired/ready replica metrics. Add panels for CPU, Pod count, request rate, errors, and latency. | Rishabh |
| Hours 1–2 | Verify Metrics Server, HPA, node headroom, and application resource requests. Fix Pending Pods or memory pressure before load testing. | Manan |
| Hours 1–2 | Verify readiness/liveness behavior and prepare test data. Ensure every replica can read records created through another replica. | Myisha |
| Hours 3–4 | Run k6 from a separate machine: baseline, gradual ramp, sustained spike, then low traffic. Record concurrency, duration, CPU, replicas, response time, and errors. | Everyone |
| Hours 3–4 | Adjust load and CPU requests only as evidence requires. If ordinary CRUD is too light to trigger CPU-based HPA, use a documented, bounded demo-only CPU workload; keep it disabled by default and restrict access. | Myisha + Manan |
| Hours 5–6 | Repeat the traffic experiment. Capture scale-up and scale-down, allowing for the HPA's configured stabilization period. Save raw k6 output and matching dashboard timestamps. | Rishabh |
| Hours 5–6 | Under steady traffic, delete one application Pod and record replacement, readiness, and failed requests. Separately exercise a controlled unhealthy process to verify liveness-triggered restart. | Myisha + Manan |
| Hours 7–8 | Repeat the full demonstration, fix failures, export the dashboard, and save the exact commands and evidence. Freeze feature development. | Everyone |

**Day 3 completion gate**

- Sustained load makes HPA increase replicas above two; reduced load eventually returns replicas to two.
- Grafana displays CPU, replica count, and traffic over the same test interval.
- A deleted Pod is replaced automatically while the remaining Pod serves traffic.
- A controlled unhealthy process is restarted by the health probe.
- k6 results include actual error and latency measurements. Claim “zero observed downtime” only if the recorded test shows zero failed requests; otherwise report the result and fix/retest where possible.

Deleting a Pod demonstrates Deployment reconciliation; it does not by itself prove that a liveness probe works. Keep both demonstrations in the evidence.

## 6. Day 4 — Validate, calculate costs, and prepare submission

**Goal:** A stable, documented project with a rehearsed demonstration and defensible conclusions.

| Time block | Step-by-step work | Owner |
|---|---|---|
| Hours 1–2 | Run the final acceptance checklist. Resolve only issues that affect required outcomes. Tag a known-good release and record its image SHA. | Everyone |
| Hours 3–4 | Calculate infrastructure costs using current official AWS pricing for the chosen region and resource configuration. State the date, usage hours, storage, and excluded/assumed charges. | Manan |
| Hours 3–4 | Complete the report: problem, architecture, implementation, tests, measured results, limitations, and conclusion. | Myisha |
| Hours 3–4 | Finish README setup/deploy/rollback/cleanup commands, dashboard export, and a concise slide deck. | Rishabh |
| Hours 5–6 | Rehearse the complete demo. Record a backup video and collect legible screenshots aligned with each expected outcome. Check a fresh reviewer can follow the README. | Everyone |
| Hours 7–8 | Reserve this block for final fixes and submission packaging. Verify all links and artifacts. Keep cloud resources only as long as needed for evaluation, then follow the documented teardown procedure. | Everyone |

### Cost comparison: report the distinction accurately

With one fixed EC2 instance and one RDS instance, reducing application Pods usually leaves the same provisioned compute/database bill. This prototype can show improved utilization and less reserved application capacity, but cannot demonstrate lower EC2 instance charges from Pod scaling alone.

Include two clearly labeled comparisons:

1. **Implemented architecture:** fixed four Pods versus HPA with two to four Pods, on the same EC2/RDS configuration. Report observed replica-hours, CPU/memory utilization, and estimated infrastructure cost. Explain why infrastructure cost is approximately unchanged.
2. **Projected infrastructure-scaling scenario:** estimate savings if worker capacity could also scale with demand. State that this scenario is modeled and is not implemented or measured in the single-node demo. Include enough fixed cluster/system capacity to make the model plausible.

Use:

```text
Monthly infrastructure estimate = EC2 hours × EC2 rate
                                + RDS hours × RDS rate
                                + storage and applicable network/registry charges

Modeled savings = fixed-capacity estimate − variable-capacity estimate
Modeled savings % = modeled savings / fixed-capacity estimate × 100
```

Do not convert fewer Pod-hours directly into AWS billing savings, assume free-tier eligibility, or invent savings percentages. If the evaluator requires actual billing savings, the architecture and scope need additional infrastructure scaling work beyond this four-day baseline.

## 7. Final acceptance checklist

| Synopsis outcome | Acceptance test | Submission evidence |
|---|---|---|
| Automated infrastructure | Terraform provisions the required AWS resources; validation passes and state is retained securely | Terraform source, setup steps, sanitized resource summary |
| Automated build and deployment | A new commit produces a new image and successful cluster rollout | Workflow source, successful run, deployed SHA |
| Automatic scale-up/down | k6 triggers a replica increase and subsequent return to minimum | Load script, raw results, HPA output, dashboard capture |
| Automatic recovery | Pod replacement and health-triggered restart happen without manual redeployment | Commands, events/restart counts, continuous traffic results |
| Live monitoring | CPU, replicas, and request rate are visible during the same experiment | Dashboard JSON and screenshots/video |
| Cost analysis | Calculations reflect the implemented architecture and separately label projections | Spreadsheet or report table with pricing sources and assumptions |

## 8. Demo sequence: approximately 10–12 minutes

1. Explain the architecture and show Terraform-managed resources (1 minute).
2. Create and retrieve a student record through the cloud API (1 minute).
3. Show the successful pipeline and deployed image version; demonstrate a push if time permits (1–2 minutes).
4. Start load and show CPU/traffic increasing, followed by additional Pods (2–3 minutes).
5. Delete one Pod under steady traffic; show continued responses and automatic replacement (1–2 minutes).
6. Stop load and show scale-down when stabilization permits; use the recorded full run if it exceeds the demo slot (1 minute).
7. Explain measured results, actual cost behavior, projected savings, and the single-node limitation (1–2 minutes).

Keep the controlled liveness failure demonstration in the recorded evidence if the live slot is too short.

## 9. Schedule protection and fallback decisions

- **AWS access blocked on Day 1:** continue local application, containers, manifests, pipeline build, and load scripts. A local demo is a fallback, but does not satisfy the required AWS/Terraform outcome; record that gap explicitly.
- **Cloud integration incomplete by midday Day 2:** stop all optional work and resolve networking, image pulls, secrets, and database access together. Do not defer the first cloud deployment to Day 3.
- **HPA cannot scale:** check resource requests, valid metrics, available node capacity, and whether the load is actually CPU-bound before changing thresholds.
- **Monitoring consumes too much capacity:** shorten retention and remove unnecessary collectors. Resize within the budget if required, and update Terraform to match.
- **Recovery causes failed requests:** inspect readiness, Service endpoints, graceful shutdown, remaining replica capacity, and probe timings. Report the measured result until a successful rerun exists.
- **Time runs short:** cut dashboard polish, extra endpoints, custom UI, and extra charts. Preserve Terraform, CI/CD, HPA, recovery tests, basic monitoring, and accurate documentation.

## 10. Final deliverables

- GitHub repository containing the application, Dockerfile, Terraform, Kubernetes manifests, GitHub Actions workflow, and k6 scripts.
- README with prerequisites, configuration, deployment, verification, rollback, demo, and cleanup steps.
- Grafana dashboard export, raw test results, screenshots, and backup demo video.
- Final report, cost comparison with source links, and presentation slides.
- A tagged working release and a checklist showing which outcomes passed and any remaining limitations.

**Completion rule:** Finish implementation by the end of Day 3. Use Day 4 for verification, evidence, documentation, and submission.

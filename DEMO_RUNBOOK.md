# Cloud-native project demo runbook

This runbook separates what the deployed demo can show from what must be observed during the presentation. The current AWS demo is intentionally small: one EC2 host running K3s, a private RDS MySQL database, one baseline API pod, and an HPA capped at two pods.

## Verified live result

On 2026-10-25, a 30-VU read-only k6 run against the Sydney deployment completed 5,699 requests with 0% failed requests and 404 ms p95 latency. The HPA observed CPU above its 60% target (up to 319%), scaled from one to two replicas, and returned to one replica after the configured scale-down stabilization period. Both pods were Ready at peak, and the final deployment returned to 1/1 Ready. Prometheus and Grafana are deployed in the cluster; the API, kube-state-metrics, and Prometheus scrape targets report healthy. This is measured evidence for pod autoscaling and recovery on the single EC2 node; it does not demonstrate EC2 host failover.

## Before the demo

- Confirm AWS CLI credentials still work for account 957123506131 and region ap-southeast-2. The last local AWS identity check could not complete; refresh sign-in before the demo if needed.
- Install k6 on the Mac if it is not already available: brew install k6.
- The ECR pull secret expires after 12 hours. Refresh it using the command in k8s/README.md before a deployment or image pull.
- Prepare three Terminal windows: app port-forward, EC2 Kubernetes shell, and k6. No Grafana tunnel is used for this presentation.

In Terminal 1, start the app port-forward and leave it running:

~~~sh
PATH="/private/tmp/ssm-plugin-install/sessionmanager-bundle/bin:$PATH" AWS_PROFILE=default aws ssm start-session \
  --target i-0cd822e9d1ba2d0dc \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["30080"],"localPortNumber":["30080"]}' \
  --region ap-southeast-2
~~~

Open a second SSM session in Terminal 2:

~~~sh
PATH="/private/tmp/ssm-plugin-install/sessionmanager-bundle/bin:$PATH" AWS_PROFILE=default aws ssm start-session \
  --target i-0cd822e9d1ba2d0dc \
  --region ap-southeast-2
~~~

Kubernetes commands below belong in Terminal 2, where the prompt is sh-5.2$. Do not run them at the Mac prompt. In Terminal 3, run k6 from the project folder only after the tunnel is responding.

Open http://localhost:30080 for the app. For the monitoring portion, open evidence/monitoring-summary.svg or the evidence record. It is a static summary of recorded Prometheus values, not a live Grafana screenshot.

## Demo sequence

1. **Show the app:** open <http://localhost:30080>, add a task, mark it complete, and refresh. Explain that the browser calls the Spring Boot API and the API stores task data in MySQL on private RDS.

2. **Show the cloud workload:** in Terminal 2 run:

   ```sh
   sudo k3s kubectl get nodes
   sudo k3s kubectl get deployment,pods,service,hpa -n student-task-manager
   sudo k3s kubectl top pods -n student-task-manager
   ```

   Explain that Terraform defines the AWS infrastructure, Docker packages the API, K3s runs it, and the HPA watches CPU. The current HPA is configured for 1–2 API pods with a 60% CPU target.

3. **Show saved monitoring evidence:** open evidence/monitoring-summary.svg. Explain that its values are reconstructed from the Prometheus query results recorded on 25 October 2026; it is not a Grafana screenshot or a live view. Grafana and Prometheus were deployed and checked, but the live Grafana walkthrough is excluded from this presentation.

4. **Show actual autoscaling:** in Terminal 2 start watching:

   ```sh
   sudo k3s kubectl get hpa,pods -n student-task-manager -w
   ```

   In Terminal 3 on the Mac, from the project directory, run the bounded read-only workload through the tunnel:

   ```sh
   cd ~/Desktop/ccd
   BASE_URL=http://localhost:30080 MAX_VUS=30 k6 run load-tests/autoscaling.js
   ```

   The script ramps up to at most 30 virtual users for a short run and only reads the task list; it does not create or modify tasks. Watch for HPA `TARGETS` to rise and `REPLICAS` to change from 1 to 2. Scaling is workload and capacity dependent, so show the HPA configuration if CPU stays below target; do not claim a scale-up unless the second pod appears. Stop the watch with Ctrl+C after k6 finishes.

5. **Show scale-down:** after k6 ends, watch the HPA and pods again. The HPA has a 180-second scale-down stabilization window, so allow several minutes for replicas to return to one. Capture the HPA output before and after the run.

6. **Show pod recovery:** only after the load test is finished, get the current pod name and delete that one pod:

   ```sh
   POD=$(sudo k3s kubectl get pods -n student-task-manager -o jsonpath='{.items[0].metadata.name}')
   sudo k3s kubectl delete pod "$POD" -n student-task-manager
   sudo k3s kubectl get pods -n student-task-manager -w
   ```

   Show the replacement reaching `1/1 Running`. With one baseline replica, requests may pause while the pod starts. This demonstrates Deployment reconciliation after a pod is deleted.

7. **Show liveness recovery evidence:** the controlled liveness-probe test has been completed and recorded in [`evidence/DEMO_EVIDENCE.md`](evidence/DEMO_EVIDENCE.md). If the tested pod still exists, show its restart count and event:

   ```sh
   sudo k3s kubectl describe pod task-manager-api-8457f74988-8w6hl -n student-task-manager
   ```

   Look for `Restart Count: 1` and `Container api failed liveness probe, will be restarted`. Use the saved evidence record if Kubernetes has since replaced that pod and its event history is gone.

## Describe the limits accurately

- This demo shows pod-level scaling and recovery on a single EC2 host. It does not recover automatically from EC2 host failure.
- HPA configuration is not evidence of autoscaling by itself. Save the before/during/after HPA and pod output from a run where a second replica actually appears.
- Prometheus keeps six hours of metrics in temporary pod storage. Its time series are lost if the pod is recreated; this demo does not use persistent monitoring storage. The static monitoring graphic is a saved summary, not a live Grafana view.
- GitHub Actions CI validates and packages the project. A manual AWS release workflow is prepared, but it needs a scoped GitHub OIDC role and repository environment/variables before it can run; it is not triggered by pushes.
- One API pod is configured at baseline to fit the small demo host. The current HPA maximum is two replicas, which is lower than the original plan's 2–4 target.
- AWS resources continue running and consuming credits after the terminal sessions close.

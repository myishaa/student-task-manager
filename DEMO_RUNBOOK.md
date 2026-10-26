# Cloud-native project demo runbook

This runbook separates what the deployed demo can show from what must be observed during the presentation. The current AWS demo is intentionally small: one EC2 host running K3s, a private RDS MySQL database, one baseline API pod, and an HPA capped at two pods.

The application now includes a browser-based Demo Console at `/demo.html`. Once the updated image is deployed and the application NodePort is reachable, it combines the task app, live Prometheus/Kubernetes status, a bounded browser load generator, and recorded recovery evidence on one page. The terminal sequence below remains the operator fallback and is still useful when verifying a deployment before presenting.

## Recommended browser-only demo

The current AWS demo URL is <http://32.236.242.165:30080/demo.html>. Its EC2 security-group rule allows port 30080 only from the demo network's current public IP. No SSM, kubectl, k6 or Grafana terminal is needed while presenting from that network.

1. Open the Demo Console and confirm the top-right badge says **Live AWS deployment**.
2. Explain the request path in section 1: browser → Kubernetes Service → Spring Boot pod → private RDS MySQL.
3. In section 2, show the live readiness, ready pods, HPA replica range and terminal-style Kubernetes output. The page refreshes these values every five seconds through the private Prometheus service.
4. In section 3, press **Start 90-second load**. The browser sends only `GET /api/tasks` requests. Watch request rate and HPA replicas on the same page. Do not claim a new scale-up unless the live replica count changes; the recorded 1→2→1 run remains visible in section 5.
5. In section 4, create a task, mark it complete and refresh the embedded app. Explain that persistence after refresh demonstrates the RDS data path.
6. Finish with the recorded pod replacement and liveness-restart evidence, then state the single-node limitation shown at the bottom of the page.

If the browser URL stops opening after changing Wi-Fi networks, edit the EC2 `student-task-manager-app` security group's existing TCP 30080 rule and set its source to **My IP** from the presentation network. The EC2 public address can also change if the instance is replaced. Keep RDS and Grafana private.

## Verified live result

On 2026-10-25, a 30-VU read-only k6 run against the Sydney deployment completed 5,699 requests with 0% failed requests and 404 ms p95 latency. The HPA observed CPU above its 60% target (up to 319%), scaled from one to two replicas, and returned to one replica after the configured scale-down stabilization period. Both pods were Ready at peak, and the final deployment returned to 1/1 Ready. Prometheus and Grafana are deployed in the cluster; the API, kube-state-metrics, and Prometheus scrape targets report healthy. This is measured evidence for pod autoscaling and recovery on the single EC2 node; it does not demonstrate EC2 host failover.

## Before the demo

- Confirm AWS CLI credentials still work for account 957123506131 and region ap-southeast-2 with `AWS_PROFILE=default aws sts get-caller-identity`. If the session expired, sign in again before the demo.
- Install k6 on the Mac if it is not already available: brew install k6.
- The ECR pull secret expires after 12 hours. Refresh it using the command in k8s/README.md before a deployment or image pull.
- Prepare four Terminal windows: app port-forward, EC2 Kubernetes shell, k6, and a private Grafana SSM tunnel.

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

Kubernetes commands below belong in Terminal 2, where the prompt is sh-5.2$. Do not run them at the Mac prompt. In Terminal 3, run k6 from the project folder only after the app tunnel is responding.

In Terminal 2, get the current Grafana ClusterIP:

~~~sh
sudo k3s kubectl get service grafana -n student-task-manager-monitoring -o wide
~~~

In Terminal 4, start a private SSM port-forward to that ClusterIP. Replace GRAFANA_CLUSTER_IP with the value shown in Terminal 2, then leave the session running:

~~~sh
PATH="/private/tmp/ssm-plugin-install/sessionmanager-bundle/bin:$PATH" AWS_PROFILE=default aws ssm start-session \
  --target i-0cd822e9d1ba2d0dc \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters '{"host":["GRAFANA_CLUSTER_IP"],"portNumber":["3000"],"localPortNumber":["3000"]}' \
  --region ap-southeast-2
~~~

Open <http://localhost:30080> for the app and <http://localhost:3000/d/student-task-manager-ops/student-task-manager-operations> for the live Grafana dashboard. Grafana is ClusterIP-only; this SSM tunnel does not expose it publicly. Anonymous access is configured as Viewer for this demo. If the Grafana Service is recreated, query its new ClusterIP in Terminal 2 and update the tunnel command.

### If Grafana does not open

The Grafana SSM tunnel ends when its Terminal 4 session closes or expires. Check it from another Mac terminal:

~~~sh
curl --fail --silent --show-error http://localhost:3000/api/health
~~~

A working tunnel returns JSON with `"database": "ok"`. If the connection is refused or times out, keep Terminal 2 connected, query the Grafana Service ClusterIP again, and restart the Terminal 4 port-forward above with that address. Confirm the Grafana pod is `1/1 Running` with `sudo k3s kubectl get pods -n student-task-manager-monitoring`. If the health endpoint works but the dashboard page does not, open the full dashboard URL shown above.

## Demo sequence

1. **Show the app:** open <http://localhost:30080>, add a task, mark it complete, and refresh. Explain that the browser calls the Spring Boot API and the API stores task data in MySQL on private RDS.

2. **Show the cloud workload:** in Terminal 2 run:

   ```sh
   sudo k3s kubectl get nodes
   sudo k3s kubectl get deployment,pods,service,hpa -n student-task-manager
   sudo k3s kubectl top pods -n student-task-manager
   ```

   Explain that Terraform defines the AWS infrastructure, Docker packages the API, K3s runs it, and the HPA watches CPU. The current HPA is configured for 1–2 API pods with a 60% CPU target.

3. **Show live monitoring:** open <http://localhost:3000/d/student-task-manager-ops/student-task-manager-operations>. Point out request rate, server errors, average request duration, ready/desired replicas, JVM heap, and Spring Boot CPU. It refreshes every 15 seconds from Prometheus. The saved evidence/monitoring-summary.svg is an offline backup if the AWS tunnel fails.

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
- Prometheus keeps six hours of metrics in temporary pod storage. Its time series are lost if the pod is recreated; this demo does not use persistent monitoring storage. The static monitoring graphic is an offline backup; the live dashboard is reached through the private SSM port-forward described above.
- GitHub Actions CI validates and packages the project. A manual AWS release workflow is prepared, but it needs a scoped GitHub OIDC role and repository environment/variables before it can run; it is not triggered by pushes.
- One API pod is configured at baseline to fit the small demo host. The current HPA maximum is two replicas, which is lower than the original plan's 2–4 target.
- AWS resources continue running and consuming credits after the terminal sessions close.

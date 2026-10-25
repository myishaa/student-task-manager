# Presenter notes (about 7 minutes)

Open PRESENTATION_SLIDES.html in a browser. Use the left/right arrow keys. For a handout, print from the browser and choose Save as PDF. The deck intentionally has no live Grafana step.

1. **Title (20 sec):** “This project is a Student Task Manager used to demonstrate autoscaling and recovery of a containerized application.”
2. **Motivation (45 sec):** Explain reproducible infrastructure and automatic replacement of failed pods. Mention the task app is the sample workload.
3. **Architecture (60 sec):** Follow the browser-to-API-to-RDS path. Terraform provisions the AWS foundation; Docker builds the image; K3s runs the pods.
4. **Demonstration (60 sec):** Create and complete a task in the browser, refresh it, then show the Kubernetes objects and HPA. Run the read-only k6 test only if time and AWS sessions allow.
5. **Monitoring evidence (40 sec):** Show the static summary. State clearly that the displayed values are a recorded Prometheus snapshot, not a live Grafana screenshot. Grafana itself is omitted from the live demo.
6. **Results (60 sec):** The 30-VU run generated 5,699 GET requests, 0% failures and 403.98 ms p95. HPA moved 1→2→1; pod deletion and a liveness failure both recovered.
7. **Cost and limitations (60 sec):** Give the $47.31/month modeled estimate as a list-price estimate, not the AWS invoice. HPA pod changes do not lower the EC2 or RDS hourly charges. One EC2 host is a single point of failure.

Close by distinguishing what was demonstrated from future work: multi-node host recovery, automatic image publication/deployment after OIDC role setup, durable monitoring storage, and authentication.

If the AWS session expires, do not spend the presentation slot troubleshooting sign-in. Present the saved screenshots and evidence record, and state which results were captured earlier.

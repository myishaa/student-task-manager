package com.ccd.studenttaskmanager.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Service
public class DemoMonitoringService {

    private static final String NAMESPACE = "student-task-manager";
    private static final String DEPLOYMENT = "task-manager-api";
    private static final String HPA = "task-manager-api";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String prometheusUrl;

    public DemoMonitoringService(ObjectMapper objectMapper,
                                 @Value("${demo.prometheus-url}") String prometheusUrl) {
        this.objectMapper = objectMapper;
        this.prometheusUrl = prometheusUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public DemoStatus snapshot() {
        Instant checkedAt = Instant.now();
        try {
            Map<String, CompletableFuture<JsonNode>> queries = new LinkedHashMap<>();
            queries.put("requestRate", query("sum(rate(http_server_requests_seconds_count[1m]))"));
            queries.put("errorRate", query("sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[1m])) or vector(0)"));
            queries.put("duration", query("sum(rate(http_server_requests_seconds_sum[1m])) / clamp_min(sum(rate(http_server_requests_seconds_count[1m])), 1e-9)"));
            queries.put("processCpu", query("max(process_cpu_usage)"));
            queries.put("heap", query("sum(jvm_memory_used_bytes{area=\"heap\"})"));
            queries.put("available", query("kube_deployment_status_replicas_available{namespace=\"" + NAMESPACE + "\",deployment=\"" + DEPLOYMENT + "\"}"));
            queries.put("desired", query("kube_deployment_spec_replicas{namespace=\"" + NAMESPACE + "\",deployment=\"" + DEPLOYMENT + "\"}"));
            queries.put("hpaCurrent", query("kube_horizontalpodautoscaler_status_current_replicas{namespace=\"" + NAMESPACE + "\",horizontalpodautoscaler=\"" + HPA + "\"}"));
            queries.put("hpaDesired", query("kube_horizontalpodautoscaler_status_desired_replicas{namespace=\"" + NAMESPACE + "\",horizontalpodautoscaler=\"" + HPA + "\"}"));
            queries.put("hpaMin", query("kube_horizontalpodautoscaler_spec_min_replicas{namespace=\"" + NAMESPACE + "\",horizontalpodautoscaler=\"" + HPA + "\"}"));
            queries.put("hpaMax", query("kube_horizontalpodautoscaler_spec_max_replicas{namespace=\"" + NAMESPACE + "\",horizontalpodautoscaler=\"" + HPA + "\"}"));
            queries.put("runningPods", query("kube_pod_status_phase{namespace=\"" + NAMESPACE + "\",pod=~\"task-manager-api-.*\",phase=\"Running\"} == 1"));
            queries.put("readyPods", query("kube_pod_status_ready{namespace=\"" + NAMESPACE + "\",pod=~\"task-manager-api-.*\",condition=\"true\"} == 1"));
            queries.put("restarts", query("sum by (pod) (kube_pod_container_status_restarts_total{namespace=\"" + NAMESPACE + "\",pod=~\"task-manager-api-.*\",container=\"api\"})"));

            CompletableFuture.allOf(queries.values().toArray(CompletableFuture[]::new))
                    .orTimeout(5, TimeUnit.SECONDS)
                    .join();

            Map<String, JsonNode> results = new LinkedHashMap<>();
            queries.forEach((name, future) -> results.put(name, future.join()));

            return new DemoStatus(
                    checkedAt,
                    true,
                    "Live data from Prometheus in the K3s cluster",
                    scalar(results.get("requestRate")),
                    scalar(results.get("errorRate")),
                    multiply(scalar(results.get("duration")), 1000),
                    multiply(scalar(results.get("processCpu")), 100),
                    longValue(results.get("heap")),
                    integer(results.get("available")),
                    integer(results.get("desired")),
                    integer(results.get("hpaCurrent")),
                    integer(results.get("hpaDesired")),
                    integer(results.get("hpaMin")),
                    integer(results.get("hpaMax")),
                    pods(results.get("runningPods"), results.get("readyPods"), results.get("restarts"))
            );
        } catch (RuntimeException error) {
            return new DemoStatus(
                    checkedAt,
                    false,
                    "Live cluster metrics are unavailable. Recorded evidence remains visible below.",
                    null, null, null, null, null, null, null, null, null, 1, 2, List.of()
            );
        }
    }

    private CompletableFuture<JsonNode> query(String promql) {
        String encoded = URLEncoder.encode(promql, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(prometheusUrl + "/api/v1/query?query=" + encoded))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new CompletionException(new IllegalStateException(
                                "Prometheus returned HTTP " + response.statusCode()));
                    }
                    try {
                        JsonNode root = objectMapper.readTree(response.body());
                        if (!"success".equals(root.path("status").asText())) {
                            throw new IllegalStateException("Prometheus query did not succeed");
                        }
                        return root.path("data").path("result");
                    } catch (Exception parseError) {
                        throw new CompletionException(parseError);
                    }
                });
    }

    private Double scalar(JsonNode result) {
        if (result == null || !result.isArray() || result.isEmpty()) {
            return null;
        }
        JsonNode value = result.get(0).path("value");
        if (!value.isArray() || value.size() < 2) {
            return null;
        }
        try {
            return Double.valueOf(value.get(1).asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer integer(JsonNode result) {
        Double value = scalar(result);
        return value == null ? null : (int) Math.round(value);
    }

    private Long longValue(JsonNode result) {
        Double value = scalar(result);
        return value == null ? null : Math.round(value);
    }

    private Double multiply(Double value, double factor) {
        return value == null ? null : value * factor;
    }

    private List<DemoStatus.PodStatus> pods(JsonNode runningResult,
                                            JsonNode readyResult,
                                            JsonNode restartResult) {
        Map<String, Boolean> ready = booleanByPod(readyResult);
        Map<String, Integer> restarts = integerByPod(restartResult);
        List<DemoStatus.PodStatus> pods = new ArrayList<>();
        if (runningResult != null && runningResult.isArray()) {
            runningResult.forEach(sample -> {
                String pod = sample.path("metric").path("pod").asText();
                if (!pod.isBlank()) {
                    pods.add(new DemoStatus.PodStatus(
                            pod,
                            ready.getOrDefault(pod, false),
                            restarts.getOrDefault(pod, 0)
                    ));
                }
            });
        }
        pods.sort(Comparator.comparing(DemoStatus.PodStatus::name));
        return pods;
    }

    private Map<String, Boolean> booleanByPod(JsonNode result) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        if (result != null && result.isArray()) {
            result.forEach(sample -> {
                String pod = sample.path("metric").path("pod").asText();
                Double value = sampleValue(sample);
                if (!pod.isBlank()) {
                    values.put(pod, value != null && value >= 1);
                }
            });
        }
        return values;
    }

    private Map<String, Integer> integerByPod(JsonNode result) {
        Map<String, Integer> values = new LinkedHashMap<>();
        if (result != null && result.isArray()) {
            result.forEach(sample -> {
                String pod = sample.path("metric").path("pod").asText();
                Double value = sampleValue(sample);
                if (!pod.isBlank() && value != null) {
                    values.put(pod, (int) Math.round(value));
                }
            });
        }
        return values;
    }

    private Double sampleValue(JsonNode sample) {
        JsonNode value = sample.path("value");
        if (!value.isArray() || value.size() < 2) {
            return null;
        }
        try {
            return Double.valueOf(value.get(1).asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

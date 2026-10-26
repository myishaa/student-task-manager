package com.ccd.studenttaskmanager.demo;

import java.time.Instant;
import java.util.List;

public record DemoStatus(
        Instant checkedAt,
        boolean live,
        String message,
        Double requestRate,
        Double errorRate,
        Double meanDurationMilliseconds,
        Double processCpuPercent,
        Long jvmHeapBytes,
        Integer availableReplicas,
        Integer desiredReplicas,
        Integer hpaCurrentReplicas,
        Integer hpaDesiredReplicas,
        Integer hpaMinReplicas,
        Integer hpaMaxReplicas,
        List<PodStatus> pods
) {
    public record PodStatus(String name, boolean ready, int restarts) {
    }
}

package com.ccd.studenttaskmanager.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoMonitoringService monitoringService;

    public DemoController(DemoMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/status")
    public DemoStatus status() {
        return monitoringService.snapshot();
    }
}

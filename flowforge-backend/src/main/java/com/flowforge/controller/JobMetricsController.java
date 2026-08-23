package com.flowforge.controller;

import com.flowforge.dto.JobMetricsResponse;
import com.flowforge.service.JobMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics/jobs")
public class JobMetricsController {

    private final JobMetricsService jobMetricsService;

    public JobMetricsController(JobMetricsService jobMetricsService) {
        this.jobMetricsService = jobMetricsService;
    }

    @GetMapping
    public JobMetricsResponse getJobMetrics() {
        return jobMetricsService.getJobMetrics();
    }
}

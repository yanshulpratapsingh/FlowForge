package com.flowforge.controller;

import com.flowforge.dto.JobTypeAnalyticsResponse;
import com.flowforge.dto.QueueAnalyticsResponse;
import com.flowforge.dto.WorkerAnalyticsResponse;
import com.flowforge.dto.WorkloadAnalyticsResponse;
import com.flowforge.service.JobAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class JobAnalyticsController {

    private final JobAnalyticsService jobAnalyticsService;

    public JobAnalyticsController(JobAnalyticsService jobAnalyticsService) {
        this.jobAnalyticsService = jobAnalyticsService;
    }

    @GetMapping("/workload")
    public WorkloadAnalyticsResponse getWorkloadAnalytics() {
        return jobAnalyticsService.getWorkloadAnalytics();
    }

    @GetMapping("/workload/types")
    public List<JobTypeAnalyticsResponse> getTypeAnalytics() {
        return jobAnalyticsService.getTypeAnalytics();
    }

    @GetMapping("/workers")
    public WorkerAnalyticsResponse getWorkerAnalytics() {
        return jobAnalyticsService.getWorkerAnalytics();
    }

    @GetMapping("/queue")
    public QueueAnalyticsResponse getQueueAnalytics() {
        return jobAnalyticsService.getQueueAnalytics();
    }
}

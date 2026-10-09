package com.flowforge.controller;

import com.flowforge.dto.CreateJobRequest;
import com.flowforge.dto.JobResponse;
import com.flowforge.service.JobService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse createJob(@Valid @RequestBody CreateJobRequest request) {
        return JobResponse.from(jobService.createJob(request));
    }

    @GetMapping
    public List<JobResponse> getAllJobs() {
        return jobService.getAllJobs().stream()
                .map(JobResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public JobResponse getJobById(@PathVariable UUID id) {
        return JobResponse.from(jobService.getJobById(id));
    }

    @PutMapping("/{id}/queue")
    public JobResponse queueJob(@PathVariable UUID id) {
        return JobResponse.from(jobService.queueJob(id));
    }

    @PostMapping("/{id}/cancel")
    public JobResponse cancelJob(@PathVariable UUID id) {
        return JobResponse.from(jobService.cancelJob(id));
    }
}
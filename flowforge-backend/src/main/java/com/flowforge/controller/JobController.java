package com.flowforge.controller;

import com.flowforge.dto.CreateJobRequest;
import com.flowforge.entity.Job;
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
    public Job createJob(@Valid @RequestBody CreateJobRequest request) {
        return jobService.createJob(request);
    }

    @GetMapping
    public List<Job> getAllJobs() {
        return jobService.getAllJobs();
    }

    @GetMapping("/{id}")
    public Job getJobById(@PathVariable UUID id) {
        return jobService.getJobById(id);
    }

    @PutMapping("/{id}/queue")
    public Job queueJob(@PathVariable UUID id) {
        return jobService.queueJob(id);
    }

    @PostMapping("/{id}/cancel")
    public Job cancelJob(@PathVariable UUID id) {
        return jobService.cancelJob(id);
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(IllegalStateException.class)
    public String handleConflict(IllegalStateException ex) {
        return ex.getMessage();
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(RuntimeException.class)
    public String handleNotFound(RuntimeException ex) {
        if (ex.getMessage() != null && ex.getMessage().startsWith("Job not found")) {
            return ex.getMessage();
        }
        throw ex;
    }
}
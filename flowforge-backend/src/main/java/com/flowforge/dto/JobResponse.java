package com.flowforge.dto;

import com.flowforge.entity.Job;
import com.flowforge.enums.JobStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class JobResponse {

    private final UUID id;
    private final String name;
    private final String payload;
    private final int priority;
    private final JobStatus status;
    private final int retryCount;
    private final int maxRetries;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final LocalDateTime scheduledAt;
    private final String workerId;
    private final LocalDateTime leaseUntil;
    private final LocalDateTime startedAt;
    private final String lastErrorMessage;
    private final LocalDateTime lastFailedAt;
    private final String type;

    public JobResponse(UUID id, String name, String payload, int priority, JobStatus status,
                       int retryCount, int maxRetries, LocalDateTime createdAt, LocalDateTime updatedAt,
                       LocalDateTime scheduledAt, String workerId, LocalDateTime leaseUntil,
                       LocalDateTime startedAt, String lastErrorMessage, LocalDateTime lastFailedAt,
                       String type) {
        this.id = id;
        this.name = name;
        this.payload = payload;
        this.priority = priority;
        this.status = status;
        this.retryCount = retryCount;
        this.maxRetries = maxRetries;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.scheduledAt = scheduledAt;
        this.workerId = workerId;
        this.leaseUntil = leaseUntil;
        this.startedAt = startedAt;
        this.lastErrorMessage = lastErrorMessage;
        this.lastFailedAt = lastFailedAt;
        this.type = type;
    }

    public static JobResponse from(Job job) {
        if (job == null) {
            return null;
        }
        return new JobResponse(
                job.getId(),
                job.getName(),
                job.getPayload(),
                job.getPriority(),
                job.getStatus(),
                job.getRetryCount(),
                job.getMaxRetries(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getScheduledAt(),
                job.getWorkerId(),
                job.getLeaseUntil(),
                job.getStartedAt(),
                job.getLastErrorMessage(),
                job.getLastFailedAt(),
                job.getType()
        );
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPayload() {
        return payload;
    }

    public int getPriority() {
        return priority;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public String getWorkerId() {
        return workerId;
    }

    public LocalDateTime getLeaseUntil() {
        return leaseUntil;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public LocalDateTime getLastFailedAt() {
        return lastFailedAt;
    }

    public String getType() {
        return type;
    }
}

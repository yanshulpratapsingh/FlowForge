package com.flowforge.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateJobRequest {

    @NotBlank
    @jakarta.validation.constraints.Size(max = 255, message = "Job name cannot exceed 255 characters")
    private String name;

    @jakarta.validation.constraints.Size(max = 10000, message = "Payload cannot exceed 10000 characters")
    private String payload;

    @NotNull
    @Min(value = 0, message = "Priority must be 0 or positive")
    private Integer priority;

    @NotNull
    @Min(value = 0, message = "Max retries must be 0 or positive")
    private Integer maxRetries;

    @jakarta.validation.constraints.Size(max = 100, message = "Job type cannot exceed 100 characters")
    private String type;

    private java.time.LocalDateTime scheduledAt;

    public CreateJobRequest() {
    }

    public java.time.LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(java.time.LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
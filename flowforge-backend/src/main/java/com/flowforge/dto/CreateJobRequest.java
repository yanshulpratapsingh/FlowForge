package com.flowforge.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateJobRequest {

    @NotBlank
    private String name;

    private String payload;

    @NotNull
    @Min(0)
    private Integer priority;

    @NotNull
    @Min(0)
    private Integer maxRetries;

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
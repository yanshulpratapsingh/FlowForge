package com.flowforge.dto;

import com.flowforge.enums.JobStatus;
import java.util.Map;

public class WorkloadAnalyticsResponse {
    private long totalJobs;
    private double successRate;
    private double failureRate;
    private double retryRate;
    private Map<JobStatus, Long> statusCounts;

    public WorkloadAnalyticsResponse() {
    }

    public long getTotalJobs() {
        return totalJobs;
    }

    public void setTotalJobs(long totalJobs) {
        this.totalJobs = totalJobs;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    public double getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate;
    }

    public double getRetryRate() {
        return retryRate;
    }

    public void setRetryRate(double retryRate) {
        this.retryRate = retryRate;
    }

    public Map<JobStatus, Long> getStatusCounts() {
        return statusCounts;
    }

    public void setStatusCounts(Map<JobStatus, Long> statusCounts) {
        this.statusCounts = statusCounts;
    }
}

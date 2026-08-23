package com.flowforge.dto;

import com.flowforge.enums.JobStatus;
import java.util.Map;

public class JobTypeAnalyticsResponse {
    private String type;
    private long totalJobs;
    private Map<JobStatus, Long> statusCounts;
    private long completedCount;
    private long failedCount;
    private long retryingCount;
    private long cancelledCount;
    private long averageDurationMs;
    private long maxDurationMs;
    private long recentExecutions;
    private long recentFailures;
    private long recentCancellations;

    public JobTypeAnalyticsResponse() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getTotalJobs() {
        return totalJobs;
    }

    public void setTotalJobs(long totalJobs) {
        this.totalJobs = totalJobs;
    }

    public Map<JobStatus, Long> getStatusCounts() {
        return statusCounts;
    }

    public void setStatusCounts(Map<JobStatus, Long> statusCounts) {
        this.statusCounts = statusCounts;
    }

    public long getCompletedCount() {
        return completedCount;
    }

    public void setCompletedCount(long completedCount) {
        this.completedCount = completedCount;
    }

    public long getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(long failedCount) {
        this.failedCount = failedCount;
    }

    public long getRetryingCount() {
        return retryingCount;
    }

    public void setRetryingCount(long retryingCount) {
        this.retryingCount = retryingCount;
    }

    public long getCancelledCount() {
        return cancelledCount;
    }

    public void setCancelledCount(long cancelledCount) {
        this.cancelledCount = cancelledCount;
    }

    public long getAverageDurationMs() {
        return averageDurationMs;
    }

    public void setAverageDurationMs(long averageDurationMs) {
        this.averageDurationMs = averageDurationMs;
    }

    public long getMaxDurationMs() {
        return maxDurationMs;
    }

    public void setMaxDurationMs(long maxDurationMs) {
        this.maxDurationMs = maxDurationMs;
    }

    public long getRecentExecutions() {
        return recentExecutions;
    }

    public void setRecentExecutions(long recentExecutions) {
        this.recentExecutions = recentExecutions;
    }

    public long getRecentFailures() {
        return recentFailures;
    }

    public void setRecentFailures(long recentFailures) {
        this.recentFailures = recentFailures;
    }

    public long getRecentCancellations() {
        return recentCancellations;
    }

    public void setRecentCancellations(long recentCancellations) {
        this.recentCancellations = recentCancellations;
    }
}

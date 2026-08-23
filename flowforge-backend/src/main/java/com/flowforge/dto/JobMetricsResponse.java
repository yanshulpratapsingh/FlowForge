package com.flowforge.dto;

import com.flowforge.enums.JobStatus;
import java.util.Map;

public class JobMetricsResponse {
    private long totalJobs;
    private Map<JobStatus, Long> statusCounts;
    private long queueDepth;
    private long runningJobs;
    private long retryingJobs;
    private long completedJobs;
    private long deadLetterJobs;
    private long cancelledJobs;
    private long activeWorkers;
    private long averageExecutionDurationMs;
    private long maxExecutionDurationMs;
    private long recentExecutions;
    private long recentFailures;
    private long recentCancellations;

    public JobMetricsResponse() {
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

    public long getQueueDepth() {
        return queueDepth;
    }

    public void setQueueDepth(long queueDepth) {
        this.queueDepth = queueDepth;
    }

    public long getRunningJobs() {
        return runningJobs;
    }

    public void setRunningJobs(long runningJobs) {
        this.runningJobs = runningJobs;
    }

    public long getRetryingJobs() {
        return retryingJobs;
    }

    public void setRetryingJobs(long retryingJobs) {
        this.retryingJobs = retryingJobs;
    }

    public long getCompletedJobs() {
        return completedJobs;
    }

    public void setCompletedJobs(long completedJobs) {
        this.completedJobs = completedJobs;
    }

    public long getDeadLetterJobs() {
        return deadLetterJobs;
    }

    public void setDeadLetterJobs(long deadLetterJobs) {
        this.deadLetterJobs = deadLetterJobs;
    }

    public long getCancelledJobs() {
        return cancelledJobs;
    }

    public void setCancelledJobs(long cancelledJobs) {
        this.cancelledJobs = cancelledJobs;
    }

    public long getActiveWorkers() {
        return activeWorkers;
    }

    public void setActiveWorkers(long activeWorkers) {
        this.activeWorkers = activeWorkers;
    }

    public long getAverageExecutionDurationMs() {
        return averageExecutionDurationMs;
    }

    public void setAverageExecutionDurationMs(long averageExecutionDurationMs) {
        this.averageExecutionDurationMs = averageExecutionDurationMs;
    }

    public long getMaxExecutionDurationMs() {
        return maxExecutionDurationMs;
    }

    public void setMaxExecutionDurationMs(long maxExecutionDurationMs) {
        this.maxExecutionDurationMs = maxExecutionDurationMs;
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

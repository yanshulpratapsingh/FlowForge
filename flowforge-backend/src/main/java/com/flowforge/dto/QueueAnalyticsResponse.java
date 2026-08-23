package com.flowforge.dto;

public class QueueAnalyticsResponse {
    private long queueDepth;
    private long runningCount;
    private long retryingCount;
    private long oldestQueuedJobAgeSeconds;
    private long oldestRetryingJobAgeSeconds;

    public QueueAnalyticsResponse() {
    }

    public long getQueueDepth() {
        return queueDepth;
    }

    public void setQueueDepth(long queueDepth) {
        this.queueDepth = queueDepth;
    }

    public long getRunningCount() {
        return runningCount;
    }

    public void setRunningCount(long runningCount) {
        this.runningCount = runningCount;
    }

    public long getRetryingCount() {
        return retryingCount;
    }

    public void setRetryingCount(long retryingCount) {
        this.retryingCount = retryingCount;
    }

    public long getOldestQueuedJobAgeSeconds() {
        return oldestQueuedJobAgeSeconds;
    }

    public void setOldestQueuedJobAgeSeconds(long oldestQueuedJobAgeSeconds) {
        this.oldestQueuedJobAgeSeconds = oldestQueuedJobAgeSeconds;
    }

    public long getOldestRetryingJobAgeSeconds() {
        return oldestRetryingJobAgeSeconds;
    }

    public void setOldestRetryingJobAgeSeconds(long oldestRetryingJobAgeSeconds) {
        this.oldestRetryingJobAgeSeconds = oldestRetryingJobAgeSeconds;
    }
}

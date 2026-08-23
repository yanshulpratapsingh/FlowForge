package com.flowforge.dto;

import java.util.List;
import java.util.UUID;

public class WorkerAnalyticsResponse {
    private long activeWorkerCount;
    private List<WorkerStats> activeWorkers;

    public WorkerAnalyticsResponse() {
    }

    public WorkerAnalyticsResponse(long activeWorkerCount, List<WorkerStats> activeWorkers) {
        this.activeWorkerCount = activeWorkerCount;
        this.activeWorkers = activeWorkers;
    }

    public long getActiveWorkerCount() {
        return activeWorkerCount;
    }

    public void setActiveWorkerCount(long activeWorkerCount) {
        this.activeWorkerCount = activeWorkerCount;
    }

    public List<WorkerStats> getActiveWorkers() {
        return activeWorkers;
    }

    public void setActiveWorkers(List<WorkerStats> activeWorkers) {
        this.activeWorkers = activeWorkers;
    }

    public static class WorkerStats {
        private String workerId;
        private int runningJobsCount;
        private List<UUID> runningJobIds;

        public WorkerStats() {
        }

        public WorkerStats(String workerId, int runningJobsCount, List<UUID> runningJobIds) {
            this.workerId = workerId;
            this.runningJobsCount = runningJobsCount;
            this.runningJobIds = runningJobIds;
        }

        public String getWorkerId() {
            return workerId;
        }

        public void setWorkerId(String workerId) {
            this.workerId = workerId;
        }

        public int getRunningJobsCount() {
            return runningJobsCount;
        }

        public void setRunningJobsCount(int runningJobsCount) {
            this.runningJobsCount = runningJobsCount;
        }

        public List<UUID> getRunningJobIds() {
            return runningJobIds;
        }

        public void setRunningJobIds(List<UUID> runningJobIds) {
            this.runningJobIds = runningJobIds;
        }
    }
}

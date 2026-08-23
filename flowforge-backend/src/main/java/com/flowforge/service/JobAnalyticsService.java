package com.flowforge.service;

import com.flowforge.dto.JobTypeAnalyticsResponse;
import com.flowforge.dto.QueueAnalyticsResponse;
import com.flowforge.dto.WorkerAnalyticsResponse;
import com.flowforge.dto.WorkloadAnalyticsResponse;
import com.flowforge.enums.JobStatus;
import com.flowforge.entity.Job;
import com.flowforge.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class JobAnalyticsService {

    private final JobRepository jobRepository;

    @Value("${flowforge.metrics.recent-window-minutes:60}")
    private int recentWindowMinutes;

    public JobAnalyticsService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional(readOnly = true)
    public WorkloadAnalyticsResponse getWorkloadAnalytics() {
        List<Object[]> statusResult = jobRepository.countJobsByStatus();
        Map<JobStatus, Long> statusCounts = new HashMap<>();
        for (JobStatus status : JobStatus.values()) {
            statusCounts.put(status, 0L);
        }
        long total = 0;
        for (Object[] row : statusResult) {
            JobStatus status = (JobStatus) row[0];
            long count = ((Number) row[1]).longValue();
            statusCounts.put(status, count);
            total += count;
        }

        WorkloadAnalyticsResponse response = new WorkloadAnalyticsResponse();
        response.setTotalJobs(total);
        response.setStatusCounts(statusCounts);

        if (total > 0) {
            response.setSuccessRate((double) statusCounts.get(JobStatus.COMPLETED) / total * 100.0);
            response.setFailureRate((double) statusCounts.get(JobStatus.DEAD_LETTER) / total * 100.0);
            response.setRetryRate((double) statusCounts.get(JobStatus.RETRYING) / total * 100.0);
        } else {
            response.setSuccessRate(0.0);
            response.setFailureRate(0.0);
            response.setRetryRate(0.0);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<JobTypeAnalyticsResponse> getTypeAnalytics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusMinutes(recentWindowMinutes);

        List<Object[]> statusRows = jobRepository.getTypeStatusCounts();
        List<Object[]> durationRows = jobRepository.getTypeDurationMetrics();
        List<Object[]> executionRows = jobRepository.getTypeRecentExecutions(since);
        List<Object[]> failureRows = jobRepository.getTypeRecentFailures(since);
        List<Object[]> cancellationRows = jobRepository.getTypeRecentCancellations(since);

        Map<String, JobTypeAnalyticsResponse> map = new HashMap<>();

        for (Object[] row : statusRows) {
            String type = (String) row[0];
            JobStatus status = (JobStatus) row[1];
            long count = ((Number) row[2]).longValue();

            JobTypeAnalyticsResponse typeRes = map.computeIfAbsent(type, t -> {
                JobTypeAnalyticsResponse r = new JobTypeAnalyticsResponse();
                r.setType(t);
                Map<JobStatus, Long> counts = new HashMap<>();
                for (JobStatus s : JobStatus.values()) {
                    counts.put(s, 0L);
                }
                r.setStatusCounts(counts);
                return r;
            });

            typeRes.getStatusCounts().put(status, count);
            typeRes.setTotalJobs(typeRes.getTotalJobs() + count);

            if (status == JobStatus.COMPLETED) {
                typeRes.setCompletedCount(count);
            } else if (status == JobStatus.DEAD_LETTER) {
                typeRes.setFailedCount(count);
            } else if (status == JobStatus.RETRYING) {
                typeRes.setRetryingCount(count);
            } else if (status == JobStatus.CANCELLED) {
                typeRes.setCancelledCount(count);
            }
        }

        for (Object[] row : durationRows) {
            String type = (String) row[0];
            long avgDur = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            long maxDur = row[2] != null ? ((Number) row[2]).longValue() : 0L;

            JobTypeAnalyticsResponse typeRes = map.get(type);
            if (typeRes != null) {
                typeRes.setAverageDurationMs(avgDur);
                typeRes.setMaxDurationMs(maxDur);
            }
        }

        for (Object[] row : executionRows) {
            String type = (String) row[0];
            long count = ((Number) row[1]).longValue();
            JobTypeAnalyticsResponse typeRes = map.get(type);
            if (typeRes != null) {
                typeRes.setRecentExecutions(count);
            }
        }

        for (Object[] row : failureRows) {
            String type = (String) row[0];
            long count = ((Number) row[1]).longValue();
            JobTypeAnalyticsResponse typeRes = map.get(type);
            if (typeRes != null) {
                typeRes.setRecentFailures(count);
            }
        }

        for (Object[] row : cancellationRows) {
            String type = (String) row[0];
            long count = ((Number) row[1]).longValue();
            JobTypeAnalyticsResponse typeRes = map.get(type);
            if (typeRes != null) {
                typeRes.setRecentCancellations(count);
            }
        }

        return new ArrayList<>(map.values());
    }

    @Transactional(readOnly = true)
    public WorkerAnalyticsResponse getWorkerAnalytics() {
        List<Job> runningJobs = jobRepository.findByStatus(JobStatus.RUNNING);
        Map<String, List<Job>> workerGroups = runningJobs.stream()
                .filter(j -> j.getWorkerId() != null)
                .collect(java.util.stream.Collectors.groupingBy(Job::getWorkerId));

        List<WorkerAnalyticsResponse.WorkerStats> activeWorkers = new ArrayList<>();
        for (Map.Entry<String, List<Job>> entry : workerGroups.entrySet()) {
            String workerId = entry.getKey();
            List<Job> jobs = entry.getValue();
            List<UUID> runningJobIds = jobs.stream().map(Job::getId).toList();
            activeWorkers.add(new WorkerAnalyticsResponse.WorkerStats(workerId, jobs.size(), runningJobIds));
        }

        return new WorkerAnalyticsResponse(activeWorkers.size(), activeWorkers);
    }

    @Transactional(readOnly = true)
    public QueueAnalyticsResponse getQueueAnalytics() {
        LocalDateTime now = LocalDateTime.now();

        List<Object[]> statusResult = jobRepository.countJobsByStatus();
        Map<JobStatus, Long> statusCounts = new HashMap<>();
        for (JobStatus status : JobStatus.values()) {
            statusCounts.put(status, 0L);
        }
        for (Object[] row : statusResult) {
            JobStatus status = (JobStatus) row[0];
            long count = ((Number) row[1]).longValue();
            statusCounts.put(status, count);
        }

        QueueAnalyticsResponse response = new QueueAnalyticsResponse();
        response.setQueueDepth(statusCounts.get(JobStatus.QUEUED));
        response.setRunningCount(statusCounts.get(JobStatus.RUNNING));
        response.setRetryingCount(statusCounts.get(JobStatus.RETRYING));

        double oldestQueued = jobRepository.getOldestQueuedJobAgeSeconds(now);
        response.setOldestQueuedJobAgeSeconds((long) oldestQueued);

        double oldestRetrying = jobRepository.getOldestRetryingJobAgeSeconds(now);
        response.setOldestRetryingJobAgeSeconds((long) oldestRetrying);

        return response;
    }
}

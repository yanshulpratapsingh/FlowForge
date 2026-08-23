package com.flowforge.service;

import com.flowforge.dto.JobMetricsResponse;
import com.flowforge.enums.JobStatus;
import com.flowforge.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JobMetricsService {

    private final JobRepository jobRepository;

    @Value("${flowforge.metrics.recent-window-minutes:60}")
    private int recentWindowMinutes;

    public JobMetricsService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional(readOnly = true)
    public JobMetricsResponse getJobMetrics() {
        JobMetricsResponse metrics = new JobMetricsResponse();

        // 1. Get status counts
        List<Object[]> statusResult = jobRepository.countJobsByStatus();
        Map<JobStatus, Long> statusCounts = new HashMap<>();
        // Initialize all status counts to 0
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
        metrics.setTotalJobs(total);
        metrics.setStatusCounts(statusCounts);

        // Map status counts to top-level fields
        metrics.setQueueDepth(statusCounts.get(JobStatus.QUEUED));
        metrics.setRunningJobs(statusCounts.get(JobStatus.RUNNING));
        metrics.setRetryingJobs(statusCounts.get(JobStatus.RETRYING));
        metrics.setCompletedJobs(statusCounts.get(JobStatus.COMPLETED));
        metrics.setDeadLetterJobs(statusCounts.get(JobStatus.DEAD_LETTER));
        metrics.setCancelledJobs(statusCounts.get(JobStatus.CANCELLED));

        // 2. Active workers
        metrics.setActiveWorkers(jobRepository.countActiveWorkers());

        // 3. Durations
        List<Object[]> durationResult = jobRepository.getCompletedJobDurationMetrics();
        if (durationResult != null && !durationResult.isEmpty()) {
            Object[] row = durationResult.get(0);
            metrics.setAverageExecutionDurationMs(row[0] != null ? ((Number) row[0]).longValue() : 0L);
            metrics.setMaxExecutionDurationMs(row[1] != null ? ((Number) row[1]).longValue() : 0L);
        }

        // 4. Recent calculations
        LocalDateTime since = LocalDateTime.now().minusMinutes(recentWindowMinutes);
        metrics.setRecentExecutions(jobRepository.countRecentExecutions(since));
        metrics.setRecentFailures(jobRepository.countRecentFailures(since));
        metrics.setRecentCancellations(jobRepository.countRecentCancellations(since));

        return metrics;
    }
}

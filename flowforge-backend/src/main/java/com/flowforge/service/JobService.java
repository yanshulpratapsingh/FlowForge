package com.flowforge.service;

import com.flowforge.dto.CreateJobRequest;
import com.flowforge.entity.Job;
import com.flowforge.enums.JobStatus;
import com.flowforge.repository.JobRepository;
import com.flowforge.config.FlowForgeLimitsConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;

    @Autowired
    private FlowForgeLimitsConfig limitsConfig;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${flowforge.retry.base-delay-ms:2000}")
    private long baseDelayMs;

    @Value("${flowforge.worker.lease-duration-ms:30000}")
    private long leaseDurationMs;

    @Value("${flowforge.scheduler.starvation-threshold-seconds:300}")
    private long starvationThresholdSeconds;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional
    public Job createJob(CreateJobRequest request) {

        Job job = new Job();

        job.setName(request.getName());
        job.setPayload(request.getPayload());
        job.setPriority(request.getPriority());

        job.setStatus(JobStatus.CREATED);

        job.setRetryCount(0);
        job.setMaxRetries(request.getMaxRetries());

        String type = request.getType();
        if (type == null || type.trim().isEmpty()) {
            type = "SIMULATED";
        }
        job.setType(type.toUpperCase());

        LocalDateTime now = LocalDateTime.now();

        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        job.setScheduledAt(request.getScheduledAt());

        return jobRepository.save(job);
    }

    public List<Job> getAllJobs() {
        return jobRepository.findAll();
    }

    public Job getJobById(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new com.flowforge.exception.ResourceNotFoundException("Job not found: " + id));
    }

    @Transactional
    public Job queueJob(UUID id) {

        Job job = getJobById(id);

        job.transitionTo(JobStatus.QUEUED);

        return jobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Job> claimExecutableJobs(int limit, String workerId) {
        LocalDateTime now = LocalDateTime.now();
        List<Job> claimedJobs = new ArrayList<>();

        // 1. Get distinct types of queued or retrying jobs
        List<String> activeTypes = jobRepository.findActiveJobTypes();

        for (String type : activeTypes) {
            if (claimedJobs.size() >= limit) {
                break;
            }

            int remainingLimit = limit - claimedJobs.size();
            FlowForgeLimitsConfig.TypeLimit limits = limitsConfig.getTypes().get(type.toUpperCase());

            int slots = remainingLimit;
            if (limits != null) {
                // Acquire transaction-scoped advisory lock for this type
                long lockId = (long) type.toUpperCase().hashCode();
                jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + lockId + ")");

                // Count currently running jobs of this type
                int runningCount = jobRepository.countRunningJobsByType(type.toUpperCase());
                if (limits.getMaxConcurrency() != null) {
                    int availableConcurrency = limits.getMaxConcurrency() - runningCount;
                    slots = Math.min(slots, availableConcurrency);
                }

                // Count executions in the last minute
                if (limits.getMaxRatePerMinute() != null) {
                    LocalDateTime oneMinuteAgo = now.minusMinutes(1);
                    int recentCount = jobRepository.countRecentExecutionsByType(type.toUpperCase(), oneMinuteAgo);
                    int availableRate = limits.getMaxRatePerMinute() - recentCount;
                    slots = Math.min(slots, availableRate);
                }
            }

            if (slots > 0) {
                List<Job> eligible = jobRepository.findExecutableJobsByTypeWithLock(
                        type.toUpperCase(), now, slots, starvationThresholdSeconds);
                for (Job job : eligible) {
                    job.transitionTo(JobStatus.RUNNING);
                    job.setWorkerId(workerId);
                    job.setStartedAt(now);
                    job.setLeaseUntil(now.plus(Duration.ofMillis(leaseDurationMs)));
                    claimedJobs.add(jobRepository.save(job));
                    log.info("Job claimed - Job ID: {}, Name: {}, Worker: {}, Lease until: {}", 
                            job.getId(), job.getName(), workerId, job.getLeaseUntil());
                }
            }
        }

        return claimedJobs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean extendLease(UUID id, String workerId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime newLeaseUntil = now.plus(Duration.ofMillis(leaseDurationMs));
        int updatedCount = jobRepository.extendLease(id, workerId, newLeaseUntil, now);
        if (updatedCount > 0) {
            log.info("Heartbeat extended lease - Job ID: {}, Worker: {} until {}", id, workerId, newLeaseUntil);
            return true;
        } else {
            log.warn("Heartbeat update rejected (expired/stale/recovered) - Job ID: {}, Worker: {}", id, workerId);
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSuccess(UUID id, String workerId) {
        int updated = jobRepository.markCompleted(id, workerId, LocalDateTime.now());
        if (updated > 0) {
            log.info("Job completed - Job ID: {}, Worker: {}", id, workerId);
        } else {
            log.warn("Stale worker completion rejected (lease expired or already recovered) - Job ID: {}, Worker: {}", id, workerId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailure(UUID id, String workerId, String errorMessage) {
        Job job = getJobById(id);
        int currentRetry = job.getRetryCount() + 1;

        LocalDateTime now = LocalDateTime.now();
        String finalError = errorMessage != null ? errorMessage : "Unknown error";
        if (finalError.length() > 2000) {
            finalError = finalError.substring(0, 1997) + "...";
        }

        if (currentRetry <= job.getMaxRetries()) {
            long delayMs = baseDelayMs * (long) Math.pow(2, currentRetry - 1);
            LocalDateTime scheduledAt = now.plus(Duration.ofMillis(delayMs));
            int updated = jobRepository.markRetrying(id, workerId, currentRetry, scheduledAt, finalError, now, now);
            if (updated > 0) {
                log.info("Retry scheduled - Job ID: {} - Attempt #{} failed. Next retry in {}ms. Error: {}",
                        id, currentRetry, delayMs, finalError);
            } else {
                log.warn("Stale worker failure update rejected (lease expired or already recovered) - Job ID: {}, Worker: {}", id, workerId);
            }
        } else {
            int updated = jobRepository.markDeadLetter(id, workerId, currentRetry, finalError, now, now);
            if (updated > 0) {
                log.error("Job moved to DEAD_LETTER - Job ID: {} - Max retries ({}) exceeded. Error: {}",
                        id, job.getMaxRetries(), finalError);
            } else {
                log.warn("Stale worker failure update rejected (lease expired or already recovered) - Job ID: {}, Worker: {}", id, workerId);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverExpiredJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<Job> expiredJobs = jobRepository.findExpiredJobsWithLock(now);
        for (Job job : expiredJobs) {
            log.warn("Recovering expired job: ID={}, workerId={}, leaseUntil={}", 
                    job.getId(), job.getWorkerId(), job.getLeaseUntil());
            
            job.transitionTo(JobStatus.QUEUED);
            job.setWorkerId(null);
            job.setStartedAt(null);
            job.setLeaseUntil(null);

            jobRepository.save(job);
            log.info("Job recovered successfully to QUEUED: ID={}", job.getId());
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private JobExecutionEngine executionEngine;

    @Transactional
    public Job cancelJob(UUID id) {
        Job job = getJobById(id);

        if (job.getStatus() == JobStatus.CANCELLED) {
            return job;
        }

        if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.DEAD_LETTER || job.getStatus() == JobStatus.CREATED) {
            throw new IllegalStateException("Cannot cancel job in status: " + job.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = 0;

        if (job.getStatus() == JobStatus.QUEUED || job.getStatus() == JobStatus.RETRYING) {
            updated = jobRepository.cancelQueuedOrRetrying(id, now);
        } else if (job.getStatus() == JobStatus.RUNNING) {
            updated = jobRepository.cancelRunning(id, job.getWorkerId(), now);
        }

        if (updated > 0) {
            if (job.getStatus() == JobStatus.RUNNING) {
                executionEngine.interruptLocalJob(id);
            }
            return getJobById(id);
        } else {
            job = getJobById(id);
            if (job.getStatus() == JobStatus.CANCELLED) {
                return job;
            }
            if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.DEAD_LETTER || job.getStatus() == JobStatus.CREATED) {
                throw new IllegalStateException("Cannot cancel job in status: " + job.getStatus());
            }
            if (job.getStatus() == JobStatus.QUEUED || job.getStatus() == JobStatus.RETRYING) {
                updated = jobRepository.cancelQueuedOrRetrying(id, now);
            } else if (job.getStatus() == JobStatus.RUNNING) {
                updated = jobRepository.cancelRunning(id, job.getWorkerId(), now);
            }
            if (updated > 0) {
                if (job.getStatus() == JobStatus.RUNNING) {
                    executionEngine.interruptLocalJob(id);
                }
                return getJobById(id);
            }
            throw new IllegalStateException("Concurrent modification during job cancellation");
        }
    }
}
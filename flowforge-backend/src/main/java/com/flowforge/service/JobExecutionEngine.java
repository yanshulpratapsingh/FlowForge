package com.flowforge.service;

import com.flowforge.entity.Job;
import com.flowforge.enums.JobStatus;
import java.util.UUID;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
@EnableScheduling
public class JobExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionEngine.class);

    private final JobService jobService;
    private final JobExecutor jobExecutor;
    private final Executor jobExecutorPool;
    private final WorkerIdentity workerIdentity;

    @Value("${flowforge.scheduler.batch-size:5}")
    private int batchSize;

    @Value("${flowforge.worker.heartbeat-interval-ms:10000}")
    private long heartbeatIntervalMs;

    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r);
        t.setName("HeartbeatScheduler");
        t.setDaemon(true);
        return t;
    });

    public JobExecutionEngine(JobService jobService,
                              JobExecutor jobExecutor,
                              @Qualifier("jobExecutor") Executor jobExecutorPool,
                              WorkerIdentity workerIdentity) {
        this.jobService = jobService;
        this.jobExecutor = jobExecutor;
        this.jobExecutorPool = jobExecutorPool;
        this.workerIdentity = workerIdentity;
    }

    private final java.util.concurrent.ConcurrentHashMap<UUID, Thread> activeThreads = new java.util.concurrent.ConcurrentHashMap<>();

    public void interruptLocalJob(UUID jobId) {
        Thread thread = activeThreads.get(jobId);
        if (thread != null) {
            log.info("Interrupting local execution thread for Job ID: {}", jobId);
            thread.interrupt();
        }
    }

    @Scheduled(fixedDelayString = "${flowforge.scheduler.poll-interval-ms:1000}")
    public void pollAndExecute() {
        log.trace("Polling for executable jobs...");

        List<Job> claimedJobs;
        try {
            // Claim jobs atomically in a separate short-lived transaction
            claimedJobs = jobService.claimExecutableJobs(batchSize, workerIdentity.getWorkerId());
        } catch (Exception e) {
            log.error("Failed to claim executable jobs", e);
            return;
        }

        if (claimedJobs.isEmpty()) {
            return;
        }

        for (Job job : claimedJobs) {
            log.info("Job submitted - Job ID: {} submitted to worker pool", job.getId());

            // Start periodic heartbeat updates
            ScheduledFuture<?>[] heartbeatTaskRef = new ScheduledFuture<?>[1];
            heartbeatTaskRef[0] = heartbeatScheduler.scheduleAtFixedRate(() -> {
                try {
                    boolean success = jobService.extendLease(job.getId(), workerIdentity.getWorkerId());
                    if (!success) {
                        Job dbJob = jobService.getJobById(job.getId());
                        if (dbJob.getStatus() == JobStatus.CANCELLED) {
                            log.info("Heartbeat detected CANCELLED status in DB for Job ID: {}. Triggering thread interruption.", job.getId());
                            interruptLocalJob(job.getId());
                            if (heartbeatTaskRef[0] != null) {
                                heartbeatTaskRef[0].cancel(true);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Heartbeat error - Failed to extend lease for Job ID: {}", job.getId(), e);
                }
            }, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);

            jobExecutorPool.execute(() -> {
                activeThreads.put(job.getId(), Thread.currentThread());
                try {
                    jobExecutor.execute(job);
                    jobService.handleSuccess(job.getId(), workerIdentity.getWorkerId());
                } catch (Throwable t) {
                    if (Thread.currentThread().isInterrupted() || t instanceof InterruptedException || t.getCause() instanceof InterruptedException) {
                        log.info("Job execution cancelled/interrupted - Job ID: {}", job.getId());
                    } else {
                        log.error("Worker failed - Job ID: {} failed during execution: {}", job.getId(), t.getMessage());
                        try {
                            jobService.handleFailure(job.getId(), workerIdentity.getWorkerId(), t.getMessage());
                        } catch (Exception ex) {
                            log.error("Failed to process job failure transition for Job ID: {}", job.getId(), ex);
                        }
                    }
                } finally {
                    activeThreads.remove(job.getId());
                    // Always cancel the heartbeat when execution completes
                    if (heartbeatTaskRef[0] != null) {
                        heartbeatTaskRef[0].cancel(true);
                    }
                }
            });
        }
    }

    @PreDestroy
    public void shutdownScheduler() {
        log.info("Shutting down HeartbeatScheduler...");
        heartbeatScheduler.shutdown();
        try {
            if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

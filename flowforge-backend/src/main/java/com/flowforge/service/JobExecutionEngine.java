package com.flowforge.service;

import com.flowforge.entity.Job;
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
            ScheduledFuture<?> heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
                try {
                    jobService.extendLease(job.getId(), workerIdentity.getWorkerId());
                } catch (Exception e) {
                    log.error("Heartbeat error - Failed to extend lease for Job ID: {}", job.getId(), e);
                }
            }, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);

            jobExecutorPool.execute(() -> {
                try {
                    jobExecutor.execute(job);
                    jobService.handleSuccess(job.getId(), workerIdentity.getWorkerId());
                } catch (Throwable t) {
                    log.error("Worker failed - Job ID: {} failed during execution: {}", job.getId(), t.getMessage());
                    try {
                        jobService.handleFailure(job.getId(), workerIdentity.getWorkerId(), t.getMessage());
                    } catch (Exception ex) {
                        log.error("Failed to process job failure transition for Job ID: {}", job.getId(), ex);
                    }
                } finally {
                    // Always cancel the heartbeat when execution completes
                    heartbeatTask.cancel(true);
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

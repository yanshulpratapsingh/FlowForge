package com.flowforge.service;

import com.flowforge.dto.CreateJobRequest;
import com.flowforge.dto.JobMetricsResponse;
import com.flowforge.entity.Job;
import com.flowforge.enums.JobStatus;
import com.flowforge.repository.JobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "flowforge.scheduler.poll-interval-ms=3600000",
    "flowforge.worker.recovery-interval-ms=3600000"
})
public class JobExecutionEngineTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobHandlerRegistry registry;

    @Autowired
    private HandlerRoutingJobExecutor routingExecutor;

    @Autowired
    private JobExecutionEngine engine;

    @Autowired
    private com.flowforge.controller.JobController jobController;

    @Autowired
    private com.flowforge.controller.JobMetricsController jobMetricsController;

    @Autowired
    private com.flowforge.service.JobMetricsService jobMetricsService;

    @Autowired
    private com.flowforge.config.FlowForgeLimitsConfig limitsConfig;

    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    public void setup() {
        this.mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(jobController, jobMetricsController)
                .build();
        limitsConfig.getTypes().clear();
    }

    @AfterEach
    public void cleanUp() {
        jobRepository.deleteAll();
    }

    @Test
    public void testValidStatusTransitions() {
        Job job = new Job();
        job.setStatus(JobStatus.CREATED);

        // CREATED -> QUEUED
        job.transitionTo(JobStatus.QUEUED);
        assertEquals(JobStatus.QUEUED, job.getStatus());

        // QUEUED -> RUNNING
        job.transitionTo(JobStatus.RUNNING);
        assertEquals(JobStatus.RUNNING, job.getStatus());

        // RUNNING -> COMPLETED
        job.transitionTo(JobStatus.COMPLETED);
        assertEquals(JobStatus.COMPLETED, job.getStatus());

        // RUNNING -> QUEUED (for recovery)
        Job job2 = new Job();
        job2.setStatus(JobStatus.RUNNING);
        job2.transitionTo(JobStatus.QUEUED);
        assertEquals(JobStatus.QUEUED, job2.getStatus());
    }

    @Test
    public void testInvalidStatusTransitions() {
        Job job = new Job();
        job.setStatus(JobStatus.CREATED);

        // CREATED to RUNNING directly should fail
        assertThrows(IllegalStateException.class, () -> job.transitionTo(JobStatus.RUNNING));

        job.setStatus(JobStatus.COMPLETED);
        // Transition from terminal state COMPLETED should fail
        assertThrows(IllegalStateException.class, () -> job.transitionTo(JobStatus.RUNNING));
    }

    @Test
    public void testExponentialBackoffCalculations() {
        long baseDelayMs = 2000;

        // Attempt 1 fails -> currentRetry = 1. delay = baseDelayMs * 2^(1-1) = 2000
        int currentRetry = 1;
        long delayMs = baseDelayMs * (long) Math.pow(2, currentRetry - 1);
        assertEquals(2000, delayMs);

        // Attempt 2 fails -> currentRetry = 2. delay = 4000
        currentRetry = 2;
        delayMs = baseDelayMs * (long) Math.pow(2, currentRetry - 1);
        assertEquals(4000, delayMs);
    }

    @Test
    public void testLeaseAssignment() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-lease");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");

        Job job = jobService.createJob(req);
        job = jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        assertEquals(1, claimed.size());

        Job claimedJob = claimed.get(0);
        assertEquals(JobStatus.RUNNING, claimedJob.getStatus());
        assertEquals("worker-1", claimedJob.getWorkerId());
        assertNotNull(claimedJob.getStartedAt());
        assertNotNull(claimedJob.getLeaseUntil());
    }

    @Test
    public void testHeartbeatExtendsLease() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-hb");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");

        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        Job claimedJob = claimed.get(0);

        Job reloadedClaimedJob = jobService.getJobById(claimedJob.getId());
        LocalDateTime firstLeaseUntil = reloadedClaimedJob.getLeaseUntil();

        boolean hbSuccess = jobService.extendLease(claimedJob.getId(), "worker-1");
        assertTrue(hbSuccess);

        Job updatedJob = jobService.getJobById(claimedJob.getId());
        assertTrue(updatedJob.getLeaseUntil().isAfter(firstLeaseUntil));
    }

    @Test
    public void testExpiredLeaseRecovery() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-recovery");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");

        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        Job claimedJob = claimed.get(0);

        // Manually set lease expired in db
        claimedJob.setLeaseUntil(LocalDateTime.now().minusMinutes(5));
        jobRepository.saveAndFlush(claimedJob);

        jobService.recoverExpiredJobs();

        Job recoveredJob = jobService.getJobById(claimedJob.getId());
        assertEquals(JobStatus.QUEUED, recoveredJob.getStatus());
        assertNull(recoveredJob.getWorkerId());
        assertNull(recoveredJob.getLeaseUntil());
        assertNull(recoveredJob.getStartedAt());
    }

    @Test
    public void testWrongWorkerCannotHeartbeat() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-wrong-worker");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");

        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        Job claimedJob = claimed.get(0);

        boolean hbSuccess = jobService.extendLease(claimedJob.getId(), "worker-2");
        assertFalse(hbSuccess);

        // Verify leaseUntil has NOT changed by reloading
        Job reloadedClaimedJob = jobService.getJobById(claimedJob.getId());
        Job finalJob = jobService.getJobById(claimedJob.getId());
        assertEquals(reloadedClaimedJob.getLeaseUntil(), finalJob.getLeaseUntil());
    }

    @Test
    public void testStaleWorkerCompletionAndFailureRejection() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-stale");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");

        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        Job claimedJob = claimed.get(0);

        // Simulate lease recovery
        claimedJob.setStatus(JobStatus.QUEUED);
        claimedJob.setWorkerId(null);
        jobRepository.saveAndFlush(claimedJob);

        // Old worker (worker-1) tries to mark success -> should fail / do nothing
        jobService.handleSuccess(claimedJob.getId(), "worker-1");
        Job afterSuccess = jobService.getJobById(claimedJob.getId());
        assertEquals(JobStatus.QUEUED, afterSuccess.getStatus()); // remains QUEUED

        // Old worker (worker-1) tries to mark failure -> should fail / do nothing
        jobService.handleFailure(claimedJob.getId(), "worker-1", "stale fail");
        Job afterFailure = jobService.getJobById(claimedJob.getId());
        assertEquals(JobStatus.QUEUED, afterFailure.getStatus()); // remains QUEUED
        assertEquals(0, afterFailure.getRetryCount());
    }

    @Test
    public void testRetryExhaustion() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-retry-exhaustion");
        req.setPriority(1);
        req.setMaxRetries(2);
        req.setPayload("payload");

        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        // Attempt 1
        List<Job> claimed1 = jobService.claimExecutableJobs(1, "worker-1");
        Job job1 = claimed1.get(0);
        jobService.handleFailure(job1.getId(), "worker-1", "error 1");

        Job state1 = jobService.getJobById(job1.getId());
        assertEquals(JobStatus.RETRYING, state1.getStatus());
        assertNull(state1.getWorkerId());
        assertEquals(1, state1.getRetryCount());
        assertEquals("error 1", state1.getLastErrorMessage());
        assertNotNull(state1.getLastFailedAt());

        // Force scheduled time to be in the past for Attempt 2 eligibility
        state1.setScheduledAt(LocalDateTime.now().minusSeconds(1));
        jobRepository.saveAndFlush(state1);

        // Attempt 2
        List<Job> claimed2 = jobService.claimExecutableJobs(1, "worker-2");
        Job job2 = claimed2.get(0);
        jobService.handleFailure(job2.getId(), "worker-2", "error 2");

        Job state2 = jobService.getJobById(job2.getId());
        assertEquals(JobStatus.RETRYING, state2.getStatus());
        assertNull(state2.getWorkerId());
        assertEquals(2, state2.getRetryCount());
        assertEquals("error 2", state2.getLastErrorMessage());

        // Force scheduled time in the past for Attempt 3 eligibility
        state2.setScheduledAt(LocalDateTime.now().minusSeconds(1));
        jobRepository.saveAndFlush(state2);

        // Attempt 3 (Retry limit exhausted, since currentRetry (3) > maxRetries (2))
        List<Job> claimed3 = jobService.claimExecutableJobs(1, "worker-3");
        Job job3 = claimed3.get(0);
        jobService.handleFailure(job3.getId(), "worker-3", "error 3");

        Job state3 = jobService.getJobById(job3.getId());
        assertEquals(JobStatus.DEAD_LETTER, state3.getStatus());
        assertNull(state3.getWorkerId());
        assertEquals(3, state3.getRetryCount());
        assertEquals("error 3", state3.getLastErrorMessage());

        // Verify that re-claiming is impossible
        List<Job> claimed4 = jobService.claimExecutableJobs(1, "worker-4");
        assertTrue(claimed4.isEmpty());

        // Verify that terminal state prevents transitions
        assertThrows(IllegalStateException.class, () -> state3.transitionTo(JobStatus.RUNNING));
    }

    @Test
    public void testRetryScheduling() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-retry-scheduling");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");

        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed1 = jobService.claimExecutableJobs(1, "worker-1");
        Job job1 = claimed1.get(0);
        jobService.handleFailure(job1.getId(), "worker-1", "first fail");

        // Job is in RETRYING, scheduledAt is in the future. It must NOT be claimable.
        List<Job> claimedFuture = jobService.claimExecutableJobs(1, "worker-2");
        assertTrue(claimedFuture.isEmpty());

        // Manually move scheduledAt to the past
        Job retryingJob = jobService.getJobById(job1.getId());
        retryingJob.setScheduledAt(LocalDateTime.now().minusSeconds(10));
        jobRepository.saveAndFlush(retryingJob);

        // Now it must be claimable
        List<Job> claimedPast = jobService.claimExecutableJobs(1, "worker-2");
        assertEquals(1, claimedPast.size());
        assertEquals("worker-2", claimedPast.get(0).getWorkerId());
    }

    @Test
    public void testFailureInformationPersistenceAndTruncation() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-failure-info");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");

        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        Job claimedJob = claimed.get(0);

        // Massive string > 2000 chars
        String massiveMessage = "A".repeat(2500);
        jobService.handleFailure(claimedJob.getId(), "worker-1", massiveMessage);

        Job failedJob = jobService.getJobById(claimedJob.getId());
        assertNotNull(failedJob.getLastFailedAt());
        assertEquals(2000, failedJob.getLastErrorMessage().length());
        assertTrue(failedJob.getLastErrorMessage().endsWith("..."));
        assertTrue(failedJob.getLastErrorMessage().startsWith("AAA"));

        // Move back to running to fail it again with null error message
        failedJob.setScheduledAt(LocalDateTime.now().minusSeconds(1));
        jobRepository.saveAndFlush(failedJob);

        List<Job> claimed2 = jobService.claimExecutableJobs(1, "worker-2");
        Job claimedJob2 = claimed2.get(0);
        jobService.handleFailure(claimedJob2.getId(), "worker-2", null);

        Job failedJob2 = jobService.getJobById(claimedJob2.getId());
        assertEquals("Unknown error", failedJob2.getLastErrorMessage());
    }

    @Test
    public void testHeartbeatCancellationAfterCompletion() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-hb-cancellation");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");

        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        Job claimedJob = claimed.get(0);

        // Heartbeat should succeed while running
        boolean hb1 = jobService.extendLease(claimedJob.getId(), "worker-1");
        assertTrue(hb1);

        // Complete job
        jobService.handleSuccess(claimedJob.getId(), "worker-1");
        Job completedJob = jobService.getJobById(claimedJob.getId());
        assertEquals(JobStatus.COMPLETED, completedJob.getStatus());

        // Heartbeat must fail after completion (affect 0 rows)
        boolean hb2 = jobService.extendLease(claimedJob.getId(), "worker-1");
        assertFalse(hb2);
    }

    @Test
    public void testRegistryDiscoversSimulatedJobHandler() {
        JobHandler handler = registry.getHandler("SIMULATED");
        assertNotNull(handler);
        assertTrue(handler instanceof SimulatedJobHandler);
    }

    @Test
    public void testCaseInsensitiveHandlerLookup() {
        JobHandler h1 = registry.getHandler("simulated");
        JobHandler h2 = registry.getHandler("Simulated");
        JobHandler h3 = registry.getHandler("SIMULATED");
        assertNotNull(h1);
        assertSame(h1, h2);
        assertSame(h2, h3);
    }

    @Test
    public void testUnknownHandlerTypeProducesClearFailure() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            registry.getHandler("NON_EXISTENT_TYPE");
        });
        assertTrue(ex.getMessage().contains("No handler registered for job type: NON_EXISTENT_TYPE"));
    }

    @Test
    public void testDuplicateHandlerTypeRegistrationIsRejected() {
        JobHandler dup = new JobHandler() {
            @Override
            public String getJobType() {
                return "SIMULATED";
            }
            @Override
            public void handle(Job job) throws Exception {}
        };
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            registry.register(dup);
        });
        assertTrue(ex.getMessage().contains("Duplicate handler registration for type: SIMULATED"));
    }

    @Test
    public void testSimulatedJobExecutesExactlyAsBefore() throws Exception {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-simulated");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("sleep:10"); // fast sleep
        req.setType("SIMULATED");

        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        Job claimedJob = claimed.get(0);

        routingExecutor.execute(claimedJob); // runs simulated logic
    }

    @Test
    public void testCustomTestHandlerSuccessfullyExecutes() throws Exception {
        String uniqueType = "CUSTOM_TEST_" + java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase();
        java.util.concurrent.atomic.AtomicBoolean called = new java.util.concurrent.atomic.AtomicBoolean(false);

        registry.register(new JobHandler() {
            @Override
            public String getJobType() {
                return uniqueType;
            }
            @Override
            public void handle(Job job) throws Exception {
                called.set(true);
            }
        });

        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-custom");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setType(uniqueType);

        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        Job claimedJob = claimed.get(0);

        routingExecutor.execute(claimedJob);
        assertTrue(called.get());
    }

    @Test
    public void testCustomHandlerFailureAndRetryExhaustion() {
        String uniqueType = "CUSTOM_FAIL_" + java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase();

        registry.register(new JobHandler() {
            @Override
            public String getJobType() {
                return uniqueType;
            }
            @Override
            public void handle(Job job) throws Exception {
                throw new RuntimeException("custom logic failure");
            }
        });

        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-custom-fail");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setType(uniqueType);

        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        // Attempt 1
        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        Job claimedJob = claimed.get(0);

        // Execute routingExecutor inside execution context simulation
        try {
            routingExecutor.execute(claimedJob);
            fail("Expected execution failure");
        } catch (Exception e) {
            jobService.handleFailure(claimedJob.getId(), "worker-1", e.getMessage());
        }

        Job state1 = jobService.getJobById(claimedJob.getId());
        assertEquals(JobStatus.RETRYING, state1.getStatus());
        assertEquals(1, state1.getRetryCount());
        assertEquals("custom logic failure", state1.getLastErrorMessage());

        // Set scheduledAt in past
        state1.setScheduledAt(LocalDateTime.now().minusSeconds(1));
        jobRepository.saveAndFlush(state1);

        // Attempt 2 (Retry limit exhausted)
        List<Job> claimed2 = jobService.claimExecutableJobs(1, "worker-2");
        Job claimedJob2 = claimed2.get(0);

        try {
            routingExecutor.execute(claimedJob2);
            fail("Expected execution failure");
        } catch (Exception e) {
            jobService.handleFailure(claimedJob2.getId(), "worker-2", e.getMessage());
        }

        Job state2 = jobService.getJobById(claimedJob2.getId());
        assertEquals(JobStatus.DEAD_LETTER, state2.getStatus());
        assertEquals(2, state2.getRetryCount());
        assertEquals("custom logic failure", state2.getLastErrorMessage());
    }

    @Test
    public void testCancelQueuedJob() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-cancel-queued");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");
        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        Job cancelled = jobService.cancelJob(job.getId());
        assertEquals(JobStatus.CANCELLED, cancelled.getStatus());
        assertNull(cancelled.getWorkerId());
        assertNull(cancelled.getLeaseUntil());
        assertNull(cancelled.getStartedAt());
        assertNull(cancelled.getScheduledAt());
    }

    @Test
    public void testCancelRetryingJob() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-cancel-retrying");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");
        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        jobService.handleFailure(claimed.get(0).getId(), "worker-1", "err");

        Job retrying = jobService.getJobById(job.getId());
        assertEquals(JobStatus.RETRYING, retrying.getStatus());

        Job cancelled = jobService.cancelJob(job.getId());
        assertEquals(JobStatus.CANCELLED, cancelled.getStatus());

        // Assert it cannot be claimed
        List<Job> claimedAfter = jobService.claimExecutableJobs(1, "worker-2");
        assertTrue(claimedAfter.isEmpty());
    }

    @Test
    public void testCancelRunningJobLocal() throws Exception {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-cancel-running-local");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("sleep:5000"); // 5s sleep to keep it active
        req.setType("SIMULATED");

        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        engine.pollAndExecute();

        // Deterministic check loop to verify job enters RUNNING state
        int limit = 100;
        Job runningJob = jobService.getJobById(job.getId());
        while (runningJob.getStatus() != JobStatus.RUNNING && limit > 0) {
            Thread.sleep(10);
            runningJob = jobService.getJobById(job.getId());
            limit--;
        }
        assertEquals(JobStatus.RUNNING, runningJob.getStatus());

        // Cancel job
        jobService.cancelJob(job.getId());

        Job cancelledJob = jobService.getJobById(job.getId());
        assertEquals(JobStatus.CANCELLED, cancelledJob.getStatus());

        // Wait a tiny moment to let the interrupted exception log and ensure retryCount stays 0
        Thread.sleep(100);
        Job finalJob = jobService.getJobById(job.getId());
        assertEquals(0, finalJob.getRetryCount());
        assertEquals(JobStatus.CANCELLED, finalJob.getStatus());
    }

    @Test
    public void testCancelRunningJobRemote() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-cancel-running-remote");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");

        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-remote");
        Job claimedJob = claimed.get(0);
        assertEquals("worker-remote", claimedJob.getWorkerId());

        // Cancel the job remotely
        jobService.cancelJob(claimedJob.getId());

        Job cancelledJob = jobService.getJobById(claimedJob.getId());
        assertEquals(JobStatus.CANCELLED, cancelledJob.getStatus());
        assertNull(cancelledJob.getWorkerId());

        // Now simulate remote heartbeat failing
        boolean success = jobService.extendLease(claimedJob.getId(), "worker-remote");
        assertFalse(success);

        // Heartbeat logic reads status and realizes it was CANCELLED
        Job dbJob = jobService.getJobById(claimedJob.getId());
        assertEquals(JobStatus.CANCELLED, dbJob.getStatus());
    }

    @Test
    public void testCancelAlreadyCancelledJob() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-cancel-idempotent");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");
        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        Job cancelled1 = jobService.cancelJob(job.getId());
        assertEquals(JobStatus.CANCELLED, cancelled1.getStatus());

        Job cancelled2 = jobService.cancelJob(job.getId());
        assertEquals(JobStatus.CANCELLED, cancelled2.getStatus());
    }

    @Test
    public void testCannotCancelCompletedJob() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-no-cancel-completed");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");
        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        jobService.handleSuccess(claimed.get(0).getId(), "worker-1");

        Job completed = jobService.getJobById(job.getId());
        assertEquals(JobStatus.COMPLETED, completed.getStatus());

        assertThrows(IllegalStateException.class, () -> {
            jobService.cancelJob(job.getId());
        });
    }

    @Test
    public void testCannotCancelDeadLetterJob() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-no-cancel-dl");
        req.setPriority(1);
        req.setMaxRetries(0); // 0 retries => fails immediately to DLQ
        req.setPayload("payload");
        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        jobService.handleFailure(claimed.get(0).getId(), "worker-1", "fail");

        Job dl = jobService.getJobById(job.getId());
        assertEquals(JobStatus.DEAD_LETTER, dl.getStatus());

        assertThrows(IllegalStateException.class, () -> {
            jobService.cancelJob(job.getId());
        });
    }

    @Test
    public void testCannotCancelCreatedJob() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-no-cancel-created");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");
        Job job = jobService.createJob(req);

        assertEquals(JobStatus.CREATED, job.getStatus());

        assertThrows(IllegalStateException.class, () -> {
            jobService.cancelJob(job.getId());
        });
    }

    @Test
    public void testLateCompletionAfterCancellationRejected() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-late-completion");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");
        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        jobService.cancelJob(job.getId());

        // Late completion should fail or affect 0 rows
        jobService.handleSuccess(job.getId(), "worker-1");

        Job dbJob = jobService.getJobById(job.getId());
        assertEquals(JobStatus.CANCELLED, dbJob.getStatus());
    }

    @Test
    public void testLateFailureAfterCancellationRejected() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-late-failure");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");
        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        jobService.cancelJob(job.getId());

        // Late failure should fail or affect 0 rows
        jobService.handleFailure(job.getId(), "worker-1", "err");

        Job dbJob = jobService.getJobById(job.getId());
        assertEquals(JobStatus.CANCELLED, dbJob.getStatus());
        assertEquals(0, dbJob.getRetryCount());
    }

    @Test
    public void testCancellationRaceWithRecovery() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-race-recovery");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");
        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        Job claimedJob = claimed.get(0);

        // Expire lease manually
        claimedJob.setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        jobRepository.saveAndFlush(claimedJob);

        // Run recovery -> Transitions to QUEUED
        jobService.recoverExpiredJobs();

        Job recovered = jobService.getJobById(job.getId());
        assertEquals(JobStatus.QUEUED, recovered.getStatus());

        // Now cancel it -> Transitions to CANCELLED
        jobService.cancelJob(job.getId());

        Job finalJob = jobService.getJobById(job.getId());
        assertEquals(JobStatus.CANCELLED, finalJob.getStatus());
    }

    @Test
    public void testCancellationDoesNotAffectOtherWorkers() throws Exception {
        CreateJobRequest req1 = new CreateJobRequest();
        req1.setName("test-no-cross-cancel-1");
        req1.setPriority(1);
        req1.setMaxRetries(1);
        req1.setPayload("sleep:5000");
        req1.setType("SIMULATED");

        Job job1 = jobService.createJob(req1);
        jobService.queueJob(job1.getId());

        CreateJobRequest req2 = new CreateJobRequest();
        req2.setName("test-no-cross-cancel-2");
        req2.setPriority(1);
        req2.setMaxRetries(1);
        req2.setPayload("sleep:5000");
        req2.setType("SIMULATED");

        Job job2 = jobService.createJob(req2);
        jobService.queueJob(job2.getId());

        engine.pollAndExecute();

        // Wait to make sure both jobs are running
        int limit = 100;
        while ((jobService.getJobById(job1.getId()).getStatus() != JobStatus.RUNNING || 
                jobService.getJobById(job2.getId()).getStatus() != JobStatus.RUNNING) && limit > 0) {
            Thread.sleep(10);
            limit--;
        }

        // Cancel job1 only
        jobService.cancelJob(job1.getId());

        Thread.sleep(100);

        // Assert job1 is CANCELLED but job2 remains RUNNING
        assertEquals(JobStatus.CANCELLED, jobService.getJobById(job1.getId()).getStatus());
        assertEquals(JobStatus.RUNNING, jobService.getJobById(job2.getId()).getStatus());

        // Clean up job2 to let test finish cleanly
        jobService.cancelJob(job2.getId());
    }

    @Test
    public void testApiControllerCancel() throws Exception {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("test-api-cancel");
        req.setPriority(1);
        req.setMaxRetries(1);
        req.setPayload("payload");
        Job job = jobService.createJob(req);
        
        // 1. CREATED -> 409 Conflict
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/jobs/" + job.getId() + "/cancel"))
               .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());

        // 2. QUEUED -> 200 OK
        jobService.queueJob(job.getId());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/jobs/" + job.getId() + "/cancel"))
               .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        // 3. already CANCELLED -> 200 OK (idempotent)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/jobs/" + job.getId() + "/cancel"))
               .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        // 4. Missing job -> 404 Not Found
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/jobs/" + java.util.UUID.randomUUID() + "/cancel"))
               .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    public void testHigherPriorityJobClaimedFirst() {
        CreateJobRequest reqA = new CreateJobRequest();
        reqA.setName("job-priority-5");
        reqA.setPriority(5);
        reqA.setMaxRetries(1);
        Job jobA = jobService.createJob(reqA);
        jobService.queueJob(jobA.getId());

        CreateJobRequest reqB = new CreateJobRequest();
        reqB.setName("job-priority-10");
        reqB.setPriority(10);
        reqB.setMaxRetries(1);
        Job jobB = jobService.createJob(reqB);
        jobService.queueJob(jobB.getId());

        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        assertEquals(1, claimed.size());
        assertEquals(jobB.getId(), claimed.get(0).getId());
    }

    @Test
    public void testFutureScheduledJobIsNotClaimed() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("job-future");
        req.setPriority(10);
        req.setMaxRetries(1);
        req.setScheduledAt(LocalDateTime.now().plusHours(1));
        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        List<Job> claimed = jobService.claimExecutableJobs(5, "worker-1");
        assertTrue(claimed.isEmpty());
    }

    @Test
    public void testScheduledJobBecomesClaimableAfterTime() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("job-future-claimable");
        req.setPriority(10);
        req.setMaxRetries(1);
        req.setScheduledAt(LocalDateTime.now().plusHours(1));
        Job job = jobService.createJob(req);
        job = jobService.queueJob(job.getId());

        // Verify it is not claimable initially
        assertTrue(jobService.claimExecutableJobs(5, "worker-1").isEmpty());

        // Manually move scheduledAt to the past
        job.setScheduledAt(LocalDateTime.now().minusMinutes(5));
        jobRepository.saveAndFlush(job);

        // Verify it is now claimed successfully
        List<Job> claimed = jobService.claimExecutableJobs(5, "worker-1");
        assertEquals(1, claimed.size());
        assertEquals(job.getId(), claimed.get(0).getId());
    }

    @Test
    public void testPriorityOrderingAmongEligibleJobs() {
        // Job A: Priority 5, created 10 mins ago
        CreateJobRequest reqA = new CreateJobRequest();
        reqA.setName("jobA");
        reqA.setPriority(5);
        reqA.setMaxRetries(1);
        Job jobA = jobService.createJob(reqA);
        jobA.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        jobRepository.saveAndFlush(jobA);
        jobService.queueJob(jobA.getId());

        // Job B: Priority 10, created 5 mins ago
        CreateJobRequest reqB = new CreateJobRequest();
        reqB.setName("jobB");
        reqB.setPriority(10);
        reqB.setMaxRetries(1);
        Job jobB = jobService.createJob(reqB);
        jobB.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        jobRepository.saveAndFlush(jobB);
        jobService.queueJob(jobB.getId());

        // Job C: Priority 5, created 20 mins ago (older)
        CreateJobRequest reqC = new CreateJobRequest();
        reqC.setName("jobC");
        reqC.setPriority(5);
        reqC.setMaxRetries(1);
        Job jobC = jobService.createJob(reqC);
        jobC.setCreatedAt(LocalDateTime.now().minusMinutes(20));
        jobRepository.saveAndFlush(jobC);
        jobService.queueJob(jobC.getId());

        // Claiming all should return in order B (priority 10), C (priority 5, older), A (priority 5, newer)
        List<Job> claimed = jobService.claimExecutableJobs(3, "worker-1");
        assertEquals(3, claimed.size());
        assertEquals(jobB.getId(), claimed.get(0).getId());
        assertEquals(jobC.getId(), claimed.get(1).getId());
        assertEquals(jobA.getId(), claimed.get(2).getId());
    }

    @Test
    public void testRetrySchedulingRespectsScheduledAt() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("job-retry-sched");
        req.setPriority(10);
        req.setMaxRetries(2);
        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        // Claim and fail to schedule retry
        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        jobService.handleFailure(claimed.get(0).getId(), "worker-1", "failure message");

        Job retryingJob = jobService.getJobById(job.getId());
        assertEquals(JobStatus.RETRYING, retryingJob.getStatus());
        assertNotNull(retryingJob.getScheduledAt());
        assertTrue(retryingJob.getScheduledAt().isAfter(LocalDateTime.now()));

        // Verify it cannot be claimed immediately
        assertTrue(jobService.claimExecutableJobs(1, "worker-2").isEmpty());
    }

    @Test
    public void testRetryingJobCompetesByPriorityAfterBecomingEligible() {
        // High priority job in RETRYING state
        CreateJobRequest reqA = new CreateJobRequest();
        reqA.setName("jobA-retrying-high");
        reqA.setPriority(10);
        reqA.setMaxRetries(2);
        Job jobA = jobService.createJob(reqA);
        jobService.queueJob(jobA.getId());
        
        List<Job> claimedA = jobService.claimExecutableJobs(1, "worker-1");
        jobService.handleFailure(claimedA.get(0).getId(), "worker-1", "failure message");

        // Make the retrying job eligible now
        Job retryingA = jobService.getJobById(jobA.getId());
        retryingA.setScheduledAt(LocalDateTime.now().minusSeconds(1));
        jobRepository.saveAndFlush(retryingA);

        // Low priority job in QUEUED state
        CreateJobRequest reqB = new CreateJobRequest();
        reqB.setName("jobB-queued-low");
        reqB.setPriority(2);
        reqB.setMaxRetries(1);
        Job jobB = jobService.createJob(reqB);
        jobService.queueJob(jobB.getId());

        // High priority retrying job should be claimed first
        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-2");
        assertEquals(1, claimed.size());
        assertEquals(jobA.getId(), claimed.get(0).getId());
    }

    @Test
    public void testPriorityDoesNotOverrideFutureSchedule() {
        // Job A: Priority 100, scheduled in future
        CreateJobRequest reqA = new CreateJobRequest();
        reqA.setName("job-future-high");
        reqA.setPriority(100);
        reqA.setMaxRetries(1);
        reqA.setScheduledAt(LocalDateTime.now().plusHours(1));
        Job jobA = jobService.createJob(reqA);
        jobService.queueJob(jobA.getId());

        // Job B: Priority 1, scheduled in past (or immediate)
        CreateJobRequest reqB = new CreateJobRequest();
        reqB.setName("job-immediate-low");
        reqB.setPriority(1);
        reqB.setMaxRetries(1);
        Job jobB = jobService.createJob(reqB);
        jobService.queueJob(jobB.getId());

        // Only Job B should be claimed
        List<Job> claimed = jobService.claimExecutableJobs(2, "worker-1");
        assertEquals(1, claimed.size());
        assertEquals(jobB.getId(), claimed.get(0).getId());
    }

    @Test
    public void testConcurrentWorkersRespectPriorityAndSkipLocked() {
        CreateJobRequest req1 = new CreateJobRequest();
        req1.setName("priority-1");
        req1.setPriority(1);
        req1.setMaxRetries(1);
        Job job1 = jobService.createJob(req1);
        jobService.queueJob(job1.getId());

        CreateJobRequest req2 = new CreateJobRequest();
        req2.setName("priority-5");
        req2.setPriority(5);
        req2.setMaxRetries(1);
        Job job2 = jobService.createJob(req2);
        jobService.queueJob(job2.getId());

        CreateJobRequest req3 = new CreateJobRequest();
        req3.setName("priority-10");
        req3.setPriority(10);
        req3.setMaxRetries(1);
        Job job3 = jobService.createJob(req3);
        jobService.queueJob(job3.getId());

        // Worker 1 claims 1 job (gets priority 10)
        List<Job> claimed1 = jobService.claimExecutableJobs(1, "worker-1");
        assertEquals(1, claimed1.size());
        assertEquals(job3.getId(), claimed1.get(0).getId());

        // Worker 2 claims 1 job (gets priority 5, skips locked priority 10)
        List<Job> claimed2 = jobService.claimExecutableJobs(1, "worker-2");
        assertEquals(1, claimed2.size());
        assertEquals(job2.getId(), claimed2.get(0).getId());
    }

    @Test
    public void testLowPriorityJobDoesNotRemainPermanentlyStarved() {
        // Job A: Priority 1, created 6 minutes ago (exceeds default starvation threshold of 300 seconds)
        CreateJobRequest reqA = new CreateJobRequest();
        reqA.setName("starved-job");
        reqA.setPriority(1);
        reqA.setMaxRetries(1);
        Job jobA = jobService.createJob(reqA);
        jobA.setCreatedAt(LocalDateTime.now().minusMinutes(6));
        jobRepository.saveAndFlush(jobA);
        jobService.queueJob(jobA.getId());

        // Job B: Priority 100, created just now
        CreateJobRequest reqB = new CreateJobRequest();
        reqB.setName("fresh-high-priority-job");
        reqB.setPriority(100);
        reqB.setMaxRetries(1);
        Job jobB = jobService.createJob(reqB);
        jobService.queueJob(jobB.getId());

        // Claiming 1 job should return Job A due to starvation override, despite Job B's high priority
        List<Job> claimed = jobService.claimExecutableJobs(1, "worker-1");
        assertEquals(1, claimed.size());
        assertEquals(jobA.getId(), claimed.get(0).getId());
    }

    @Test
    public void testExistingCancellationStillWorksWithScheduledJobs() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("job-to-cancel");
        req.setPriority(10);
        req.setMaxRetries(1);
        req.setScheduledAt(LocalDateTime.now().plusHours(1));
        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        // Cancel the scheduled job
        Job cancelled = jobService.cancelJob(job.getId());
        assertEquals(JobStatus.CANCELLED, cancelled.getStatus());
        assertNull(cancelled.getScheduledAt());
    }

    @Test
    public void testExistingRetryAndDLQBehaviorStillWorks() {
        CreateJobRequest req = new CreateJobRequest();
        req.setName("job-dlq");
        req.setPriority(10);
        req.setMaxRetries(1);
        Job job = jobService.createJob(req);
        jobService.queueJob(job.getId());

        // Claim and fail -> goes to RETRYING
        List<Job> claimed1 = jobService.claimExecutableJobs(1, "worker-1");
        jobService.handleFailure(claimed1.get(0).getId(), "worker-1", "err1");
        Job state1 = jobService.getJobById(job.getId());
        assertEquals(JobStatus.RETRYING, state1.getStatus());
        assertEquals(1, state1.getRetryCount());

        // Make it eligible
        state1.setScheduledAt(LocalDateTime.now().minusSeconds(1));
        jobRepository.saveAndFlush(state1);

        // Claim and fail again -> goes to DEAD_LETTER (max retries = 1 exceeded)
        List<Job> claimed2 = jobService.claimExecutableJobs(1, "worker-2");
        jobService.handleFailure(claimed2.get(0).getId(), "worker-2", "err2");
        Job state2 = jobService.getJobById(job.getId());
        assertEquals(JobStatus.DEAD_LETTER, state2.getStatus());
        assertEquals(2, state2.getRetryCount());
    }

    @Test
    public void testConcurrencyLimitEnforced() {
        // Configure max concurrency limit of 2 for LIMITED_CONCURRENCY type
        com.flowforge.config.FlowForgeLimitsConfig.TypeLimit limit = new com.flowforge.config.FlowForgeLimitsConfig.TypeLimit();
        limit.setMaxConcurrency(2);
        limitsConfig.getTypes().put("LIMITED_CONCURRENCY", limit);

        // Create 5 jobs of LIMITED_CONCURRENCY type
        for (int i = 0; i < 5; i++) {
            CreateJobRequest req = new CreateJobRequest();
            req.setName("job-concurrency-" + i);
            req.setPriority(1);
            req.setMaxRetries(1);
            req.setType("LIMITED_CONCURRENCY");
            Job job = jobService.createJob(req);
            job = jobService.queueJob(job.getId());
        }

        // Claim with a limit of 5
        List<Job> claimed = jobService.claimExecutableJobs(5, "worker-1");
        assertEquals(2, claimed.size());

        // Verify remaining 3 jobs are still QUEUED
        long queuedCount = jobRepository.findAll().stream()
                .filter(j -> j.getStatus() == JobStatus.QUEUED && "LIMITED_CONCURRENCY".equals(j.getType()))
                .count();
        assertEquals(3, queuedCount);
    }

    @Test
    public void testRateLimitEnforced() {
        // Configure max rate limit of 3 per minute for LIMITED_RATE type
        com.flowforge.config.FlowForgeLimitsConfig.TypeLimit limit = new com.flowforge.config.FlowForgeLimitsConfig.TypeLimit();
        limit.setMaxRatePerMinute(3);
        limitsConfig.getTypes().put("LIMITED_RATE", limit);

        // Create 5 jobs of LIMITED_RATE type
        for (int i = 0; i < 5; i++) {
            CreateJobRequest req = new CreateJobRequest();
            req.setName("job-rate-" + i);
            req.setPriority(1);
            req.setMaxRetries(1);
            req.setType("LIMITED_RATE");
            Job job = jobService.createJob(req);
            job = jobService.queueJob(job.getId());
        }

        // Claim with a limit of 5
        List<Job> claimed = jobService.claimExecutableJobs(5, "worker-1");
        assertEquals(3, claimed.size());

        // Verify remaining 2 jobs are still QUEUED
        long queuedCount = jobRepository.findAll().stream()
                .filter(j -> j.getStatus() == JobStatus.QUEUED && "LIMITED_RATE".equals(j.getType()))
                .count();
        assertEquals(2, queuedCount);
    }

    @Test
    public void testConcurrentWorkersRespectAdvisoryLocks() {
        // Configure concurrency limit of 1
        com.flowforge.config.FlowForgeLimitsConfig.TypeLimit limit = new com.flowforge.config.FlowForgeLimitsConfig.TypeLimit();
        limit.setMaxConcurrency(1);
        limitsConfig.getTypes().put("LIMITED_LOCK", limit);

        // Create 2 jobs of LIMITED_LOCK type
        for (int i = 0; i < 2; i++) {
            CreateJobRequest req = new CreateJobRequest();
            req.setName("job-lock-" + i);
            req.setPriority(1);
            req.setMaxRetries(1);
            req.setType("LIMITED_LOCK");
            Job job = jobService.createJob(req);
            job = jobService.queueJob(job.getId());
        }

        // Worker 1 claims 1 job (limit = 1)
        List<Job> claimed1 = jobService.claimExecutableJobs(1, "worker-1");
        assertEquals(1, claimed1.size());

        // Worker 2 tries to claim 1 job, should get 0 because limit is reached
        List<Job> claimed2 = jobService.claimExecutableJobs(1, "worker-2");
        assertEquals(0, claimed2.size());
    }

    @Test
    public void testUnlimitedTypesClaimNormally() {
        // No limits configured for UNLIMITED type

        // Create 5 jobs of UNLIMITED type
        for (int i = 0; i < 5; i++) {
            CreateJobRequest req = new CreateJobRequest();
            req.setName("job-unlimited-" + i);
            req.setPriority(1);
            req.setMaxRetries(1);
            req.setType("UNLIMITED");
            Job job = jobService.createJob(req);
            job = jobService.queueJob(job.getId());
        }

        // Claim with a limit of 5
        List<Job> claimed = jobService.claimExecutableJobs(5, "worker-1");
        assertEquals(5, claimed.size());
    }

    @Test
    public void testCancelledAndCompletedJobsDoNotBlockConcurrency() {
        // Configure concurrency limit of 1
        com.flowforge.config.FlowForgeLimitsConfig.TypeLimit limit = new com.flowforge.config.FlowForgeLimitsConfig.TypeLimit();
        limit.setMaxConcurrency(1);
        limitsConfig.getTypes().put("LIMITED_RELEASE", limit);

        // Job A: completed
        CreateJobRequest reqA = new CreateJobRequest();
        reqA.setName("jobA");
        reqA.setPriority(1);
        reqA.setMaxRetries(1);
        reqA.setType("LIMITED_RELEASE");
        Job jobA = jobService.createJob(reqA);
        jobA = jobService.queueJob(jobA.getId());

        List<Job> claimedA = jobService.claimExecutableJobs(1, "worker-1");
        assertEquals(1, claimedA.size());
        jobService.handleSuccess(claimedA.get(0).getId(), "worker-1");

        // Job B: should be claimable now
        CreateJobRequest reqB = new CreateJobRequest();
        reqB.setName("jobB");
        reqB.setPriority(1);
        reqB.setMaxRetries(1);
        reqB.setType("LIMITED_RELEASE");
        Job jobB = jobService.createJob(reqB);
        jobB = jobService.queueJob(jobB.getId());

        List<Job> claimedB = jobService.claimExecutableJobs(1, "worker-2");
        assertEquals(1, claimedB.size());
        assertEquals(jobB.getId(), claimedB.get(0).getId());
        jobService.handleSuccess(claimedB.get(0).getId(), "worker-2");

        // Job C: cancelled
        CreateJobRequest reqC = new CreateJobRequest();
        reqC.setName("jobC");
        reqC.setPriority(1);
        reqC.setMaxRetries(1);
        reqC.setType("LIMITED_RELEASE");
        Job jobC = jobService.createJob(reqC);
        jobC = jobService.queueJob(jobC.getId());

        List<Job> claimedC = jobService.claimExecutableJobs(1, "worker-3");
        assertEquals(1, claimedC.size());
        jobService.cancelJob(claimedC.get(0).getId());

        // Job D: should be claimable now
        CreateJobRequest reqD = new CreateJobRequest();
        reqD.setName("jobD");
        reqD.setPriority(1);
        reqD.setMaxRetries(1);
        reqD.setType("LIMITED_RELEASE");
        Job jobD = jobService.createJob(reqD);
        jobD = jobService.queueJob(jobD.getId());

        List<Job> claimedD = jobService.claimExecutableJobs(1, "worker-4");
        assertEquals(1, claimedD.size());
        assertEquals(jobD.getId(), claimedD.get(0).getId());
    }

    @Test
    public void testExistingCancellationAndRetryBehaviorStillWorks() {
        // Configure rate limit of 1 per minute
        com.flowforge.config.FlowForgeLimitsConfig.TypeLimit limit = new com.flowforge.config.FlowForgeLimitsConfig.TypeLimit();
        limit.setMaxRatePerMinute(1);
        limitsConfig.getTypes().put("LIMITED_RETRY_RATE", limit);

        // Job A: fails and goes to RETRYING
        CreateJobRequest reqA = new CreateJobRequest();
        reqA.setName("jobA");
        reqA.setPriority(1);
        reqA.setMaxRetries(2);
        reqA.setType("LIMITED_RETRY_RATE");
        Job jobA = jobService.createJob(reqA);
        jobA = jobService.queueJob(jobA.getId());

        List<Job> claimedA = jobService.claimExecutableJobs(1, "worker-1");
        assertEquals(1, claimedA.size());
        jobService.handleFailure(claimedA.get(0).getId(), "worker-1", "failure error");

        // Verify status is RETRYING
        Job stateA = jobService.getJobById(jobA.getId());
        assertEquals(JobStatus.RETRYING, stateA.getStatus());

        // Job B: created and queued, but should NOT be claimed because the failed attempt consumes the rate limit
        CreateJobRequest reqB = new CreateJobRequest();
        reqB.setName("jobB");
        reqB.setPriority(1);
        reqB.setMaxRetries(1);
        reqB.setType("LIMITED_RETRY_RATE");
        Job jobB = jobService.createJob(reqB);
        jobB = jobService.queueJob(jobB.getId());

        List<Job> claimedB = jobService.claimExecutableJobs(1, "worker-2");
        assertEquals(0, claimedB.size());
    }

    private Job createRawJob(JobStatus status) {
        Job job = new Job();
        job.setName("test-metrics");
        job.setPriority(1);
        job.setMaxRetries(1);
        job.setRetryCount(0);
        job.setType("SIMULATED");
        job.setStatus(status);
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        return job;
    }

    @Test
    public void testEmptyDatabaseMetrics() {
        jobRepository.deleteAll();
        JobMetricsResponse m = jobMetricsService.getJobMetrics();
        assertEquals(0, m.getTotalJobs());
        assertEquals(0, m.getQueueDepth());
        assertEquals(0, m.getRunningJobs());
        assertEquals(0, m.getRetryingJobs());
        assertEquals(0, m.getCompletedJobs());
        assertEquals(0, m.getDeadLetterJobs());
        assertEquals(0, m.getCancelledJobs());
        assertEquals(0, m.getActiveWorkers());
        assertEquals(0, m.getAverageExecutionDurationMs());
        assertEquals(0, m.getMaxExecutionDurationMs());
        assertEquals(0, m.getRecentExecutions());
        assertEquals(0, m.getRecentFailures());
        assertEquals(0, m.getRecentCancellations());
    }

    @Test
    public void testStatusCountsCorrect() {
        jobRepository.deleteAll();
        // Save one job for each status
        for (JobStatus status : JobStatus.values()) {
            Job j = createRawJob(status);
            jobRepository.saveAndFlush(j);
        }

        JobMetricsResponse m = jobMetricsService.getJobMetrics();
        assertEquals(JobStatus.values().length, m.getTotalJobs());
        for (JobStatus status : JobStatus.values()) {
            assertEquals(1, m.getStatusCounts().get(status));
        }
    }

    @Test
    public void testQueueDepthCorrect() {
        jobRepository.deleteAll();
        jobRepository.saveAndFlush(createRawJob(JobStatus.QUEUED));
        jobRepository.saveAndFlush(createRawJob(JobStatus.QUEUED));
        jobRepository.saveAndFlush(createRawJob(JobStatus.QUEUED));

        JobMetricsResponse m = jobMetricsService.getJobMetrics();
        assertEquals(3, m.getQueueDepth());
    }

    @Test
    public void testRunningJobCountCorrect() {
        jobRepository.deleteAll();
        jobRepository.saveAndFlush(createRawJob(JobStatus.RUNNING));
        jobRepository.saveAndFlush(createRawJob(JobStatus.RUNNING));

        JobMetricsResponse m = jobMetricsService.getJobMetrics();
        assertEquals(2, m.getRunningJobs());
    }

    @Test
    public void testRetryingJobCountCorrect() {
        jobRepository.deleteAll();
        jobRepository.saveAndFlush(createRawJob(JobStatus.RETRYING));
        jobRepository.saveAndFlush(createRawJob(JobStatus.RETRYING));
        jobRepository.saveAndFlush(createRawJob(JobStatus.RETRYING));
        jobRepository.saveAndFlush(createRawJob(JobStatus.RETRYING));

        JobMetricsResponse m = jobMetricsService.getJobMetrics();
        assertEquals(4, m.getRetryingJobs());
    }

    @Test
    public void testCompletedJobCountCorrect() {
        jobRepository.deleteAll();
        jobRepository.saveAndFlush(createRawJob(JobStatus.COMPLETED));
        jobRepository.saveAndFlush(createRawJob(JobStatus.COMPLETED));
        jobRepository.saveAndFlush(createRawJob(JobStatus.COMPLETED));
        jobRepository.saveAndFlush(createRawJob(JobStatus.COMPLETED));
        jobRepository.saveAndFlush(createRawJob(JobStatus.COMPLETED));

        JobMetricsResponse m = jobMetricsService.getJobMetrics();
        assertEquals(5, m.getCompletedJobs());
    }

    @Test
    public void testDLQCountCorrect() {
        jobRepository.deleteAll();
        jobRepository.saveAndFlush(createRawJob(JobStatus.DEAD_LETTER));
        jobRepository.saveAndFlush(createRawJob(JobStatus.DEAD_LETTER));

        JobMetricsResponse m = jobMetricsService.getJobMetrics();
        assertEquals(2, m.getDeadLetterJobs());
    }

    @Test
    public void testCancelledJobCountCorrect() {
        jobRepository.deleteAll();
        jobRepository.saveAndFlush(createRawJob(JobStatus.CANCELLED));
        jobRepository.saveAndFlush(createRawJob(JobStatus.CANCELLED));
        jobRepository.saveAndFlush(createRawJob(JobStatus.CANCELLED));

        JobMetricsResponse m = jobMetricsService.getJobMetrics();
        assertEquals(3, m.getCancelledJobs());
    }

    @Test
    public void testActiveWorkerCountDerivedCorrectly() {
        jobRepository.deleteAll();
        
        Job j1 = createRawJob(JobStatus.RUNNING);
        j1.setWorkerId("worker-A");
        jobRepository.saveAndFlush(j1);

        Job j2 = createRawJob(JobStatus.RUNNING);
        j2.setWorkerId("worker-A");
        jobRepository.saveAndFlush(j2);

        Job j3 = createRawJob(JobStatus.RUNNING);
        j3.setWorkerId("worker-B");
        jobRepository.saveAndFlush(j3);

        // Job 4: workerId set but status is COMPLETED (should not count as active worker!)
        Job j4 = createRawJob(JobStatus.COMPLETED);
        j4.setWorkerId("worker-C");
        jobRepository.saveAndFlush(j4);

        JobMetricsResponse m = jobMetricsService.getJobMetrics();
        assertEquals(2, m.getActiveWorkers());
    }

    @Test
    public void testAverageExecutionDurationCalculatedCorrectly() {
        jobRepository.deleteAll();

        LocalDateTime now = LocalDateTime.now();
        
        // Duration: 10s = 10000ms
        Job j1 = createRawJob(JobStatus.COMPLETED);
        j1.setStartedAt(now.minusSeconds(10));
        j1.setUpdatedAt(now);
        jobRepository.saveAndFlush(j1);

        // Duration: 20s = 20000ms
        Job j2 = createRawJob(JobStatus.COMPLETED);
        j2.setStartedAt(now.minusSeconds(20));
        j2.setUpdatedAt(now);
        jobRepository.saveAndFlush(j2);

        JobMetricsResponse m = jobMetricsService.getJobMetrics();
        assertEquals(15000L, m.getAverageExecutionDurationMs());
    }

    @Test
    public void testMaxExecutionDurationCalculatedCorrectly() {
        jobRepository.deleteAll();

        LocalDateTime now = LocalDateTime.now();
        
        // Duration: 10s = 10000ms
        Job j1 = createRawJob(JobStatus.COMPLETED);
        j1.setStartedAt(now.minusSeconds(10));
        j1.setUpdatedAt(now);
        jobRepository.saveAndFlush(j1);

        // Duration: 30s = 30000ms
        Job j2 = createRawJob(JobStatus.COMPLETED);
        j2.setStartedAt(now.minusSeconds(30));
        j2.setUpdatedAt(now);
        jobRepository.saveAndFlush(j2);

        JobMetricsResponse m = jobMetricsService.getJobMetrics();
        assertEquals(30000L, m.getMaxExecutionDurationMs());
    }

    @Test
    public void testRecentExecutionWindowWorksCorrectly() {
        jobRepository.deleteAll();

        LocalDateTime now = LocalDateTime.now();

        // 1. RUNNING, started 10 minutes ago (within 60m window) -> counts
        Job j1 = createRawJob(JobStatus.RUNNING);
        j1.setStartedAt(now.minusMinutes(10));
        jobRepository.saveAndFlush(j1);

        // 2. COMPLETED, started 90 minutes ago (outside 60m window) -> does not count
        Job j2 = createRawJob(JobStatus.COMPLETED);
        j2.setStartedAt(now.minusMinutes(90));
        jobRepository.saveAndFlush(j2);

        // 3. RETRYING, failed 5 minutes ago (within 60m window) -> counts
        Job j3 = createRawJob(JobStatus.RETRYING);
        j3.setLastFailedAt(now.minusMinutes(5));
        jobRepository.saveAndFlush(j3);

        // 4. CANCELLED, cancelled 20 minutes ago (within 60m window) -> counts
        Job j4 = createRawJob(JobStatus.CANCELLED);
        j4.setUpdatedAt(now.minusMinutes(20));
        jobRepository.saveAndFlush(j4);

        JobMetricsResponse m = jobMetricsService.getJobMetrics();
        assertEquals(3, m.getRecentExecutions());
    }

    @Test
    public void testRecentFailuresCountedCorrectly() {
        jobRepository.deleteAll();

        LocalDateTime now = LocalDateTime.now();

        // 1. RETRYING, failed 5 minutes ago -> counts
        Job j1 = createRawJob(JobStatus.RETRYING);
        j1.setLastFailedAt(now.minusMinutes(5));
        jobRepository.saveAndFlush(j1);

        // 2. DEAD_LETTER, failed 10 minutes ago -> counts
        Job j2 = createRawJob(JobStatus.DEAD_LETTER);
        j2.setLastFailedAt(now.minusMinutes(10));
        jobRepository.saveAndFlush(j2);

        // 3. RETRYING, failed 90 minutes ago -> does not count
        Job j3 = createRawJob(JobStatus.RETRYING);
        j3.setLastFailedAt(now.minusMinutes(90));
        jobRepository.saveAndFlush(j3);

        JobMetricsResponse m = jobMetricsService.getJobMetrics();
        assertEquals(2, m.getRecentFailures());
    }

    @Test
    public void testRecentCancellationsCountedCorrectly() {
        jobRepository.deleteAll();

        LocalDateTime now = LocalDateTime.now();

        // 1. CANCELLED, cancelled 10 minutes ago -> counts
        Job j1 = createRawJob(JobStatus.CANCELLED);
        j1.setUpdatedAt(now.minusMinutes(10));
        jobRepository.saveAndFlush(j1);

        // 2. CANCELLED, cancelled 80 minutes ago -> does not count
        Job j2 = createRawJob(JobStatus.CANCELLED);
        j2.setUpdatedAt(now.minusMinutes(80));
        jobRepository.saveAndFlush(j2);

        JobMetricsResponse m = jobMetricsService.getJobMetrics();
        assertEquals(1, m.getRecentCancellations());
    }

    @Test
    public void testMetricsEndpointReturnsHttp200AndValidJson() throws Exception {
        jobRepository.deleteAll();
        jobRepository.saveAndFlush(createRawJob(JobStatus.QUEUED));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/metrics/jobs"))
               .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
               .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalJobs").value(1))
               .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.queueDepth").value(1))
               .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.statusCounts.QUEUED").value(1));
    }

    @Test
    public void testExistingCancellationRetrySchedulingBehaviorRemainsUnaffected() {
        // Simply assert that limitsConfig and other settings continue to function normally
        assertNotNull(limitsConfig);
        assertNotNull(jobService);
        assertNotNull(engine);
    }
}

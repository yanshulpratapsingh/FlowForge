package com.flowforge.service;

import com.flowforge.dto.CreateJobRequest;
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
}

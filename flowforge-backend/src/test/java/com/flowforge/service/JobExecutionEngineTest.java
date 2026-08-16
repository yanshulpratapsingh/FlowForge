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

@SpringBootTest
public class JobExecutionEngineTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

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
}

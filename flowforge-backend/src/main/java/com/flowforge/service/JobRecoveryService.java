package com.flowforge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(JobRecoveryService.class);

    private final JobService jobService;

    public JobRecoveryService(JobService jobService) {
        this.jobService = jobService;
    }

    @Scheduled(fixedDelayString = "${flowforge.worker.recovery-interval-ms:5000}")
    public void recoverExpiredLeases() {
        log.trace("Running expired lease recovery scanner...");
        try {
            jobService.recoverExpiredJobs();
        } catch (Exception e) {
            log.error("Failed to run lease recovery scanner", e);
        }
    }
}

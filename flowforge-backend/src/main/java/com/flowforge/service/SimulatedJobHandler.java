package com.flowforge.service;

import com.flowforge.entity.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SimulatedJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(SimulatedJobHandler.class);

    @Override
    public String getJobType() {
        return "SIMULATED";
    }

    @Override
    public void handle(Job job) throws Exception {
        log.info("Worker started - [Thread: {}] - Job ID: {}, Name: {}",
                Thread.currentThread().getName(), job.getId(), job.getName());

        long sleepTimeMs = 1000;
        String payload = job.getPayload();
        if (payload != null && payload.startsWith("sleep:")) {
            try {
                sleepTimeMs = Long.parseLong(payload.substring(6).trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid sleep payload format: {}, defaulting to 1000ms", payload);
            }
        }

        try {
            log.info("Worker [Thread: {}] sleeping for {}ms for Job ID: {}",
                    Thread.currentThread().getName(), sleepTimeMs, job.getId());
            Thread.sleep(sleepTimeMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Job execution interrupted", e);
        }

        if (payload != null && (payload.toLowerCase().contains("fail") || payload.toLowerCase().contains("error"))) {
            log.error("Worker failed - [Thread: {}] - Job ID: {}, Name: {} - Simulated failure triggered",
                    Thread.currentThread().getName(), job.getId(), job.getName());
            throw new RuntimeException("Simulated execution failure for job: " + job.getId());
        }

        log.info("Worker completed - [Thread: {}] - Job ID: {}, Name: {}",
                Thread.currentThread().getName(), job.getId(), job.getName());
    }
}

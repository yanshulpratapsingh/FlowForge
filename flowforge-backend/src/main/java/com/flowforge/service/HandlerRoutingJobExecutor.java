package com.flowforge.service;

import com.flowforge.entity.Job;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class HandlerRoutingJobExecutor implements JobExecutor {

    private final JobHandlerRegistry registry;

    public HandlerRoutingJobExecutor(JobHandlerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void execute(Job job) throws Exception {
        if (job == null) {
            throw new IllegalArgumentException("Job cannot be null");
        }
        JobHandler handler = registry.getHandler(job.getType());
        handler.handle(job);
    }
}

package com.flowforge.service;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class WorkerIdentity {

    private final String workerId;

    public WorkerIdentity() {
        this.workerId = "worker-" + UUID.randomUUID().toString();
    }

    public String getWorkerId() {
        return workerId;
    }
}

package com.flowforge.service;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JobHandlerRegistry {
    private final Map<String, JobHandler> handlers = new ConcurrentHashMap<>();

    public JobHandlerRegistry(List<JobHandler> registeredHandlers) {
        for (JobHandler handler : registeredHandlers) {
            register(handler);
        }
    }

    public void register(JobHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        String type = handler.getJobType();
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Handler type cannot be null or empty");
        }
        String normalizedType = type.trim().toUpperCase();
        if (handlers.containsKey(normalizedType)) {
            throw new IllegalStateException("Duplicate handler registration for type: " + normalizedType);
        }
        handlers.put(normalizedType, handler);
    }

    public JobHandler getHandler(String type) {
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Job type cannot be null or empty");
        }
        String normalizedType = type.trim().toUpperCase();
        JobHandler handler = handlers.get(normalizedType);
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for job type: " + normalizedType);
        }
        return handler;
    }
}

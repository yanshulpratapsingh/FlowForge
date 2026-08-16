package com.flowforge.service;

import com.flowforge.entity.Job;

public interface JobHandler {
    String getJobType();
    void handle(Job job) throws Exception;
}

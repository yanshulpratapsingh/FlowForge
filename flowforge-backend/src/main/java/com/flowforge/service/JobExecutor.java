package com.flowforge.service;

import com.flowforge.entity.Job;

public interface JobExecutor {
    void execute(Job job) throws Exception;
}

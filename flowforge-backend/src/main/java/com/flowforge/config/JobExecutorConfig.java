package com.flowforge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class JobExecutorConfig {

    @Value("${flowforge.worker.core-pool-size:4}")
    private int corePoolSize;

    @Value("${flowforge.worker.max-pool-size:8}")
    private int maxPoolSize;

    @Value("${flowforge.worker.queue-capacity:100}")
    private int queueCapacity;

    @Bean(name = "jobExecutor")
    public Executor jobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("JobWorker-");
        executor.initialize();
        return executor;
    }
}

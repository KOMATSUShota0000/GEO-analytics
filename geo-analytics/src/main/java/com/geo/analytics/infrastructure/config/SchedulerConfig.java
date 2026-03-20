package com.geo.analytics.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

@Configuration
public class SchedulerConfig {
    @Bean
    public TaskScheduler virtualThreadTaskScheduler() {
        SimpleAsyncTaskScheduler taskScheduler = new SimpleAsyncTaskScheduler();
        taskScheduler.setVirtualThreads(true);
        taskScheduler.setThreadNamePrefix("batch-scheduler-vt-");
        return taskScheduler;
    }
}

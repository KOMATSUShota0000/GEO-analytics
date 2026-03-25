package com.geo.analytics.infrastructure.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class StreamingExecutorConfig {
    public static final String STREAM_DELIVERY_VIRTUAL_EXECUTOR = "streamDeliveryVirtualExecutor";
    public static final String JOB_SSE_HEARTBEAT_SCHEDULER = "jobSseHeartbeatScheduler";

    @Bean(destroyMethod = "shutdown")
    @Qualifier(STREAM_DELIVERY_VIRTUAL_EXECUTOR)
    ExecutorService streamDeliveryVirtualExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(destroyMethod = "shutdown")
    @Qualifier(JOB_SSE_HEARTBEAT_SCHEDULER)
    ScheduledExecutorService jobSseHeartbeatScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "job-sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }
}

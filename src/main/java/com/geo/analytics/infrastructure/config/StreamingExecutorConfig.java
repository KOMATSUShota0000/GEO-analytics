package com.geo.analytics.infrastructure.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class StreamingExecutorConfig {
    public static final String STREAM_DELIVERY_VIRTUAL_EXECUTOR = "streamDeliveryVirtualExecutor";
    /** リアルタイム検証のクエリ単位並列（ハイブリッド継続とは別プール。単一スレッドの stream 配信と共有するとデッドロックする） */
    public static final String REALTIME_PARALLEL_VIRTUAL_EXECUTOR = "realtimeParallelVirtualExecutor";
    public static final String JOB_SSE_HEARTBEAT_SCHEDULER = "jobSseHeartbeatScheduler";

    /**
     * 本番は仮想スレッド・タスク毎。一部のテスト／CIでは {@code newVirtualThreadPerTaskExecutor} 上の
     * {@code CompletableFuture.runAsync} がディスパッチされないことがあるため、専用の単一ワーカーで
     * ストリーム配信・ハイブリッド継続などを直列化するオプションを用意する。
     */
    @Bean(destroyMethod = "shutdown")
    @Qualifier(STREAM_DELIVERY_VIRTUAL_EXECUTOR)
    ExecutorService streamDeliveryVirtualExecutor(
            @Value("${app.streaming.stream-delivery-dedicated-thread:false}") boolean dedicatedThread) {
        if (dedicatedThread) {
            return Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "stream-delivery-dedicated");
                thread.setDaemon(true);
                return thread;
            });
        }
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(destroyMethod = "shutdown")
    @Qualifier(REALTIME_PARALLEL_VIRTUAL_EXECUTOR)
    ExecutorService realtimeParallelVirtualExecutor() {
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

package com.geo.analytics.infrastructure.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BlockingBucket;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

/**
 * Global throughput limit for AI visibility (AIO) provider calls: shared bucket (≈2 req/s)
 * plus in-flight cap to protect carrier threads.
 */
@Component
public final class SerpApiGlobalRequestGate {

    private static final String SERP_BUCKET_KEY = "__global_serpapi__";
    private static final int MAX_IN_FLIGHT = 2;

    private final BlockingBucket blockingBucket;
    private final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT, true);

    public SerpApiGlobalRequestGate(@Qualifier("rateLimitProxyManager") ProxyManager<String> proxyManager) {
        Objects.requireNonNull(proxyManager, "proxyManager");
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(MAX_IN_FLIGHT)
                        .refillGreedy(MAX_IN_FLIGHT, Duration.ofSeconds(1))
                        .build())
                .build();
        Bucket bucket = proxyManager.builder().build(SERP_BUCKET_KEY, () -> configuration);
        this.blockingBucket = bucket.asBlocking();
    }

    public <T> T execute(Callable<T> action) throws Exception {
        blockingBucket.consume(1L);
        inFlight.acquire();
        try {
            return action.call();
        } finally {
            inFlight.release();
        }
    }
}

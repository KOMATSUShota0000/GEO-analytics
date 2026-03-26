package com.geo.analytics.infrastructure.config;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.remote.RemoteBucketState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;
@Configuration
public class Bucket4jConfiguration {
@Bean
public ProxyManager<String> rateLimitProxyManager() {
@SuppressWarnings("unchecked")
Caffeine<String, RemoteBucketState> caffeine = (Caffeine<String, RemoteBucketState>) (Caffeine<?, ?>) Caffeine.newBuilder().maximumSize(10000L);
return new CaffeineProxyManager<>(caffeine, Duration.ofDays(1L));
}
}
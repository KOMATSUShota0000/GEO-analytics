package com.geo.analytics.infrastructure.crawler;

import io.lettuce.core.api.StatefulRedisConnection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class TieredCrawlCache {
    private final StatefulRedisConnection<String, String> redisConnection;
    private final ConcurrentHashMap<String, LocalCacheEntry> localEntriesByCacheKey = new ConcurrentHashMap<>();

    public TieredCrawlCache(StatefulRedisConnection<String, String> redisConnection) {
        this.redisConnection = redisConnection;
    }

    public Optional<String> get(String cacheKey) {
        if (redisConnection != null) {
            try {
                String fromRedis = redisConnection.sync().get(cacheKey);
                if (fromRedis != null && !fromRedis.isEmpty()) {
                    return Optional.of(fromRedis);
                }
            } catch (Exception ignored) {
            }
        }
        LocalCacheEntry localCacheEntry = localEntriesByCacheKey.get(cacheKey);
        if (localCacheEntry == null || localCacheEntry.expired()) {
            localEntriesByCacheKey.remove(cacheKey);
            return Optional.empty();
        }
        return Optional.of(localCacheEntry.jsonPayload());
    }

    public void put(String cacheKey, String jsonPayload, long ttlSeconds) {
        long boundedTtlSeconds = Math.max(1, ttlSeconds);
        if (redisConnection != null) {
            try {
                redisConnection.sync().setex(cacheKey, boundedTtlSeconds, jsonPayload);
                return;
            } catch (Exception ignored) {
            }
        }
        long expiresAtMillis = System.currentTimeMillis() + boundedTtlSeconds * 1000L;
        localEntriesByCacheKey.put(cacheKey, new LocalCacheEntry(jsonPayload, expiresAtMillis));
    }

    public void invalidate(String cacheKey) {
        if (redisConnection != null) {
            try {
                redisConnection.sync().del(cacheKey);
            } catch (Exception ignored) {
            }
        }
        localEntriesByCacheKey.remove(cacheKey);
    }

    private record LocalCacheEntry(String jsonPayload, long expiresAtMillis) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }
}

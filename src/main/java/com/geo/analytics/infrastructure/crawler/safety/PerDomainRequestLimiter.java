package com.geo.analytics.infrastructure.crawler.safety;

import com.geo.analytics.domain.exception.SsrFBlockedException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public final class PerDomainRequestLimiter {
    private static final int MAX_PER_HOST = 2;
    private final ConcurrentHashMap<String, Semaphore> map = new ConcurrentHashMap<>();
    public void acquire(String host) {
        if (host == null || host.isEmpty()) {
            return;
        }
        String k = host.toLowerCase(Locale.ROOT);
        try {
            map.computeIfAbsent(k, h -> new Semaphore(MAX_PER_HOST)).acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SsrFBlockedException("interrupted", host);
        }
    }
    public void release(String host) {
        if (host == null || host.isEmpty()) {
            return;
        }
        String k = host.toLowerCase(Locale.ROOT);
        Semaphore s = map.get(k);
        if (s != null) {
            s.release();
        }
    }
}

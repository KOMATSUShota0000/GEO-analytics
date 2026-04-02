package com.geo.analytics.domain.exception;

import io.github.bucket4j.ConsumptionProbe;

public class RateLimitExceededException extends RuntimeException {
    private final ConsumptionProbe probe;
    private final int currentLimit;
    private final String planName;

    public RateLimitExceededException(ConsumptionProbe probe, int currentLimit, String planName) {
        super("rate limit exceeded");
        this.probe = probe;
        this.currentLimit = currentLimit;
        this.planName = planName;
    }

    public ConsumptionProbe getProbe() {
        return probe;
    }

    public int getCurrentLimit() {
        return currentLimit;
    }

    public String getPlanName() {
        return planName;
    }
}

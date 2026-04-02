package com.geo.analytics.domain.exception;

public class InsufficientQuotaException extends RuntimeException {
    private final int currentLimit;
    private final String planName;

    public InsufficientQuotaException(String message, int currentLimit, String planName) {
        super(message);
        this.currentLimit = currentLimit;
        this.planName = planName;
    }

    public int getCurrentLimit() {
        return currentLimit;
    }

    public String getPlanName() {
        return planName;
    }
}

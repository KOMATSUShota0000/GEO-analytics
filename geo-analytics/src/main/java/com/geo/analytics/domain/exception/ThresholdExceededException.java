package com.geo.analytics.domain.exception;

public class ThresholdExceededException extends RuntimeException {
    private final int threshold;

    public ThresholdExceededException(int threshold) {
        this.threshold = threshold;
    }

    public int getThreshold() {
        return threshold;
    }
}

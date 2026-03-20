package com.geo.analytics.infrastructure.ai;

public class GeminiBatchApiException extends RuntimeException {
    public GeminiBatchApiException(String message) {
        super(message);
    }

    public GeminiBatchApiException(String message, Throwable cause) {
        super(message, cause);
    }
}

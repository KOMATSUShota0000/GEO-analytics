package com.geo.analytics.domain.exception;

public class TaskRegenerationTooManyRequestsException extends RuntimeException {
    public TaskRegenerationTooManyRequestsException() {
        super("task regeneration rate limited");
    }
}

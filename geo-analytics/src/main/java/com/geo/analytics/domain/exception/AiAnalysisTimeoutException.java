package com.geo.analytics.domain.exception;

public class AiAnalysisTimeoutException extends RuntimeException {
    public AiAnalysisTimeoutException() {
        super("no successful model responses");
    }
}

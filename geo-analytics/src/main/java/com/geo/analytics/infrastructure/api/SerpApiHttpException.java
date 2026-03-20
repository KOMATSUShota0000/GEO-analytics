package com.geo.analytics.infrastructure.api;

public class SerpApiHttpException extends RuntimeException {
    private final int statusCode;
    private final String errorResponseBody;

    public SerpApiHttpException(int statusCode, String statusText, String errorResponseBody, Throwable cause) {
        super(
            "SerpApi HTTP " + statusCode + " " + statusText + " errorBody=" + errorResponseBody,
            cause);
        this.statusCode = statusCode;
        this.errorResponseBody = errorResponseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorResponseBody() {
        return errorResponseBody;
    }
}

package com.geo.analytics.infrastructure.persistence;

public class JsonbSerializationException extends RuntimeException {
    public JsonbSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}

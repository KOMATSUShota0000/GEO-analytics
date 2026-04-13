package com.geo.analytics.infrastructure.security;

public class JwtTokenException extends RuntimeException {

    public JwtTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}

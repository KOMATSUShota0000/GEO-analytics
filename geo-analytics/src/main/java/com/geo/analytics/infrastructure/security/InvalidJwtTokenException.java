package com.geo.analytics.infrastructure.security;

public class InvalidJwtTokenException extends JwtTokenException {

    public InvalidJwtTokenException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidJwtTokenException(String message) {
        super(message, null);
    }
}

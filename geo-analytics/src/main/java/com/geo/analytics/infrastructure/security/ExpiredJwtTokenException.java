package com.geo.analytics.infrastructure.security;

public class ExpiredJwtTokenException extends JwtTokenException {

    public ExpiredJwtTokenException(Throwable cause) {
        super("JWT expired", cause);
    }
}

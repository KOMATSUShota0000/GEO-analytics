package com.geo.analytics.domain.exception;

public class InsufficientCreditException extends RuntimeException {
    public InsufficientCreditException() {
        super("insufficient credit");
    }
}

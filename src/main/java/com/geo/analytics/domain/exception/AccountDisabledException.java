package com.geo.analytics.domain.exception;

/** アカウントが無効化されている。 */
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("このアカウントは利用できません。");
    }

    public AccountDisabledException(String message) {
        super(message);
    }

    public AccountDisabledException(String message, Throwable cause) {
        super(message, cause);
    }
}

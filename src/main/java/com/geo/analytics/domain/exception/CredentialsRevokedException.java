package com.geo.analytics.domain.exception;

/** 認証情報（API キー、リフレッシュトークン等）が失効した場合。 */
public class CredentialsRevokedException extends RuntimeException {

    public CredentialsRevokedException() {
        super("認証情報が失効しています。再度ログインしてください。");
    }

    public CredentialsRevokedException(String message) {
        super(message);
    }

    public CredentialsRevokedException(String message, Throwable cause) {
        super(message, cause);
    }
}

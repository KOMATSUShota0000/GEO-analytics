package com.geo.analytics.domain.exception;

/** アクセストークン（またはセッション相当）の有効期限切れ。 */
public class TokenExpiredException extends RuntimeException {

    public TokenExpiredException() {
        super("アクセストークンの有効期限が切れています。");
    }

    public TokenExpiredException(String message) {
        super(message);
    }

    public TokenExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}

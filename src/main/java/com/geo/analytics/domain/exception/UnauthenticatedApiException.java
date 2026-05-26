package com.geo.analytics.domain.exception;

/**
 * 認証情報がなく保護リソースにアクセスした場合（Spring Security の {@code AuthenticationEntryPoint} 等から送出）。
 */
public class UnauthenticatedApiException extends RuntimeException {

    public UnauthenticatedApiException() {
        super("認証が必要です。");
    }

    public UnauthenticatedApiException(String message) {
        super(message);
    }

    public UnauthenticatedApiException(String message, Throwable cause) {
        super(message, cause);
    }
}

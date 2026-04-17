package com.geo.analytics.domain.exception;

/**
 * 認証済みだが当該操作の権限がない場合（Spring Security の {@code AccessDeniedHandler} 等から送出）。
 */
public class ForbiddenApiException extends RuntimeException {

    public ForbiddenApiException() {
        super("この操作を行う権限がありません。");
    }

    public ForbiddenApiException(String message) {
        super(message);
    }

    public ForbiddenApiException(String message, Throwable cause) {
        super(message, cause);
    }
}

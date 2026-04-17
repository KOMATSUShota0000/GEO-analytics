package com.geo.analytics.domain.exception;

/** サーバー側でセッションが失効・失効扱いとされた場合。 */
public class SessionRevokedException extends RuntimeException {

    public SessionRevokedException() {
        super("セッションが無効化されています。再度ログインしてください。");
    }

    public SessionRevokedException(String message) {
        super(message);
    }

    public SessionRevokedException(String message, Throwable cause) {
        super(message, cause);
    }
}

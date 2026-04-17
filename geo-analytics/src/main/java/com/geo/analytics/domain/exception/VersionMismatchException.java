package com.geo.analytics.domain.exception;

/** クライアントの API / アプリバージョンがサーバー要件を満たさない。 */
public class VersionMismatchException extends RuntimeException {

    public VersionMismatchException() {
        super("クライアントのバージョンが古いか、サーバーと一致しません。アプリを更新してください。");
    }

    public VersionMismatchException(String message) {
        super(message);
    }

    public VersionMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}

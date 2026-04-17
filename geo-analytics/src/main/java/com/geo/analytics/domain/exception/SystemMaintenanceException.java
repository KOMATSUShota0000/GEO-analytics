package com.geo.analytics.domain.exception;

/** メンテナンスモード等によりサービスが利用できない。 */
public class SystemMaintenanceException extends RuntimeException {

    public SystemMaintenanceException() {
        super("現在メンテナンス中です。しばらくしてから再度お試しください。");
    }

    public SystemMaintenanceException(String message) {
        super(message);
    }

    public SystemMaintenanceException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.geo.analytics.domain.exception;

/** テナントの利用が一時停止されている。 */
public class TenantSuspendedException extends RuntimeException {

    public TenantSuspendedException() {
        super("組織の利用が一時停止されています。管理者にお問い合わせください。");
    }

    public TenantSuspendedException(String message) {
        super(message);
    }

    public TenantSuspendedException(String message, Throwable cause) {
        super(message, cause);
    }
}

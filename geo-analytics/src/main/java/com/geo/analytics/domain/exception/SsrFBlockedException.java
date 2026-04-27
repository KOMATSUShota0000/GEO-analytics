package com.geo.analytics.domain.exception;

public class SsrFBlockedException extends RuntimeException {
    private final String blockReason;
    private final String hostname;
    public SsrFBlockedException(String blockReason, String hostname) {
        super(blockReason + " " + (hostname == null ? "" : hostname));
        this.blockReason = blockReason;
        this.hostname = hostname;
    }
    public String getBlockReason() {
        return blockReason;
    }
    public String getHostname() {
        return hostname;
    }
}

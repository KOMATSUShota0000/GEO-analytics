package com.geo.analytics.infrastructure.crawler.extraction;

public class StreamSizeLimitExceededException extends RuntimeException {
    private final long maxBytes;
    private final long readBeforeFailure;
    public StreamSizeLimitExceededException(long maxBytes, long readBeforeFailure) {
        super("size limit " + maxBytes + " " + readBeforeFailure);
        this.maxBytes = maxBytes;
        this.readBeforeFailure = readBeforeFailure;
    }
    public long getMaxBytes() {
        return maxBytes;
    }
    public long getReadBeforeFailure() {
        return readBeforeFailure;
    }
}

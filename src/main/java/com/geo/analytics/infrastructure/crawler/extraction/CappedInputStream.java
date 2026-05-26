package com.geo.analytics.infrastructure.crawler.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class CappedInputStream extends InputStream {
    private final InputStream in;
    private final long maxBytes;
    private long readTotal;
    private boolean failed;
    public CappedInputStream(InputStream in, long maxBytes) {
        this.in = Objects.requireNonNull(in, "in");
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes");
        }
        this.maxBytes = maxBytes;
    }
    @Override
    public int read() throws IOException {
        if (failed) {
            return -1;
        }
        if (readTotal >= maxBytes) {
            fail();
        }
        int b = in.read();
        if (b != -1) {
            readTotal++;
        }
        return b;
    }
    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);
        if (len == 0) {
            return 0;
        }
        if (failed) {
            return -1;
        }
        if (readTotal >= maxBytes) {
            fail();
        }
        int allowed = (int) Math.min(len, maxBytes - readTotal);
        if (allowed == 0) {
            fail();
        }
        int n = in.read(b, off, allowed);
        if (n > 0) {
            readTotal += n;
        }
        return n;
    }
    @Override
    public void close() throws IOException {
        in.close();
    }
    private void fail() {
        failed = true;
        try {
            in.close();
        } catch (IOException e) {
        }
        throw new StreamSizeLimitExceededException(maxBytes, readTotal);
    }
}

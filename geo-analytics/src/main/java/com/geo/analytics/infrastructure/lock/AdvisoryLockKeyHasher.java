package com.geo.analytics.infrastructure.lock;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 論理ロックキーを PostgreSQL {@code pg_advisory_lock(bigint)} 用の 64bit に写像する。
 * SHA-256 の先頭 8 バイトを big-endian {@code long} として解釈する（衝突確率は実用上無視可能）。
 */
final class AdvisoryLockKeyHasher {

    private AdvisoryLockKeyHasher() {}

    static long toAdvisoryLong(String lockKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(lockKey.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

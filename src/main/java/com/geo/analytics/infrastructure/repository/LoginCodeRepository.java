package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.LoginCode;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginCodeRepository extends JpaRepository<LoginCode, UUID> {

    // Why: 「探してから挿入」だと同時の要求で主キーが衝突する。1文で上書きし、古いコードの失敗回数・使用済みも戻す。
    // 呼び出し側（LoginCodeStore）のトランザクション内で呼ぶ。RLS の組織IDはトランザクション内でしか渡らない。
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    INSERT INTO login_codes (user_id, organization_id, code_hash, issued_at, expires_at, failed_attempts, consumed_at)
                    VALUES (:userId, :organizationId, :codeHash, :issuedAt, :expiresAt, 0, NULL)
                    ON CONFLICT (user_id) DO UPDATE SET
                        organization_id = EXCLUDED.organization_id,
                        code_hash = EXCLUDED.code_hash,
                        issued_at = EXCLUDED.issued_at,
                        expires_at = EXCLUDED.expires_at,
                        failed_attempts = 0,
                        consumed_at = NULL
                    """,
            nativeQuery = true)
    int upsert(
            @Param("userId") UUID userId,
            @Param("organizationId") UUID organizationId,
            @Param("codeHash") String codeHash,
            @Param("issuedAt") Instant issuedAt,
            @Param("expiresAt") Instant expiresAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM LoginCode c WHERE c.userId = :userId")
    Optional<LoginCode> findForUpdate(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "UPDATE login_codes SET failed_attempts = failed_attempts + 1 WHERE user_id = :userId AND consumed_at IS NULL",
            nativeQuery = true)
    int recordFailure(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "UPDATE login_codes SET consumed_at = :consumedAt WHERE user_id = :userId AND consumed_at IS NULL",
            nativeQuery = true)
    int markConsumed(@Param("userId") UUID userId, @Param("consumedAt") Instant consumedAt);
}

package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.UserSession;
import com.geo.analytics.infrastructure.persistence.GlobalAccess;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM user_sessions WHERE user_id = :userId", nativeQuery = true)
    int deleteAllByUserId(@Param("userId") UUID userId);

    @Transactional(readOnly = true)
    long countByUserId(UUID userId);

    @Transactional(readOnly = true)
    long countByUserIdAndDeletedAtIsNull(UUID userId);

    @Transactional(readOnly = true)
    @Query("SELECT u.sessionId FROM UserSession u WHERE u.userId = :userId AND u.deletedAt IS NULL")
    List<UUID> findActiveSessionIdsByUserId(@Param("userId") UUID userId);

    @GlobalAccess
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "UPDATE UserSession u SET u.deletedAt = :deletedAt WHERE u.userId = :userId AND u.deletedAt IS NULL")
    int softDeleteAllActiveByUserId(@Param("userId") UUID userId, @Param("deletedAt") Instant deletedAt);

    @Transactional(readOnly = true)
    Optional<UserSession> findBySessionId(UUID sessionId);
}

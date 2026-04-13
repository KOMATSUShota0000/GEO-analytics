package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.UserSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    @Modifying
    @Query(value = "DELETE FROM user_sessions WHERE user_id = :userId", nativeQuery = true)
    int deleteAllByUserId(@Param("userId") UUID userId);

    long countByUserId(UUID userId);

    Optional<UserSession> findBySessionId(UUID sessionId);
}

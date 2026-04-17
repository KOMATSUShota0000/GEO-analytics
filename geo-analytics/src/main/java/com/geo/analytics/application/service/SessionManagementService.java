package com.geo.analytics.application.service;

import com.geo.analytics.application.event.UserSessionEvictedEvent;
import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.domain.entity.UserSession;
import com.geo.analytics.infrastructure.persistence.GlobalAccess;
import com.geo.analytics.infrastructure.repository.OrganizationUserRepository;
import com.geo.analytics.infrastructure.repository.UserSessionRepository;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 認証直後や PDF レンダリング用 JWT など、ワークスペース未確定の経路から呼ばれ得るセッション操作。
 * {@link GlobalAccess} は「テナント未束縛でもよい」ことを明示するためメソッドに限定し、
 * 通常リクエストでは {@link com.geo.analytics.infrastructure.tenant.TenantContextHolder} が束縛されていれば RLS 用セッション変数は従来どおり設定される。
 * {@code user_sessions} のシステム操作は {@link com.geo.analytics.infrastructure.persistence.RlsConnectionInterceptor} が
 * 付与する {@code app.rls_bypass} により DB 側で許可される。
 */
@Service
public class SessionManagementService {

    private final OrganizationUserRepository organizationUserRepository;
    private final UserSessionRepository userSessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Cache<UUID, Boolean> userSessionsCache;

    public SessionManagementService(
            OrganizationUserRepository organizationUserRepository,
            UserSessionRepository userSessionRepository,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("userSessionsCache") Cache<UUID, Boolean> userSessionsCache) {
        this.organizationUserRepository = organizationUserRepository;
        this.userSessionRepository = userSessionRepository;
        this.eventPublisher = eventPublisher;
        this.userSessionsCache = userSessionsCache;
    }

    @GlobalAccess
    @Transactional
    public UUID createNewSession(UUID userId) {
        OrganizationUser user = organizationUserRepository
                .findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("OrganizationUser not found"));
        UUID organizationId = Objects.requireNonNull(user.getOrganizationId(), "organizationId");
        List<UUID> revokedSessionIds = userSessionRepository.findActiveSessionIdsByUserId(userId);
        if (!revokedSessionIds.isEmpty()) {
            userSessionRepository.softDeleteAllActiveByUserId(userId, Instant.now());
            userSessionRepository.flush();
        }
        revokedSessionIds.forEach(userSessionsCache::invalidate);
        eventPublisher.publishEvent(
                new UserSessionEvictedEvent(userId, organizationId, revokedSessionIds));
        UUID sessionId = UUID.randomUUID();
        UserSession row = new UserSession();
        row.setOrganizationId(organizationId);
        row.setUserId(user.getId());
        row.setSessionId(sessionId);
        row.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        userSessionRepository.save(row);
        return sessionId;
    }

    /**
     * 人間の既存セッション行は消さずに PDF ボット用の追加行を挿入する。{@code userSessionsCache} は
     * {@code sessionId} キーで当該ボット JWT のみを高速検証できるようにする。
     */
    @GlobalAccess
    @Transactional
    public UUID appendRenderingSession(UUID userId) {
        OrganizationUser user = organizationUserRepository
                .findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("OrganizationUser not found"));
        UUID sessionId = UUID.randomUUID();
        UserSession row = new UserSession();
        row.setOrganizationId(user.getOrganizationId());
        row.setUserId(user.getId());
        row.setSessionId(sessionId);
        row.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        userSessionRepository.save(row);
        userSessionsCache.put(sessionId, Boolean.TRUE);
        return sessionId;
    }

    @GlobalAccess
    @Transactional(readOnly = true)
    public long countActiveSessionsForUser(UUID userId) {
        return userSessionRepository.countByUserIdAndDeletedAtIsNull(userId);
    }

    @GlobalAccess
    @Transactional(readOnly = true)
    public Optional<UserSession> findActiveSessionBySessionId(UUID sessionId) {
        return userSessionRepository.findBySessionId(sessionId).filter(s -> s.getDeletedAt() == null);
    }
}

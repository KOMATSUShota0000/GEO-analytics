package com.geo.analytics.application.service;

import com.geo.analytics.application.event.UserSessionEvictedEvent;
import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.domain.entity.UserSession;
import com.geo.analytics.infrastructure.repository.OrganizationUserRepository;
import com.geo.analytics.infrastructure.repository.UserSessionRepository;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionManagementService {

    private final OrganizationUserRepository organizationUserRepository;
    private final UserSessionRepository userSessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Cache<UUID, UUID> userSessionsCache;

    public SessionManagementService(
            OrganizationUserRepository organizationUserRepository,
            UserSessionRepository userSessionRepository,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("userSessionsCache") Cache<UUID, UUID> userSessionsCache) {
        this.organizationUserRepository = organizationUserRepository;
        this.userSessionRepository = userSessionRepository;
        this.eventPublisher = eventPublisher;
        this.userSessionsCache = userSessionsCache;
    }

    @Transactional
    public UUID createNewSession(UUID userId) {
        OrganizationUser user = organizationUserRepository
                .findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("OrganizationUser not found"));
        userSessionRepository.deleteAllByUserId(userId);
        userSessionRepository.flush();
        eventPublisher.publishEvent(new UserSessionEvictedEvent(userId, user.getOrganizationId()));
        UUID sessionId = UUID.randomUUID();
        UserSession row = new UserSession();
        row.setOrganizationId(user.getOrganizationId());
        row.setUserId(user.getId());
        row.setSessionId(sessionId);
        row.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        userSessionRepository.save(row);
        return sessionId;
    }

    /**
     * Inserts an additional active session row without rotating (deleting) existing sessions.
     * Updates {@code userSessionsCache} so JWT validation matches the new session id.
     */
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
        userSessionsCache.put(userId, sessionId);
        return sessionId;
    }

    @Transactional(readOnly = true)
    public long countActiveSessionsForUser(UUID userId) {
        return userSessionRepository.countByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Optional<UserSession> findActiveSessionBySessionId(UUID sessionId) {
        return userSessionRepository.findBySessionId(sessionId);
    }
}

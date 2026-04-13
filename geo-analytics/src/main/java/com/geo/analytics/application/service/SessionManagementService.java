package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.domain.entity.UserSession;
import com.geo.analytics.infrastructure.repository.OrganizationUserRepository;
import com.geo.analytics.infrastructure.repository.UserSessionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionManagementService {

    private final OrganizationUserRepository organizationUserRepository;
    private final UserSessionRepository userSessionRepository;

    public SessionManagementService(
            OrganizationUserRepository organizationUserRepository,
            UserSessionRepository userSessionRepository) {
        this.organizationUserRepository = organizationUserRepository;
        this.userSessionRepository = userSessionRepository;
    }

    @Transactional
    public UUID createNewSession(UUID userId) {
        OrganizationUser user = organizationUserRepository
                .findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("OrganizationUser not found"));
        userSessionRepository.deleteAllByUserId(userId);
        userSessionRepository.flush();
        UUID sessionId = UUID.randomUUID();
        UserSession row = new UserSession();
        row.setOrganizationId(user.getOrganizationId());
        row.setUserId(user.getId());
        row.setSessionId(sessionId);
        row.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        userSessionRepository.save(row);
        return sessionId;
    }
}

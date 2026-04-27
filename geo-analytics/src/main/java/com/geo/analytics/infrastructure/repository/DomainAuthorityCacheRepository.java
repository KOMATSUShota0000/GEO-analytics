package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.DomainAuthorityCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DomainAuthorityCacheRepository extends JpaRepository<DomainAuthorityCacheEntity, UUID> {
    Optional<DomainAuthorityCacheEntity> findByDomain(String domain);
}

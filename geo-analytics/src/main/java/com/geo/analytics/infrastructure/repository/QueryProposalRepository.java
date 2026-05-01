package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.QueryProposalEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryProposalRepository extends JpaRepository<QueryProposalEntity, UUID> {

    Optional<QueryProposalEntity> findByTenantIdAndId(String tenantId, UUID id);

    List<QueryProposalEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}

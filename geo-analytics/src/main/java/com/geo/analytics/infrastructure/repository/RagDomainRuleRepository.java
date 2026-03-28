package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.RagDomainRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RagDomainRuleRepository extends JpaRepository<RagDomainRuleEntity, UUID> {
    long count();

    List<RagDomainRuleEntity> findAllByActiveTrue();
}

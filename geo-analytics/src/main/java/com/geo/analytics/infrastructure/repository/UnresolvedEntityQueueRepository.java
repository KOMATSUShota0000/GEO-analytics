package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.UnresolvedEntityQueueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UnresolvedEntityQueueRepository extends JpaRepository<UnresolvedEntityQueueEntity, UUID> {}

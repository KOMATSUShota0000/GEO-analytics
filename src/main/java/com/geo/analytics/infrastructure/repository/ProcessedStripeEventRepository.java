package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.ProcessedStripeEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEventEntity, UUID> {
    boolean existsByEventId(String eventId);
}

package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.OrganizationEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrganizationEntity o WHERE o.id = :id AND o.deletedAt IS NULL")
    Optional<OrganizationEntity> findByIdForUpdate(@Param("id") UUID id);
}

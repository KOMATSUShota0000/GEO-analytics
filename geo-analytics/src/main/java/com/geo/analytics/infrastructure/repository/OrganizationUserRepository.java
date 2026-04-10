package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.OrganizationUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationUserRepository extends JpaRepository<OrganizationUser, UUID> {

    Optional<OrganizationUser> findByEmailAndDeletedAtIsNull(String email);
}

package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.OrganizationUser;
import com.geo.analytics.domain.enums.OrganizationUserRole;
import com.geo.analytics.infrastructure.persistence.GlobalAccess;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationUserRepository extends JpaRepository<OrganizationUser, UUID> {

    @GlobalAccess
    Optional<OrganizationUser> findByEmailAndDeletedAtIsNull(String email);

    // Why: ログインコードは入力の大文字・小文字を区別しない（#146）。一意制約は区別するため、
    //      大文字違いの行が複数あり得る。登録の早い方に決め打ちして結果を一意にする。
    @GlobalAccess
    Optional<OrganizationUser> findFirstByEmailIgnoreCaseAndDeletedAtIsNullOrderByCreatedAtAsc(String email);

    Optional<OrganizationUser> findFirstByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID organizationId);

    Optional<OrganizationUser> findFirstByOrganizationIdAndRoleAndDeletedAtIsNullOrderByCreatedAtAsc(
            UUID organizationId, OrganizationUserRole role);
}

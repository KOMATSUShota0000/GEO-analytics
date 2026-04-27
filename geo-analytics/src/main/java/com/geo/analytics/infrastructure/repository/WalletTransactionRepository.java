package com.geo.analytics.infrastructure.repository;

import com.geo.analytics.domain.entity.WalletTransactionEntity;
import com.geo.analytics.domain.enums.TransactionType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransactionRepository extends JpaRepository<WalletTransactionEntity, UUID> {
    boolean existsByParentReservationId(UUID parentReservationId);
    Optional<WalletTransactionEntity> findByIdAndOrganizationIdAndTransactionType(
            UUID id, UUID organizationId, TransactionType transactionType);

    @Query(
            "SELECT w FROM WalletTransactionEntity w WHERE w.transactionType = :reserve AND w.createdAt < :cutoff AND"
                + " NOT EXISTS (SELECT c FROM WalletTransactionEntity c WHERE c.parentReservationId = w.id)")
    List<WalletTransactionEntity> findReserveRowsWithoutChildOlderThan(
            @Param("reserve") TransactionType reserve, @Param("cutoff") LocalDateTime cutoff);
}

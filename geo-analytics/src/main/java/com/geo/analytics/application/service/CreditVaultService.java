package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.OrganizationEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.entity.WalletTransactionEntity;
import com.geo.analytics.domain.entity.WorkspaceEntity;
import com.geo.analytics.domain.enums.TransactionType;
import com.geo.analytics.domain.exception.InsufficientCreditException;
import com.geo.analytics.infrastructure.repository.OrganizationRepository;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.repository.WalletTransactionRepository;
import com.geo.analytics.infrastructure.repository.WorkspaceRepository;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class CreditVaultService {
    private final OrganizationRepository organizationRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    public CreditVaultService(
            OrganizationRepository organizationRepository,
            WalletTransactionRepository walletTransactionRepository,
            ProjectRepository projectRepository,
            WorkspaceRepository workspaceRepository) {
        this.organizationRepository = organizationRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
    }
    @Transactional
    public UUID reserve(UUID projectId, long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount");
        }
        UUID orgId = requireOrgId();
        ProjectEntity project = projectRepository.findById(projectId).orElseThrow(() -> new IllegalArgumentException("projectId"));
        WorkspaceEntity workspace = workspaceRepository.findById(project.getWorkspaceId()).orElseThrow(() -> new IllegalArgumentException("workspace"));
        if (!workspace.getOrganizationId().equals(orgId)) {
            throw new IllegalStateException("organization");
        }
        OrganizationEntity org = organizationRepository.findByIdForUpdate(orgId).orElseThrow(() -> new IllegalStateException("organization"));
        if (org.getCreditBalance() < amount) {
            throw new InsufficientCreditException();
        }
        org.setCreditBalance(org.getCreditBalance() - amount);
        organizationRepository.save(org);
        UUID reservationId = UUID.randomUUID();
        walletTransactionRepository.save(
                new WalletTransactionEntity(
                        reservationId, orgId, projectId, TransactionType.RESERVE, amount, null, LocalDateTime.now(), null));
        return reservationId;
    }
    @Transactional
    public void settle(UUID reservationId, long consumedAmount) {
        settle(reservationId, consumedAmount, null);
    }

    @Transactional
    public void settle(UUID reservationId, long consumedAmount, String note) {
        UUID orgId = requireOrgId();
        if (consumedAmount < 0L) {
            throw new IllegalArgumentException("consumedAmount");
        }
        WalletTransactionEntity reserve =
                walletTransactionRepository
                        .findByIdAndOrganizationIdAndTransactionType(reservationId, orgId, TransactionType.RESERVE)
                        .orElseThrow(() -> new IllegalArgumentException("reservationId"));
        if (walletTransactionRepository.existsByParentReservationId(reservationId)) {
            throw new IllegalStateException("reservationId");
        }
        long reserved = reserve.getAmount();
        if (consumedAmount > reserved) {
            throw new IllegalStateException("consumedAmount");
        }
        OrganizationEntity org = organizationRepository.findByIdForUpdate(orgId).orElseThrow(() -> new IllegalStateException("organization"));
        org.setCreditBalance(org.getCreditBalance() + (reserved - consumedAmount));
        organizationRepository.save(org);
        walletTransactionRepository.save(
                new WalletTransactionEntity(
                        UUID.randomUUID(),
                        orgId,
                        reserve.getProjectId(),
                        TransactionType.SETTLE,
                        consumedAmount,
                        reservationId,
                        LocalDateTime.now(),
                        note));
    }
    public List<WalletTransactionEntity> findStaleReservations(OffsetDateTime cutoff) {
        LocalDateTime at =
                LocalDateTime.ofInstant(cutoff.toInstant(), ZoneId.systemDefault());
        return walletTransactionRepository.findReserveRowsWithoutChildOlderThan(
                TransactionType.RESERVE, at);
    }

    @Transactional
    public void refund(UUID reservationId) {
        UUID orgId = requireOrgId();
        WalletTransactionEntity reserve =
                walletTransactionRepository
                        .findByIdAndOrganizationIdAndTransactionType(reservationId, orgId, TransactionType.RESERVE)
                        .orElseThrow(() -> new IllegalArgumentException("reservationId"));
        if (walletTransactionRepository.existsByParentReservationId(reservationId)) {
            throw new IllegalStateException("reservationId");
        }
        long reserved = reserve.getAmount();
        OrganizationEntity org = organizationRepository.findByIdForUpdate(orgId).orElseThrow(() -> new IllegalStateException("organization"));
        org.setCreditBalance(org.getCreditBalance() + reserved);
        organizationRepository.save(org);
        walletTransactionRepository.save(
                new WalletTransactionEntity(
                        UUID.randomUUID(),
                        orgId,
                        reserve.getProjectId(),
                        TransactionType.REFUND,
                        reserved,
                        reservationId,
                        LocalDateTime.now(),
                        null));
    }
    private static UUID requireOrgId() {
        UUID orgId = TenantContextHolder.requireContext().organizationId();
        if (orgId == null) {
            throw new IllegalStateException("organizationId");
        }
        return orgId;
    }
}

package com.geo.analytics.domain.entity;

import com.geo.analytics.domain.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallet_transactions")
public class WalletTransactionEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "project_id", updatable = false)
    private UUID projectId;
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 32, updatable = false)
    private TransactionType transactionType;
    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;
    @Column(name = "parent_reservation_id", updatable = false)
    private UUID parentReservationId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "note", length = 2048)
    private String note;
    protected WalletTransactionEntity() {
    }
    public WalletTransactionEntity(
            UUID id,
            UUID organizationId,
            UUID projectId,
            TransactionType transactionType,
            long amount,
            UUID parentReservationId,
            LocalDateTime createdAt) {
        this(id, organizationId, projectId, transactionType, amount, parentReservationId, createdAt, null);
    }
    public WalletTransactionEntity(
            UUID id,
            UUID organizationId,
            UUID projectId,
            TransactionType transactionType,
            long amount,
            UUID parentReservationId,
            LocalDateTime createdAt,
            String note) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.parentReservationId = parentReservationId;
        this.createdAt = createdAt;
        this.note = note;
    }
    public UUID getId() {
        return id;
    }
    public UUID getOrganizationId() {
        return organizationId;
    }
    public UUID getProjectId() {
        return projectId;
    }
    public TransactionType getTransactionType() {
        return transactionType;
    }
    public long getAmount() {
        return amount;
    }
    public UUID getParentReservationId() {
        return parentReservationId;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public String getNote() {
        return note;
    }
}

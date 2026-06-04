package com.geo.analytics.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stripe Webhook の冪等台帳。1つの Stripe イベントを高々1回だけ適用するために、
 * 処理済みイベントIDを記録する（重複配信での二重プラン変更を防ぐ）。
 */
@Entity
@Table(name = "processed_stripe_events")
public class ProcessedStripeEventEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;
    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;
    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    public ProcessedStripeEventEntity() {
    }

    public ProcessedStripeEventEntity(UUID id, UUID organizationId, String eventId, String eventType) {
        this.id = id;
        this.organizationId = organizationId;
        this.eventId = eventId;
        this.eventType = eventType;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    @PrePersist
    protected void onCreate() {
        if (processedAt == null) {
            processedAt = LocalDateTime.now();
        }
    }
}

package com.geo.analytics.domain.entity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;
import java.util.UUID;
@Audited
@MappedSuperclass
public abstract class BaseTenantEntity {
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
    private String tenantId;
    public String getTenantId() {
        return tenantId;
    }
    public UUID getWorkspaceId() {
        return UUID.fromString(tenantId);
    }
    public void setWorkspaceId(UUID workspaceId) {
        this.tenantId = workspaceId.toString();
    }
}

package com.geo.analytics.domain.entity;
import com.geo.analytics.domain.audit.ImmutableAuditRevisionPayload;
import com.geo.analytics.domain.audit.UserRevisionListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;
@Entity
@Table(name = "revinfo")
@RevisionEntity(UserRevisionListener.class)
public class AuditRevisionEntity {
    @Id
    @RevisionNumber
    private int rev;
    @RevisionTimestamp
    private long revtstmp;
    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId = "";
    @Column(name = "operator_user_id", nullable = false, length = 320)
    private String operatorUserId = "";
    public int getRev() {
        return rev;
    }
    public void setRev(int rev) {
        this.rev = rev;
    }
    public long getRevtstmp() {
        return revtstmp;
    }
    public void setRevtstmp(long revtstmp) {
        this.revtstmp = revtstmp;
    }
    public String getTenantId() {
        return tenantId;
    }
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    public String getOperatorUserId() {
        return operatorUserId;
    }
    public void setOperatorUserId(String operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
    public ImmutableAuditRevisionPayload toImmutablePayload() {
        return ImmutableAuditRevisionPayload.from(rev, revtstmp, tenantId, operatorUserId);
    }
}

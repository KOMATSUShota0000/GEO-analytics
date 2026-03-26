package com.geo.analytics.domain.audit;
import java.io.Serializable;
public record ImmutableAuditRevisionPayload(
        int revisionNumber,
        long revisionEpochMillis,
        String tenantId,
        String operatorUserId) implements Serializable {
    public static ImmutableAuditRevisionPayload from(int rev, long ts, String tenantId, String operatorUserId) {
        return new ImmutableAuditRevisionPayload(rev, ts, tenantId != null ? tenantId : "", operatorUserId != null ? operatorUserId : "");
    }
}

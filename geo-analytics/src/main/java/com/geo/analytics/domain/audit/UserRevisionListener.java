package com.geo.analytics.domain.audit;
import com.geo.analytics.domain.entity.AuditRevisionEntity;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
public class UserRevisionListener implements RevisionListener {
    @Override
    public void newRevision(Object revisionEntity) {
        AuditRevisionEntity rev = (AuditRevisionEntity) revisionEntity;
        String tid = TenantContext.getTenantId();
        rev.setTenantId(tid != null && !tid.isBlank() ? tid : "");
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        String uid = "";
        if (a != null && a.isAuthenticated() && a.getName() != null) {
            uid = a.getName();
        }
        rev.setOperatorUserId(uid);
    }
}

package com.geo.analytics.infrastructure.persistence;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
public class RlsInjectingTransactionManager extends JpaTransactionManager {
    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        injectTenantSessionVariable();
    }
    private void injectTenantSessionVariable() {
        EntityManagerFactory emf = getEntityManagerFactory();
        if (emf == null) {
            return;
        }
        EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(emf);
        if (holder == null || !holder.isOpen()) {
            return;
        }
        String tid = TenantContext.getTenantId();
        if (tid == null || tid.isBlank()) {
            tid = DefaultTenantIds.WORKSPACE_ID.toString();
        }
        Session session = holder.getEntityManager().unwrap(Session.class);
        session.createNativeQuery("SELECT set_config('app.current_tenant', cast(:tid as text), true)", String.class)
                .setParameter("tid", tid)
                .getSingleResult();
    }
}

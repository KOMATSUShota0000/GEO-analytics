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

    private static final String SESSION_PARAM_PATTERN = "^[a-zA-Z_][a-zA-Z0-9_.]*$";

    private final String postgresSessionParameter;
    private final boolean rlsEnabled;

    public RlsInjectingTransactionManager(String postgresSessionParameter, boolean rlsEnabled) {
        this.postgresSessionParameter = validatePostgresSessionParameter(postgresSessionParameter);
        this.rlsEnabled = rlsEnabled;
    }

    private static String validatePostgresSessionParameter(String raw) {
        if (raw == null || raw.isBlank()) {
            return "app.current_tenant";
        }
        if (!raw.matches(SESSION_PARAM_PATTERN)) {
            throw new IllegalArgumentException("Invalid postgres session parameter: " + raw);
        }
        return raw;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        if (rlsEnabled) {
            injectTenantSessionVariable();
        }
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
        session.createNativeQuery("SELECT set_config(:sn, cast(:tid as text), true)", String.class)
                .setParameter("sn", postgresSessionParameter)
                .setParameter("tid", tid)
                .getSingleResult();
    }
}

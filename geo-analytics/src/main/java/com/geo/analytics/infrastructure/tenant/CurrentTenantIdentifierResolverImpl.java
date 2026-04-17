package com.geo.analytics.infrastructure.tenant;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;
import java.util.Map;
@Component
public class CurrentTenantIdentifierResolverImpl implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {
    @Override
    public String resolveCurrentTenantIdentifier() {
        String t = TenantPlanScope.getTenantId();
        if (t == null || t.isBlank()) {
            return DefaultTenantIds.WORKSPACE_ID.toString();
        }
        return t;
    }
    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put("hibernate.multiTenancy", "DISCRIMINATOR");
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}

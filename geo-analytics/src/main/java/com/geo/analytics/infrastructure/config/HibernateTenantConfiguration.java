package com.geo.analytics.infrastructure.config;
import com.geo.analytics.infrastructure.tenant.WorkspaceTenantResolver;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class HibernateTenantConfiguration {
    @Bean
    public HibernatePropertiesCustomizer hibernateTenantPropertiesCustomizer(WorkspaceTenantResolver workspaceTenantResolver) {
        return hibernateProperties -> {
            hibernateProperties.put("hibernate.multiTenancy", "DISCRIMINATOR");
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, workspaceTenantResolver);
        };
    }
}

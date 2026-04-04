package com.geo.analytics.infrastructure.config;

import com.geo.analytics.infrastructure.persistence.RlsInjectingTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@Profile("!test")
public class JpaTransactionConfiguration {

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory,
            AppProperties appProperties) {
        boolean rlsEnabled = true;
        String postgresSessionParameter = "app.current_tenant";
        if (appProperties.getSecurity() != null && appProperties.getSecurity().getRls() != null) {
            AppProperties.Rls rls = appProperties.getSecurity().getRls();
            rlsEnabled = rls.isEnabled();
            if (rls.getPostgresSessionParameter() != null && !rls.getPostgresSessionParameter().isBlank()) {
                postgresSessionParameter = rls.getPostgresSessionParameter();
            }
        }
        RlsInjectingTransactionManager tm = new RlsInjectingTransactionManager(postgresSessionParameter, rlsEnabled);
        tm.setEntityManagerFactory(entityManagerFactory);
        return tm;
    }
}

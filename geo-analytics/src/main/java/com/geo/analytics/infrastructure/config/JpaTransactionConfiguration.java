package com.geo.analytics.infrastructure.config;
import com.geo.analytics.infrastructure.persistence.RlsInjectingTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
@Configuration
public class JpaTransactionConfiguration {
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        RlsInjectingTransactionManager tm = new RlsInjectingTransactionManager();
        tm.setEntityManagerFactory(entityManagerFactory);
        return tm;
    }
}

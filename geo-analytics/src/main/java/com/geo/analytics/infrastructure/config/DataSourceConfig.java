package com.geo.analytics.infrastructure.config;

import com.geo.analytics.infrastructure.datasource.TenantAwareDataSourceProxy;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class DataSourceConfig {

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    MeterRegistry geoAnalyticsMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean("apiHikariDataSource")
    @ConfigurationProperties("spring.datasource.api.hikari")
    public HikariDataSource apiHikariDataSource(
            @Value("${spring.datasource.api.jdbc-url}") String jdbcUrl,
            @Value("${spring.datasource.api.username}") String username,
            @Value("${spring.datasource.api.password:}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }

    @Bean
    @Primary
    public DataSource apiDataSource(
            @Qualifier("apiHikariDataSource") HikariDataSource delegate, MeterRegistry meterRegistry) {
        return new TenantAwareDataSourceProxy(delegate, meterRegistry);
    }

    @Bean("batchHikariDataSource")
    @ConfigurationProperties("spring.datasource.batch.hikari")
    public HikariDataSource batchHikariDataSource(
            @Value("${spring.datasource.batch.jdbc-url}") String jdbcUrl,
            @Value("${spring.datasource.batch.username}") String username,
            @Value("${spring.datasource.batch.password:}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }

    @Bean("batchDataSource")
    public DataSource batchDataSource(
            @Qualifier("batchHikariDataSource") HikariDataSource delegate, MeterRegistry meterRegistry) {
        return new TenantAwareDataSourceProxy(delegate, meterRegistry);
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("apiDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean("batchJdbcTemplate")
    public JdbcTemplate batchJdbcTemplate(@Qualifier("batchDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean("batchTransactionManager")
    public PlatformTransactionManager batchTransactionManager(
            @Qualifier("batchDataSource") DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }
}

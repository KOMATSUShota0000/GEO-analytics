package com.geo.analytics.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.api.hikari")
    public HikariDataSource apiDataSource(
            @Value("${spring.datasource.api.jdbc-url}") String jdbcUrl,
            @Value("${spring.datasource.api.username}") String username,
            @Value("${spring.datasource.api.password:}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }

    @Bean("batchDataSource")
    @ConfigurationProperties("spring.datasource.batch.hikari")
    public HikariDataSource batchDataSource(
            @Value("${spring.datasource.batch.jdbc-url}") String jdbcUrl,
            @Value("${spring.datasource.batch.username}") String username,
            @Value("${spring.datasource.batch.password:}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
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

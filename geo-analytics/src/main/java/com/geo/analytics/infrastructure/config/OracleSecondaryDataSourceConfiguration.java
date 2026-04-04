package com.geo.analytics.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Configuration
@Profile("prod")
public class OracleSecondaryDataSourceConfiguration {

    @Bean(name = "oracleDataSource")
    @ConditionalOnProperty(prefix = "app.oracle.datasource", name = "url")
    public DataSource oracleDataSource(AppProperties appProperties) {
        AppProperties.Oracle oracle = appProperties.getOracle();
        if (oracle == null) {
            throw new IllegalStateException("app.oracle is required for Oracle secondary DataSource");
        }
        AppProperties.Datasource ds = oracle.getDatasource();
        if (ds.getUrl() == null || ds.getUrl().isBlank()) {
            throw new IllegalStateException("app.oracle.datasource.url must not be blank");
        }
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName("oracle.jdbc.OracleDriver")
                .url(ds.getUrl())
                .username(ds.getUsername() != null ? ds.getUsername() : "")
                .password(ds.getPassword() != null ? ds.getPassword() : "")
                .build();
    }
}

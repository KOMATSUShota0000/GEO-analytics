package com.geo.analytics;

import com.geo.analytics.infrastructure.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(exclude = {TransactionAutoConfiguration.class})
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE, proxyTargetClass = true)
@EnableConfigurationProperties(AppProperties.class)
@EntityScan(basePackages = "com.geo.analytics.domain.entity")
@EnableJpaRepositories(basePackages = "com.geo.analytics.infrastructure.repository")
@EnableScheduling
public class GeoAnalyticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(GeoAnalyticsApplication.class, args);
    }
}
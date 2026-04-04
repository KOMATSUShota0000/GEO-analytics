package com.geo.analytics;

import com.geo.analytics.infrastructure.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EntityScan(basePackages = "com.geo.analytics.domain.entity")
@EnableJpaRepositories(basePackages = "com.geo.analytics.infrastructure.repository")
@EnableScheduling
public class GeoAnalyticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(GeoAnalyticsApplication.class, args);
    }
}
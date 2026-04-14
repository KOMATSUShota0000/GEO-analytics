package com.geo.analytics.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class PostgresSuperuserTestBase {

    private static final String PG_USER = "postgres";
    private static final String PG_PASSWORD = "postgres";

    private static final PostgreSQLContainer<?> POSTGRES = createContainer();

    @SuppressWarnings("resource")
    private static PostgreSQLContainer<?> createContainer() {
        var c = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("geo_subscription_it")
                .withUsername(PG_USER)
                .withPassword(PG_PASSWORD);
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                c.start();
            }
        } catch (RuntimeException ex) {
            c.close();
            throw ex;
        }
        return c;
    }

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            throw new IllegalStateException(
                    "Docker is required for PostgreSQL-backed tests (PostgresSuperuserTestBase subclasses).");
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> PG_USER);
        registry.add("spring.datasource.password", () -> PG_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> PG_USER);
        registry.add("spring.flyway.password", () -> PG_PASSWORD);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}

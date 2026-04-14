package com.geo.analytics.integration;

import com.geo.analytics.integration.flyway.ApiWorkerLoginFlywayCallback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class PostgresTestBase {

    private static final String PG_USER = "postgres";
    private static final String PG_PASSWORD = "postgres";

    private static final PostgreSQLContainer<?> POSTGRES = createContainer();

    @SuppressWarnings("resource")
    private static PostgreSQLContainer<?> createContainer() {
        var c = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("geo_rls_it")
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
                    "Docker is required for PostgreSQL-backed tests (PostgresTestBase subclasses).");
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "api_worker");
        registry.add("spring.datasource.password", () -> ApiWorkerLoginFlywayCallback.API_WORKER_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> PG_USER);
        registry.add("spring.flyway.password", () -> PG_PASSWORD);
    }
}

package com.geo.analytics.integration;

import com.geo.analytics.integration.flyway.ApiWorkerLoginFlywayCallback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresTestBase {

    private static final String PG_USER = "postgres";
    private static final String PG_PASSWORD = "postgres";

    @Container
    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("geo_rls_it").withUsername(PG_USER).withPassword(PG_PASSWORD);

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "api_worker");
        registry.add("spring.datasource.password", () -> ApiWorkerLoginFlywayCallback.API_WORKER_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> PG_USER);
        registry.add("spring.flyway.password", () -> PG_PASSWORD);
    }
}

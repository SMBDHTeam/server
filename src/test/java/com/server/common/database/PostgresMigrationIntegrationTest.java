package com.server.common.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///tour_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
@DisplayName("PostgreSQL migration 통합")
class PostgresMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway 전체 migration과 JPA 스키마 검증을 통과한다")
    void migrationsMatchJpaSchema() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Integer.class
        );
        Integer retryColumnCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'places' "
                        + "and column_name = 'ingestion_next_retry_at'",
                Integer.class
        );
        Integer quotaTableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = 'public' and table_name = 'tour_api_request_usage'",
                Integer.class
        );

        assertThat(migrationCount).isEqualTo(3);
        assertThat(retryColumnCount).isEqualTo(1);
        assertThat(quotaTableCount).isEqualTo(1);
    }
}

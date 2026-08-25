package com.server.common.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
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
    void migrationsMatchJpaSchema() throws IOException {
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
        Integer previewTableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = 'public' and table_name = 'schedule_previews'",
                Integer.class
        );
        Integer creationRequestTableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = 'public' and table_name = 'schedule_creation_requests'",
                Integer.class
        );
        Integer stopTimeColumnCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'schedule_stops' "
                        + "and column_name in ('arrive_at', 'depart_at')",
                Integer.class
        );
        Integer userAuthColumnCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'users' "
                        + "and column_name in ('email', 'provider', 'provider_id', 'role', "
                        + "'status', 'suspended_until', 'suspended_reason')",
                Integer.class
        );
        Integer providerIndexCount = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes "
                        + "where schemaname = 'public' and tablename = 'users' "
                        + "and indexname = 'uk_users_provider_active'",
                Integer.class
        );
        Integer reportHandlingColumnCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'reports' "
                        + "and column_name in ('handled_by', 'handled_at')",
                Integer.class
        );
        Integer scheduleOwnerColumnCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'schedules' "
                        + "and column_name = 'user_id'",
                Integer.class
        );
        Integer questionUiStepColumnCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'questions' "
                        + "and column_name = 'ui_step'",
                Integer.class
        );

        // migration을 추가할 때마다 기대값을 고치지 않도록 실제 파일 수와 대조한다.
        // 예전에는 5로 고정돼 있어 V6가 들어온 뒤 이 테스트가 계속 실패했다.
        assertThat(migrationCount).isEqualTo(migrationScriptCount());
        assertThat(retryColumnCount).isEqualTo(1);
        assertThat(quotaTableCount).isEqualTo(1);
        assertThat(previewTableCount).isEqualTo(1);
        assertThat(creationRequestTableCount).isEqualTo(1);
        assertThat(questionUiStepColumnCount).isEqualTo(1);
        assertThat(stopTimeColumnCount).isEqualTo(2);
        assertThat(userAuthColumnCount).isEqualTo(7);
        // 같은 구글 계정으로 두 번 가입되지 않게 막는 부분 고유 인덱스.
        assertThat(providerIndexCount).isEqualTo(1);
        assertThat(scheduleOwnerColumnCount).isEqualTo(1);
        assertThat(reportHandlingColumnCount).isEqualTo(2);
    }

    /** classpath의 db/migration 아래 있는 실제 스크립트 수. */
    private int migrationScriptCount() throws IOException {
        return new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*__*.sql").length;
    }
}

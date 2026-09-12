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
        Integer spontaneousScheduleColumnCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'schedules' "
                        + "and column_name in ('schedule_type', 'transport_mode', 'start_at', "
                        + "'return_by', 'estimated_return_at', 'spontaneous_metadata_json')",
                Integer.class
        );
        Integer spontaneousStopColumnCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'schedule_stops' "
                        + "and column_name in ('arrive_at_datetime', 'depart_at_datetime', "
                        + "'role', 'themes_json')",
                Integer.class
        );
        Integer spontaneousTransitColumnCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'transit_routes' "
                        + "and column_name in ('depart_at_datetime', 'arrive_at_datetime')",
                Integer.class
        );
        Integer spontaneousRequestColumnCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'schedule_creation_requests' "
                        + "and column_name in ('user_id', 'request_type', 'spontaneous_preview_id')",
                Integer.class
        );
        Integer spontaneousRequestIndexCount = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes "
                        + "where schemaname = 'public' and tablename = 'schedule_creation_requests' "
                        + "and indexname in ('uk_schedule_creation_requests_legacy_key', "
                        + "'uk_schedule_creation_requests_user_key', "
                        + "'uk_schedule_creation_requests_spontaneous_preview')",
                Integer.class
        );
        Integer v18MigrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '18' and success = true",
                Integer.class
        );
        Integer spontaneousOffsetColumnCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = 'public' "
                        + "and data_type = 'timestamp with time zone' and ("
                        + "(table_name = 'schedules' and column_name in "
                        + "('start_at', 'return_by', 'estimated_return_at')) or "
                        + "(table_name = 'schedule_stops' and column_name in "
                        + "('arrive_at_datetime', 'depart_at_datetime')) or "
                        + "(table_name = 'transit_routes' and column_name in "
                        + "('depart_at_datetime', 'arrive_at_datetime')))",
                Integer.class
        );
        Integer spontaneousDefaultAndNullableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = 'public' and ("
                        + "(table_name = 'schedules' and column_name = 'schedule_type' "
                        + "and is_nullable = 'NO' and column_default like '%PLANNED%') or "
                        + "(table_name = 'schedule_creation_requests' and column_name = 'preview_id' "
                        + "and is_nullable = 'YES'))",
                Integer.class
        );
        Integer spontaneousCheckConstraintCount = jdbcTemplate.queryForObject(
                "select count(*) from pg_constraint where conname in "
                        + "('ck_schedules_schedule_type', 'ck_schedule_creation_request_type')",
                Integer.class
        );
        Integer spontaneousRequestIndexDefinitionCount = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' "
                        + "and tablename = 'schedule_creation_requests' and ("
                        + "(indexname = 'uk_schedule_creation_requests_legacy_key' "
                        + "and indexdef like '%(idempotency_key)%' "
                        + "and indexdef like '%user_id IS NULL%') or "
                        + "(indexname = 'uk_schedule_creation_requests_user_key' "
                        + "and indexdef like '%(user_id, idempotency_key)%' "
                        + "and indexdef like '%user_id IS NOT NULL%') or "
                        + "(indexname = 'uk_schedule_creation_requests_spontaneous_preview' "
                        + "and indexdef like '%(user_id, spontaneous_preview_id)%' "
                        + "and indexdef like '%spontaneous_preview_id IS NOT NULL%'))",
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
        assertThat(spontaneousScheduleColumnCount).isEqualTo(6);
        assertThat(spontaneousStopColumnCount).isEqualTo(4);
        assertThat(spontaneousTransitColumnCount).isEqualTo(2);
        assertThat(spontaneousRequestColumnCount).isEqualTo(3);
        assertThat(spontaneousRequestIndexCount).isEqualTo(3);
        assertThat(v18MigrationCount).isEqualTo(1);
        assertThat(spontaneousOffsetColumnCount).isEqualTo(7);
        assertThat(spontaneousDefaultAndNullableCount).isEqualTo(2);
        assertThat(spontaneousCheckConstraintCount).isEqualTo(2);
        assertThat(spontaneousRequestIndexDefinitionCount).isEqualTo(3);
    }

    /** classpath의 db/migration 아래 있는 실제 스크립트 수. */
    private int migrationScriptCount() throws IOException {
        return new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*__*.sql").length;
    }
}

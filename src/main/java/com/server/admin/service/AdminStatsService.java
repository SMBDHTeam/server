package com.server.admin.service;

import com.server.admin.dto.AdminStatsPopularResponse;
import com.server.admin.dto.AdminStatsSummaryResponse;
import com.server.admin.dto.AdminStatsTrendResponse;
import com.server.admin.dto.StatsMetric;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 통계.
 *
 * <p>실시간 집계다. 지금 규모에서는 매번 세도 충분하고, 집계 테이블을 두면 그것을 채우는
 * 배치와 어긋남을 관리해야 한다. 데이터가 커져 느려지면 그때 일 단위 집계로 옮긴다.
 *
 * <p>지표 이름을 SQL 에 그대로 넣지 않는다. {@link StatsMetric} 이 허용된 테이블만
 * 담고 있어, 문자열이 질의로 흘러들 여지를 두지 않는다.
 */
@Service
public class AdminStatsService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 365;
    private static final int DEFAULT_POPULAR_SIZE = 10;
    private static final int MAX_POPULAR_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;

    public AdminStatsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public AdminStatsSummaryResponse getSummary(Integer days) {
        int window = resolveDays(days);
        LocalDateTime since = LocalDate.now(KOREA_ZONE).minusDays(window - 1L).atStartOfDay();

        return new AdminStatsSummaryResponse(
                window,
                metric("users", "deleted_at is null", since),
                metric("posts", "deleted_at is null", since),
                metric("schedules", null, since),
                count("select count(*) from reports where status = 'PENDING'"),
                count("select count(*) from users where status = 'SUSPENDED' and deleted_at is null"),
                count("select count(*) from places where hidden_at is null"));
    }

    @Transactional(readOnly = true)
    public AdminStatsTrendResponse getTrend(StatsMetric metric, Integer days) {
        int window = resolveDays(days);
        LocalDate from = LocalDate.now(KOREA_ZONE).minusDays(window - 1L);

        // 지표는 enum 이 담은 테이블 이름만 쓴다. 사용자 입력이 SQL 로 흘러들지 않는다.
        // 별칭에 day 를 쓰지 않는다. H2 에서 예약어라 문법 오류가 난다.
        String sql = "select cast(created_at as date) as stat_date, count(*) as stat_count from "
                + metric.table() + " where created_at >= ? group by cast(created_at as date)";

        Map<LocalDate, Long> byDay = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            byDay.put(rs.getObject("stat_date", LocalDate.class), rs.getLong("stat_count"));
        }, from.atStartOfDay());

        // 값이 없는 날도 0으로 채운다. 빈 날을 건너뛰면 그래프가 실제보다 완만해 보인다.
        List<AdminStatsTrendResponse.Point> points = new ArrayList<>(window);
        for (int offset = 0; offset < window; offset++) {
            LocalDate date = from.plusDays(offset);
            points.add(new AdminStatsTrendResponse.Point(date, byDay.getOrDefault(date, 0L)));
        }
        return new AdminStatsTrendResponse(metric.name(), points);
    }

    /**
     * 인기 장소와 해시태그.
     *
     * <p>장소는 게시물에 태그된 횟수로 센다. 일정에 담긴 횟수를 쓰면 Planner 가 고른 것이
     * 섞여 사용자가 고른 것과 구분되지 않는다.
     */
    @Transactional(readOnly = true)
    public AdminStatsPopularResponse getPopularPlaces(Integer size) {
        int limit = resolvePopularSize(size);
        List<AdminStatsPopularResponse.Item> items = jdbcTemplate.query("""
                select place.id as id, place.name as name, count(*) as count
                from post_place_tags tag
                join places place on place.id = tag.place_id
                join posts post on post.id = tag.post_id
                where post.deleted_at is null and place.hidden_at is null
                group by place.id, place.name
                order by count desc, place.name asc
                limit ?
                """,
                (rs, rowNum) -> new AdminStatsPopularResponse.Item(
                        rs.getLong("id"), rs.getString("name"), rs.getLong("count")),
                limit);
        return new AdminStatsPopularResponse("PLACE", items);
    }

    @Transactional(readOnly = true)
    public AdminStatsPopularResponse getPopularHashtags(Integer size) {
        int limit = resolvePopularSize(size);
        // hashtags.post_count 대신 실제 연결을 센다. 집계 컬럼은 어긋날 수 있고,
        // 삭제된 게시물의 몫이 남아 있을 수도 있다.
        List<AdminStatsPopularResponse.Item> items = jdbcTemplate.query("""
                select hashtag.name as name, count(*) as count
                from post_hashtags link
                join hashtags hashtag on hashtag.id = link.hashtag_id
                join posts post on post.id = link.post_id
                where post.deleted_at is null
                group by hashtag.name
                order by count desc, hashtag.name asc
                limit ?
                """,
                (rs, rowNum) -> new AdminStatsPopularResponse.Item(
                        null, rs.getString("name"), rs.getLong("count")),
                limit);
        return new AdminStatsPopularResponse("HASHTAG", items);
    }

    private AdminStatsSummaryResponse.Metric metric(
            String table, String aliveCondition, LocalDateTime since) {
        String where = aliveCondition == null ? "" : " where " + aliveCondition;
        long total = count("select count(*) from " + table + where);

        String recentWhere = aliveCondition == null
                ? " where created_at >= ?"
                : " where " + aliveCondition + " and created_at >= ?";
        Long recent = jdbcTemplate.queryForObject(
                "select count(*) from " + table + recentWhere, Long.class, since);

        return new AdminStatsSummaryResponse.Metric(total, recent == null ? 0 : recent);
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private int resolveDays(Integer days) {
        if (days == null || days <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }

    private int resolvePopularSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_POPULAR_SIZE;
        }
        return Math.min(size, MAX_POPULAR_SIZE);
    }
}

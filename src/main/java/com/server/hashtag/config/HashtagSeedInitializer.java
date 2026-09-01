package com.server.hashtag.config;

import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 자주 쓰일 해시태그를 미리 만들어 둔다.
 *
 * <p>같은 태그를 처음 쓰는 요청이 동시에 들어오면 이름 고유 제약에 걸려 하나가 실패할 수 있다.
 * 미리 만들어 두면 그 순간에는 이미 존재하므로 충돌하지 않는다. 서비스 초기에 자동완성이
 * 비어 보이는 것도 함께 막는다.
 *
 * <p>{@code post_count}는 0에서 시작한다. 실제로 쓰인 횟수만 세야 자동완성 순서가 뒤틀리지 않는다.
 */
@Configuration
public class HashtagSeedInitializer {

    /**
     * 쓸 수 있는 태그 전체. 사용자가 새로 만들 수 없으므로 이 목록이 곧 선택지다.
     * 화면의 카테고리 탭과 같은 이름을 쓴다. 탭을 누르면 이 태그로 거른다.
     *
     * <p>화면의 "전체" 탭은 필터 없이 조회하는 것이라 태그가 아니다.
     */
    private static final List<String> POPULAR_HASHTAGS = List.of(
            "맛집", "카페", "힐링", "액티비티", "쇼핑", "야경", "역사", "자연"
    );

    /**
     * 확인 후 넣으면 인스턴스가 둘 이상 동시에 뜰 때 이름 고유 제약에 걸린다.
     * ApplicationRunner 에서 예외가 나면 기동 자체가 실패하므로 충돌을 무시하고 넣는다.
     */
    @Bean
    ApplicationRunner seedPopularHashtags(JdbcTemplate jdbcTemplate) {
        return args -> jdbcTemplate.batchUpdate(
                "insert into hashtags(name, post_count) values (?, 0) on conflict do nothing",
                POPULAR_HASHTAGS.stream().map(name -> new Object[] {name}).toList());
    }
}

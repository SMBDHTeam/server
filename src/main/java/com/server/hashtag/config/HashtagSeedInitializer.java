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

    private static final List<String> POPULAR_HASHTAGS = List.of(
            // 지역·장소
            "부산", "해운대", "광안리", "감천문화마을", "자갈치시장", "태종대",
            "송도", "서면", "남포동", "영도", "다대포", "기장",
            // 여행 성격
            "부산여행", "뚜벅이여행", "당일치기", "데이트", "가족여행", "혼자여행",
            // 주제
            "맛집", "카페", "야경", "바다", "사진스팟", "축제",
            // 지역과 주제를 붙여 쓰는 형태. 태그에는 띄어쓰기를 넣을 수 없어 자주 쓰인다.
            "부산맛집", "부산카페", "부산야경", "부산바다", "부산데이트", "부산가볼만한곳",
            "해운대맛집", "해운대카페", "광안리맛집", "광안리카페", "서면맛집", "남포동맛집"
    );

    @Bean
    ApplicationRunner seedPopularHashtags(JdbcTemplate jdbcTemplate) {
        return args -> POPULAR_HASHTAGS.forEach(name -> {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from hashtags where name = ?", Integer.class, name);
            if (count == null || count == 0) {
                jdbcTemplate.update(
                        "insert into hashtags(name, post_count) values (?, 0)", name);
            }
        });
    }
}

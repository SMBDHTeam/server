package com.server.common.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.post.domain.Post;
import com.server.post.repository.PostRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한국 시각으로 만든 값이 DB 에 그대로 들어가는지 본다.
 *
 * <p>JVM 기본 시간대가 한국이 아닐 때 JDBC 가 값을 옮기며 시각을 바꿔 버리면, 코드에서
 * 시간대를 고정한 것이 소용없어진다. 컬럼이 {@code timestamp}(시간대 없음)이고 필드가
 * {@code LocalDateTime} 이면 변환이 일어나지 않아야 한다. 그 전제를 고정한다.
 *
 * <p>H2 가 아니라 실제 PostgreSQL 로 확인한다. 시간대 변환은 드라이버가 하는 일이라
 * 운영과 같은 드라이버로 봐야 의미가 있다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///tour_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Transactional
@ActiveProfiles("test")
@DisplayName("서버 시계와 DB 저장")
class ServerClockPersistenceTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("JVM 시간대와 무관하게 한국 시각 그대로 저장된다")
    void storesKoreaTimeAsIs() {
        User author = userRepository.save(new User("시계테스트" + System.nanoTime(), null));
        Post post = postRepository.saveAndFlush(new Post(author, "본문"));

        LocalDateTime stored = jdbcTemplate.queryForObject(
                "select created_at from posts where id = ?", LocalDateTime.class, post.getId());
        LocalDateTime korea = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        assertThat(Duration.between(korea, stored).abs())
                .describedAs("JVM 기본 시간대=%s", TimeZone.getDefault().getID())
                .isLessThan(Duration.ofMinutes(1));
    }
}

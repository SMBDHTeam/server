package com.server.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

/**
 * Redis 접속 설정이 실제로 바인딩되는지 확인한다.
 *
 * <p>리프레시 토큰 저장소로 쓰므로 호스트·포트를 잘못 잡으면 로그인 갱신이 전부 실패한다.
 * 연결은 지연 생성이라 서버가 뜰 때는 드러나지 않고 첫 갱신 요청에서야 터진다.
 *
 * <p>여기서는 살아 있는 Redis 를 요구하지 않는다. 설정값이 커넥션 팩토리까지 전달되는지만
 * 본다. 테스트가 Redis 기동에 의존하면 CI 가 불안정해진다.
 */
@SpringBootTest(properties = {"REDIS_HOST=redis.example", "REDIS_PORT=6380"})
@ActiveProfiles("test")
@DisplayName("Redis 접속 설정")
class RedisConnectionConfigTest {

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Test
    @DisplayName("환경변수의 호스트와 포트를 커넥션 팩토리에 적용한다")
    void bindsHostAndPortFromEnvironment() {
        assertThat(connectionFactory).isInstanceOf(LettuceConnectionFactory.class);

        LettuceConnectionFactory lettuce = (LettuceConnectionFactory) connectionFactory;
        assertThat(lettuce.getHostName()).isEqualTo("redis.example");
        assertThat(lettuce.getPort()).isEqualTo(6380);
    }
}

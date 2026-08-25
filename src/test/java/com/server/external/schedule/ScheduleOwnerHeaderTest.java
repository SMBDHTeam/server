package com.server.external.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.auth.service.AuthenticatedUser;
import com.server.schedule.dto.SchedulePreviewScheduleRequest;
import com.server.user.domain.UserRole;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 일정 소유자가 FastAPI 까지 전달되는지 확인한다.
 *
 * <p>스텁 서버가 받은 헤더를 직접 본다. 여기서 빠지면 일정에 주인이 없는 채로 저장되고,
 * 내 일정 목록·수정 권한·공유가 전부 성립하지 않는다.
 */
@DisplayName("일정 소유자 전달")
class ScheduleOwnerHeaderTest {

    private static final String OWNER_HEADER = "X-Auth-User-Id";

    private HttpServer server;
    private final AtomicReference<String> receivedOwner = new AtomicReference<>();

    @BeforeEach
    void startStub() throws IOException {
        receivedOwner.set(null);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedOwner.set(exchange.getRequestHeaders().getFirst(OWNER_HEADER));
            exchange.getRequestBody().readAllBytes();
            byte[] body = scheduleJson();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        SecurityContextHolder.clearContext();
    }

    private static byte[] scheduleJson() {
        return ("{\"id\":\"" + UUID.randomUUID() + "\",\"status\":\"READY\",\"days\":[]}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private FastApiScheduleClient client() {
        FastApiScheduleProperties properties = new FastApiScheduleProperties(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(3),
                Duration.ofSeconds(15));
        return new FastApiScheduleClient(
                new FastApiScheduleConfig().fastApiScheduleRestClient(properties),
                properties,
                new ObjectMapper());
    }

    private void loginAs(Long userId) {
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    @DisplayName("로그인한 사용자의 ID를 헤더로 보낸다")
    void sendsOwnerHeaderWhenLoggedIn() {
        client().createScheduleFromPreview(
                new SchedulePreviewScheduleRequest(UUID.randomUUID()), "key-1", 42L);

        assertThat(receivedOwner.get()).isEqualTo("42");
    }

    @Test
    @DisplayName("로그인하지 않았으면 헤더를 붙이지 않는다")
    void omitsOwnerHeaderWhenAnonymous() {
        // 인가를 켜기 전이라 비로그인 생성이 가능하다. 이때는 소유자 없는 일정으로 저장된다.
        client().createScheduleFromPreview(
                new SchedulePreviewScheduleRequest(UUID.randomUUID()), "key-1", null);

        assertThat(receivedOwner.get()).isNull();
    }

    @Test
    @DisplayName("SecurityContext 의 사용자를 그대로 읽는다")
    void readsUserFromSecurityContext() {
        loginAs(7L);

        assertThat(com.server.auth.web.CurrentUser.idOrNull()).isEqualTo(7L);
    }

    @Test
    @DisplayName("인증 정보가 없으면 사용자 ID는 null 이다")
    void returnsNullWithoutAuthentication() {
        assertThat(com.server.auth.web.CurrentUser.idOrNull()).isNull();
    }
}

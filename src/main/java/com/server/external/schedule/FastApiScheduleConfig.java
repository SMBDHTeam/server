package com.server.external.schedule;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class FastApiScheduleConfig {

    /**
     * FastAPI 위임 전용 RestClient.
     *
     * <p>요청 팩토리로 {@link JdkClientHttpRequestFactory}를 쓴다.
     * 기본값인 {@code SimpleClientHttpRequestFactory}는 내부가
     * {@code HttpURLConnection}이라 PATCH를 보내지 못하고
     * {@code Invalid HTTP method: PATCH}로 즉시 실패한다. 이 실패는
     * {@code ResourceAccessException}이라 일정 수정이 항상 503으로 나갔다.
     *
     * <p>프로토콜을 HTTP/1.1로 고정한다. {@link HttpClient}의 기본값은 HTTP/2라
     * 평문 연결에서 h2c 업그레이드를 시도하는데, FastAPI를 띄우는 uvicorn은 이를
     * 지원하지 않는다. 업그레이드가 거부되는 과정에서 요청 본문이 유실되고
     * ({@code Unsupported upgrade request} / {@code Invalid HTTP request received}),
     * FastAPI는 본문 없는 요청으로 보아 {@code loc=["body"] "Field required"}로
     * 422를 낸다. 본문이 있는 POST·PATCH가 전부 여기 걸린다.
     */
    @Bean
    RestClient fastApiScheduleRestClient(FastApiScheduleProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}

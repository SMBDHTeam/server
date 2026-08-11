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
     */
    @Bean
    RestClient fastApiScheduleRestClient(FastApiScheduleProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
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

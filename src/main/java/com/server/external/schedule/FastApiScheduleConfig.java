package com.server.external.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class FastApiScheduleConfig {

    @Bean
    RestClient fastApiScheduleRestClient(
            FastApiScheduleProperties properties,
            ObjectMapper objectMapper
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        MappingJackson2HttpMessageConverter jacksonConverter =
                new MappingJackson2HttpMessageConverter(objectMapper);

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .messageConverters(converters -> prioritizeJacksonConverter(converters, jacksonConverter))
                .build();
    }

    private void prioritizeJacksonConverter(
            List<HttpMessageConverter<?>> converters,
            MappingJackson2HttpMessageConverter jacksonConverter
    ) {
        converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
        converters.add(0, jacksonConverter);
    }
}

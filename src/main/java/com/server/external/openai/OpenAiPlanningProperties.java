package com.server.external.openai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-planner")
public record OpenAiPlanningProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        Duration connectTimeout,
        Duration readTimeout
) {

    public OpenAiPlanningProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com";
        }
        if (apiKey == null) {
            apiKey = "";
        }
        if (model == null || model.isBlank()) {
            model = "gpt-5.6-luna";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(8);
        }
    }
}

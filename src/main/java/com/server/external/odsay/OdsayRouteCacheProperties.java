package com.server.external.odsay;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.odsay.cache")
public record OdsayRouteCacheProperties(
        Duration pathTtl,
        Duration detailTtl,
        int maxEntries
) {

    public OdsayRouteCacheProperties {
        if (pathTtl == null || pathTtl.isNegative()) {
            pathTtl = Duration.ofMinutes(30);
        }
        if (detailTtl == null || detailTtl.isNegative()) {
            detailTtl = Duration.ofMinutes(5);
        }
        maxEntries = Math.max(0, Math.min(maxEntries, 20_000));
    }

    public static OdsayRouteCacheProperties defaults() {
        return new OdsayRouteCacheProperties(
                Duration.ofMinutes(30), Duration.ofMinutes(5), 2_048);
    }
}

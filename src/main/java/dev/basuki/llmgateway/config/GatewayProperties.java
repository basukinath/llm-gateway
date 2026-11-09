package dev.basuki.llmgateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "gateway")
@Validated
public record GatewayProperties(
        Claude claude,
        OpenAi openai,
        RateLimit rateLimit,
        Cache cache,
        Routing routing
) {
    public record Claude(@NotBlank String apiKey) {}

    public record OpenAi(@NotBlank String apiKey) {}

    public record RateLimit(int requestsPerMinute) {
        public RateLimit {
            if (requestsPerMinute <= 0) requestsPerMinute = 60;
        }
    }

    public record Cache(boolean enabled, int maxSize, int ttlMinutes) {
        public Cache {
            if (maxSize <= 0) maxSize = 1000;
            if (ttlMinutes <= 0) ttlMinutes = 30;
        }
    }

    public record Routing(List<String> fallbackOrder) {
        public Routing {
            if (fallbackOrder == null || fallbackOrder.isEmpty()) {
                fallbackOrder = List.of("claude", "openai");
            }
        }
    }
}

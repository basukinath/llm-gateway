package dev.basuki.llmgateway.model;

import java.time.Instant;

public record ChatResponse(
        String id,
        String provider,
        String model,
        String content,
        Usage usage,
        long latencyMs,
        boolean cached,
        Instant timestamp
) {
    public record Usage(
            int promptTokens,
            int completionTokens,
            int totalTokens,
            double estimatedCostUsd
    ) {}

    public static ChatResponse cached(ChatResponse original) {
        return new ChatResponse(
                original.id(),
                original.provider(),
                original.model(),
                original.content(),
                original.usage(),
                0,
                true,
                Instant.now()
        );
    }
}

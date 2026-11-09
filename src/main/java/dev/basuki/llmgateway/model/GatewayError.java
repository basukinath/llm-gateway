package dev.basuki.llmgateway.model;

import java.time.Instant;

public record GatewayError(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp
) {
    public GatewayError(int status, String error, String message, String path) {
        this(status, error, message, path, Instant.now());
    }
}

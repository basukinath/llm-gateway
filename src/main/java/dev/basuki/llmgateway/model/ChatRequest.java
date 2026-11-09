package dev.basuki.llmgateway.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ChatRequest(

        String provider,

        String model,

        @NotEmpty(message = "Messages cannot be empty")
        List<Message> messages,

        @Positive(message = "Max tokens must be positive")
        Integer maxTokens,

        Double temperature
) {
    public record Message(
            @NotBlank String role,
            @NotBlank String content
    ) {}

    public ChatRequest {
        if (maxTokens == null) maxTokens = 1024;
        if (temperature == null) temperature = 0.7;
    }
}

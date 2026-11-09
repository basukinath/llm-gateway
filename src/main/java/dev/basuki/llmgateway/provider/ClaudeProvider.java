package dev.basuki.llmgateway.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.basuki.llmgateway.config.GatewayProperties;
import dev.basuki.llmgateway.model.ChatRequest;
import dev.basuki.llmgateway.model.ChatResponse;
import dev.basuki.llmgateway.model.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.*;

@Component
public class ClaudeProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeProvider.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";
    private static final Set<String> SUPPORTED_MODELS = Set.of(
            "claude-sonnet-4-20250514", "claude-haiku-35-20241022",
            "claude-opus-4-20250514"
    );

    private static final double COST_PER_INPUT_TOKEN = 0.003 / 1000;
    private static final double COST_PER_OUTPUT_TOKEN = 0.015 / 1000;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ClaudeProvider(GatewayProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(API_URL)
                .defaultHeader("x-api-key", properties.claude().apiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Provider provider() {
        return Provider.CLAUDE;
    }

    @Override
    public String defaultModel() {
        return DEFAULT_MODEL;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();

        String model = request.model() != null ? request.model() : DEFAULT_MODEL;

        Map<String, Object> body = buildRequestBody(request, model);

        String responseJson = webClient.post()
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        long latency = System.currentTimeMillis() - start;
        return parseResponse(responseJson, model, latency);
    }

    @Override
    public boolean supports(String model) {
        return model == null || SUPPORTED_MODELS.contains(model);
    }

    private Map<String, Object> buildRequestBody(ChatRequest request, String model) {
        List<Map<String, String>> messages = request.messages().stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", request.maxTokens());
        body.put("messages", messages);
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        }
        return body;
    }

    private ChatResponse parseResponse(String json, String model, long latency) {
        try {
            JsonNode root = objectMapper.readTree(json);

            String id = root.path("id").asText();
            String content = root.path("content").get(0).path("text").asText();

            int inputTokens = root.path("usage").path("input_tokens").asInt();
            int outputTokens = root.path("usage").path("output_tokens").asInt();
            double cost = (inputTokens * COST_PER_INPUT_TOKEN) + (outputTokens * COST_PER_OUTPUT_TOKEN);

            return new ChatResponse(
                    id,
                    Provider.CLAUDE.id(),
                    model,
                    content,
                    new ChatResponse.Usage(inputTokens, outputTokens, inputTokens + outputTokens, cost),
                    latency,
                    false,
                    Instant.now()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Claude response: " + e.getMessage(), e);
        }
    }
}

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
public class OpenAiProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o";
    private static final Set<String> SUPPORTED_MODELS = Set.of(
            "gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo"
    );

    private static final double COST_PER_INPUT_TOKEN = 0.005 / 1000;
    private static final double COST_PER_OUTPUT_TOKEN = 0.015 / 1000;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAiProvider(GatewayProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(API_URL)
                .defaultHeader("Authorization", "Bearer " + properties.openai().apiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Provider provider() {
        return Provider.OPENAI;
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
        body.put("messages", messages);
        body.put("max_tokens", request.maxTokens());
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        }
        return body;
    }

    private ChatResponse parseResponse(String json, String model, long latency) {
        try {
            JsonNode root = objectMapper.readTree(json);

            String id = root.path("id").asText();
            String content = root.path("choices").get(0).path("message").path("content").asText();

            int inputTokens = root.path("usage").path("prompt_tokens").asInt();
            int outputTokens = root.path("usage").path("completion_tokens").asInt();
            double cost = (inputTokens * COST_PER_INPUT_TOKEN) + (outputTokens * COST_PER_OUTPUT_TOKEN);

            return new ChatResponse(
                    id,
                    Provider.OPENAI.id(),
                    model,
                    content,
                    new ChatResponse.Usage(inputTokens, outputTokens, inputTokens + outputTokens, cost),
                    latency,
                    false,
                    Instant.now()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }
}

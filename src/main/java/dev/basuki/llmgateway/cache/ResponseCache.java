package dev.basuki.llmgateway.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.basuki.llmgateway.config.GatewayProperties;
import dev.basuki.llmgateway.model.ChatRequest;
import dev.basuki.llmgateway.model.ChatResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Caches LLM responses keyed by a hash of the request (provider + model + messages + temperature).
 * Identical prompts with the same parameters return the cached response.
 */
@Component
public class ResponseCache {

    private final Cache<String, ChatResponse> cache;
    private final boolean enabled;
    private final Counter hitCounter;
    private final Counter missCounter;

    public ResponseCache(GatewayProperties properties, MeterRegistry registry) {
        this.enabled = properties.cache().enabled();
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.cache().maxSize())
                .expireAfterWrite(Duration.ofMinutes(properties.cache().ttlMinutes()))
                .recordStats()
                .build();

        this.hitCounter = Counter.builder("gateway.cache.hits").register(registry);
        this.missCounter = Counter.builder("gateway.cache.misses").register(registry);
    }

    public Optional<ChatResponse> get(ChatRequest request) {
        if (!enabled) return Optional.empty();

        String key = computeKey(request);
        ChatResponse cached = cache.getIfPresent(key);

        if (cached != null) {
            hitCounter.increment();
            return Optional.of(ChatResponse.cached(cached));
        }

        missCounter.increment();
        return Optional.empty();
    }

    public void put(ChatRequest request, ChatResponse response) {
        if (!enabled) return;
        cache.put(computeKey(request), response);
    }

    public long size() {
        return cache.estimatedSize();
    }

    public void invalidate() {
        cache.invalidateAll();
    }

    private String computeKey(ChatRequest request) {
        String raw = String.valueOf(request.provider())
                + "|" + request.model()
                + "|" + request.temperature()
                + "|" + request.messages().stream()
                    .map(m -> m.role() + ":" + m.content())
                    .reduce("", (a, b) -> a + "||" + b);

        return sha256(raw);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

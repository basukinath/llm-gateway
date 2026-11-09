package dev.basuki.llmgateway;

import dev.basuki.llmgateway.cache.ResponseCache;
import dev.basuki.llmgateway.config.GatewayProperties;
import dev.basuki.llmgateway.model.ChatRequest;
import dev.basuki.llmgateway.model.ChatResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ResponseCacheTest {

    private ResponseCache cache;

    @BeforeEach
    void setUp() {
        var properties = new GatewayProperties(
                new GatewayProperties.Claude("test"),
                new GatewayProperties.OpenAi("test"),
                new GatewayProperties.RateLimit(60),
                new GatewayProperties.Cache(true, 100, 30),
                new GatewayProperties.Routing(List.of("claude", "openai"))
        );
        cache = new ResponseCache(properties, new SimpleMeterRegistry());
    }

    @Test
    void shouldReturnEmptyForCacheMiss() {
        var request = new ChatRequest("claude", null,
                List.of(new ChatRequest.Message("user", "hello")), 100, 0.7);
        assertTrue(cache.get(request).isEmpty());
    }

    @Test
    void shouldReturnCachedResponseForSameRequest() {
        var request = new ChatRequest("claude", null,
                List.of(new ChatRequest.Message("user", "hello")), 100, 0.7);
        var response = new ChatResponse("id-1", "claude", "claude-sonnet-4-20250514",
                "Hi there!", new ChatResponse.Usage(10, 5, 15, 0.001),
                200, false, Instant.now());

        cache.put(request, response);

        Optional<ChatResponse> cached = cache.get(request);
        assertTrue(cached.isPresent());
        assertEquals("Hi there!", cached.get().content());
        assertTrue(cached.get().cached(), "Response should be marked as cached");
        assertEquals(0, cached.get().latencyMs(), "Cached response should have 0 latency");
    }

    @Test
    void shouldNotCacheWithDifferentMessages() {
        var request1 = new ChatRequest("claude", null,
                List.of(new ChatRequest.Message("user", "hello")), 100, 0.7);
        var request2 = new ChatRequest("claude", null,
                List.of(new ChatRequest.Message("user", "goodbye")), 100, 0.7);
        var response = new ChatResponse("id-1", "claude", "claude-sonnet-4-20250514",
                "Hi!", new ChatResponse.Usage(10, 5, 15, 0.001),
                200, false, Instant.now());

        cache.put(request1, response);
        assertTrue(cache.get(request2).isEmpty());
    }

    @Test
    void shouldNotCacheWithDifferentTemperature() {
        var request1 = new ChatRequest("claude", null,
                List.of(new ChatRequest.Message("user", "hello")), 100, 0.7);
        var request2 = new ChatRequest("claude", null,
                List.of(new ChatRequest.Message("user", "hello")), 100, 0.9);
        var response = new ChatResponse("id-1", "claude", "claude-sonnet-4-20250514",
                "Hi!", new ChatResponse.Usage(10, 5, 15, 0.001),
                200, false, Instant.now());

        cache.put(request1, response);
        assertTrue(cache.get(request2).isEmpty());
    }

    @Test
    void shouldClearAllEntries() {
        var request = new ChatRequest("claude", null,
                List.of(new ChatRequest.Message("user", "hello")), 100, 0.7);
        var response = new ChatResponse("id-1", "claude", "claude-sonnet-4-20250514",
                "Hi!", new ChatResponse.Usage(10, 5, 15, 0.001),
                200, false, Instant.now());

        cache.put(request, response);
        cache.invalidate();
        assertTrue(cache.get(request).isEmpty());
    }
}

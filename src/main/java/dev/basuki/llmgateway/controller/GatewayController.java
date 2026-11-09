package dev.basuki.llmgateway.controller;

import dev.basuki.llmgateway.cache.ResponseCache;
import dev.basuki.llmgateway.exception.RateLimitExceededException;
import dev.basuki.llmgateway.metrics.UsageTracker;
import dev.basuki.llmgateway.model.ChatRequest;
import dev.basuki.llmgateway.model.ChatResponse;
import dev.basuki.llmgateway.provider.LlmProvider;
import dev.basuki.llmgateway.provider.ProviderRouter;
import dev.basuki.llmgateway.ratelimit.RateLimiter;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);

    private final ProviderRouter router;
    private final RateLimiter rateLimiter;
    private final ResponseCache cache;
    private final UsageTracker usageTracker;

    public GatewayController(ProviderRouter router, RateLimiter rateLimiter,
                             ResponseCache cache, UsageTracker usageTracker) {
        this.router = router;
        this.rateLimiter = rateLimiter;
        this.cache = cache;
        this.usageTracker = usageTracker;
    }

    @PostMapping("/chat")
    @CircuitBreaker(name = "llmProvider", fallbackMethod = "chatFallback")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Client-Id", defaultValue = "anonymous") String clientId) {

        if (!rateLimiter.tryAcquire(clientId)) {
            RateLimiter.RateLimitInfo info = rateLimiter.getInfo(clientId);
            throw new RateLimitExceededException(info);
        }

        var cached = cache.get(request);
        if (cached.isPresent()) {
            ChatResponse response = cached.get();
            usageTracker.record(clientId, response);
            return buildResponse(response, clientId);
        }

        LlmProvider provider = router.resolve(request.provider(), request.model());
        log.info("Routing request to {} for client {}", provider.provider(), clientId);

        ChatResponse response = provider.chat(request);
        cache.put(request, response);
        usageTracker.record(clientId, response);

        return buildResponse(response, clientId);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "cache_size", cache.size()
        );
    }

    @GetMapping("/usage/{clientId}")
    public Map<String, Object> usage(@PathVariable String clientId) {
        return Map.of(
                "clientId", clientId,
                "totalRequests", usageTracker.getTotalRequests(clientId),
                "totalTokens", usageTracker.getTotalTokens(clientId),
                "rateLimit", rateLimiter.getInfo(clientId)
        );
    }

    @DeleteMapping("/cache")
    public Map<String, String> clearCache() {
        cache.invalidate();
        return Map.of("status", "cache cleared");
    }

    @SuppressWarnings("unused")
    private ResponseEntity<ChatResponse> chatFallback(ChatRequest request, String clientId, Throwable t) {
        log.warn("Primary provider failed for client {}: {}. Attempting fallback.", clientId, t.getMessage());

        LlmProvider primary = router.resolve(request.provider(), request.model());
        LlmProvider fallback = router.fallback(primary.provider());

        log.info("Falling back to {} for client {}", fallback.provider(), clientId);
        ChatResponse response = fallback.chat(request);
        cache.put(request, response);
        usageTracker.record(clientId, response);

        return buildResponse(response, clientId);
    }

    private ResponseEntity<ChatResponse> buildResponse(ChatResponse response, String clientId) {
        RateLimiter.RateLimitInfo info = rateLimiter.getInfo(clientId);
        return ResponseEntity.ok()
                .header("X-RateLimit-Limit", String.valueOf(info.limit()))
                .header("X-RateLimit-Remaining", String.valueOf(info.remaining()))
                .header("X-RateLimit-Reset", String.valueOf(info.resetAtEpochSecond()))
                .header("X-Response-Cached", String.valueOf(response.cached()))
                .body(response);
    }
}

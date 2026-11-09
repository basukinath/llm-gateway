package dev.basuki.llmgateway.metrics;

import dev.basuki.llmgateway.model.ChatResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class UsageTracker {

    private static final Logger log = LoggerFactory.getLogger(UsageTracker.class);

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicLong> tokensByClient = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> requestsByClient = new ConcurrentHashMap<>();

    public UsageTracker(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String clientId, ChatResponse response) {
        Counter.builder("gateway.requests.total")
                .tag("provider", response.provider())
                .tag("model", response.model())
                .tag("cached", String.valueOf(response.cached()))
                .register(registry)
                .increment();

        Counter.builder("gateway.tokens.total")
                .tag("provider", response.provider())
                .tag("type", "input")
                .register(registry)
                .increment(response.usage().promptTokens());

        Counter.builder("gateway.tokens.total")
                .tag("provider", response.provider())
                .tag("type", "output")
                .register(registry)
                .increment(response.usage().completionTokens());

        Counter.builder("gateway.cost.usd")
                .tag("provider", response.provider())
                .register(registry)
                .increment(response.usage().estimatedCostUsd());

        Timer.builder("gateway.latency")
                .tag("provider", response.provider())
                .tag("cached", String.valueOf(response.cached()))
                .register(registry)
                .record(Duration.ofMillis(response.latencyMs()));

        tokensByClient.computeIfAbsent(clientId, k -> new AtomicLong())
                .addAndGet(response.usage().totalTokens());
        requestsByClient.computeIfAbsent(clientId, k -> new AtomicLong())
                .incrementAndGet();

        log.info("Request completed: client={}, provider={}, model={}, tokens={}, cost=${}, latency={}ms, cached={}",
                clientId, response.provider(), response.model(),
                response.usage().totalTokens(),
                String.format("%.6f", response.usage().estimatedCostUsd()),
                response.latencyMs(), response.cached());
    }

    public long getTotalTokens(String clientId) {
        AtomicLong tokens = tokensByClient.get(clientId);
        return tokens != null ? tokens.get() : 0;
    }

    public long getTotalRequests(String clientId) {
        AtomicLong requests = requestsByClient.get(clientId);
        return requests != null ? requests.get() : 0;
    }
}

package dev.basuki.llmgateway.ratelimit;

import dev.basuki.llmgateway.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenBucketRateLimiter implements RateLimiter {

    private final int maxTokens;
    private final long refillIntervalMs;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(GatewayProperties properties) {
        this.maxTokens = properties.rateLimit().requestsPerMinute();
        this.refillIntervalMs = 60_000;
    }

    @Override
    public boolean tryAcquire(String clientId) {
        Bucket bucket = buckets.computeIfAbsent(clientId, k -> new Bucket(maxTokens));
        return bucket.tryConsume();
    }

    @Override
    public RateLimitInfo getInfo(String clientId) {
        Bucket bucket = buckets.get(clientId);
        if (bucket == null) {
            return new RateLimitInfo(maxTokens, maxTokens, System.currentTimeMillis() / 1000 + 60);
        }
        return bucket.getInfo(maxTokens);
    }

    private class Bucket {
        private int tokens;
        private long lastRefillTime;

        Bucket(int tokens) {
            this.tokens = tokens;
            this.lastRefillTime = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        synchronized RateLimitInfo getInfo(int max) {
            refill();
            long resetAt = (lastRefillTime + refillIntervalMs) / 1000;
            return new RateLimitInfo(max, tokens, resetAt);
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            if (elapsed >= refillIntervalMs) {
                tokens = maxTokens;
                lastRefillTime = now;
            }
        }
    }
}

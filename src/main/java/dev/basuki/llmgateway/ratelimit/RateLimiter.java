package dev.basuki.llmgateway.ratelimit;

public interface RateLimiter {

    boolean tryAcquire(String clientId);

    RateLimitInfo getInfo(String clientId);

    record RateLimitInfo(int limit, int remaining, long resetAtEpochSecond) {}
}

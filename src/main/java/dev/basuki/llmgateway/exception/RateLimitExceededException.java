package dev.basuki.llmgateway.exception;

import dev.basuki.llmgateway.ratelimit.RateLimiter.RateLimitInfo;

public class RateLimitExceededException extends RuntimeException {

    private final RateLimitInfo rateLimitInfo;

    public RateLimitExceededException(RateLimitInfo info) {
        super("Rate limit exceeded. Limit: " + info.limit() + ", resets at: " + info.resetAtEpochSecond());
        this.rateLimitInfo = info;
    }

    public RateLimitInfo getRateLimitInfo() {
        return rateLimitInfo;
    }
}

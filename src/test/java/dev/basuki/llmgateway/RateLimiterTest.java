package dev.basuki.llmgateway;

import dev.basuki.llmgateway.config.GatewayProperties;
import dev.basuki.llmgateway.ratelimit.RateLimiter;
import dev.basuki.llmgateway.ratelimit.TokenBucketRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        var properties = new GatewayProperties(
                new GatewayProperties.Claude("test"),
                new GatewayProperties.OpenAi("test"),
                new GatewayProperties.RateLimit(5),
                new GatewayProperties.Cache(false, 100, 10),
                new GatewayProperties.Routing(List.of("claude", "openai"))
        );
        rateLimiter = new TokenBucketRateLimiter(properties);
    }

    @Test
    void shouldAllowRequestsWithinLimit() {
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire("client-1"), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void shouldRejectRequestsBeyondLimit() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire("client-1");
        }
        assertFalse(rateLimiter.tryAcquire("client-1"), "6th request should be rejected");
    }

    @Test
    void shouldTrackClientsIndependently() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire("client-1");
        }
        assertFalse(rateLimiter.tryAcquire("client-1"));
        assertTrue(rateLimiter.tryAcquire("client-2"), "Different client should not be affected");
    }

    @Test
    void shouldReturnCorrectRateLimitInfo() {
        rateLimiter.tryAcquire("client-1");
        rateLimiter.tryAcquire("client-1");

        RateLimiter.RateLimitInfo info = rateLimiter.getInfo("client-1");
        assertEquals(5, info.limit());
        assertEquals(3, info.remaining());
        assertTrue(info.resetAtEpochSecond() > System.currentTimeMillis() / 1000);
    }

    @Test
    void shouldReturnFullInfoForNewClient() {
        RateLimiter.RateLimitInfo info = rateLimiter.getInfo("never-seen");
        assertEquals(5, info.limit());
        assertEquals(5, info.remaining());
    }
}

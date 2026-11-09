package dev.basuki.llmgateway;

import dev.basuki.llmgateway.config.GatewayProperties;
import dev.basuki.llmgateway.model.ChatRequest;
import dev.basuki.llmgateway.model.ChatResponse;
import dev.basuki.llmgateway.model.Provider;
import dev.basuki.llmgateway.provider.LlmProvider;
import dev.basuki.llmgateway.provider.ProviderRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProviderRouterTest {

    private ProviderRouter router;
    private LlmProvider claudeProvider;
    private LlmProvider openAiProvider;

    @BeforeEach
    void setUp() {
        claudeProvider = new StubProvider(Provider.CLAUDE, "claude-sonnet-4-20250514",
                Set.of("claude-sonnet-4-20250514", "claude-haiku-35-20241022"));
        openAiProvider = new StubProvider(Provider.OPENAI, "gpt-4o",
                Set.of("gpt-4o", "gpt-4o-mini"));

        var properties = new GatewayProperties(
                new GatewayProperties.Claude("test"),
                new GatewayProperties.OpenAi("test"),
                new GatewayProperties.RateLimit(60),
                new GatewayProperties.Cache(true, 100, 30),
                new GatewayProperties.Routing(List.of("claude", "openai"))
        );

        router = new ProviderRouter(List.of(claudeProvider, openAiProvider), properties);
    }

    @Test
    void shouldRouteByExplicitProvider() {
        LlmProvider resolved = router.resolve("openai", null);
        assertEquals(Provider.OPENAI, resolved.provider());
    }

    @Test
    void shouldRouteByModel() {
        LlmProvider resolved = router.resolve(null, "gpt-4o");
        assertEquals(Provider.OPENAI, resolved.provider());
    }

    @Test
    void shouldDefaultToFirstInFallbackOrder() {
        LlmProvider resolved = router.resolve(null, null);
        assertEquals(Provider.CLAUDE, resolved.provider());
    }

    @Test
    void shouldReturnFallbackExcludingPrimary() {
        LlmProvider fallback = router.fallback(Provider.CLAUDE);
        assertEquals(Provider.OPENAI, fallback.provider());
    }

    @Test
    void shouldThrowForUnknownProvider() {
        assertThrows(IllegalArgumentException.class, () -> router.resolve("gemini", null));
    }

    @Test
    void shouldThrowForUnsupportedModel() {
        assertThrows(IllegalArgumentException.class, () -> router.resolve(null, "llama-3"));
    }

    record StubProvider(Provider provider, String defaultModel, Set<String> supportedModels) implements LlmProvider {

        @Override
        public ChatResponse chat(ChatRequest request) {
            throw new UnsupportedOperationException("Stub provider");
        }

        @Override
        public boolean supports(String model) {
            return model == null || supportedModels.contains(model);
        }
    }
}

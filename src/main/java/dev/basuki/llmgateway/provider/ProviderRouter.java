package dev.basuki.llmgateway.provider;

import dev.basuki.llmgateway.config.GatewayProperties;
import dev.basuki.llmgateway.model.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes requests to the appropriate LLM provider.
 * Supports explicit provider selection and automatic fallback.
 */
@Component
public class ProviderRouter {

    private static final Logger log = LoggerFactory.getLogger(ProviderRouter.class);

    private final Map<Provider, LlmProvider> providers;
    private final List<Provider> fallbackOrder;

    public ProviderRouter(List<LlmProvider> providerList, GatewayProperties properties) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(LlmProvider::provider, Function.identity()));

        this.fallbackOrder = properties.routing().fallbackOrder().stream()
                .map(Provider::fromId)
                .toList();

        log.info("Initialized provider router with {} providers, fallback order: {}",
                providers.size(), fallbackOrder);
    }

    public LlmProvider resolve(String providerName, String model) {
        if (providerName != null) {
            Provider provider = Provider.fromId(providerName);
            LlmProvider llmProvider = providers.get(provider);
            if (llmProvider == null) {
                throw new IllegalArgumentException("Provider not configured: " + providerName);
            }
            return llmProvider;
        }

        if (model != null) {
            for (LlmProvider p : providers.values()) {
                if (p.supports(model)) return p;
            }
            throw new IllegalArgumentException("No provider supports model: " + model);
        }

        return providers.get(fallbackOrder.getFirst());
    }

    public LlmProvider fallback(Provider excluding) {
        return fallbackOrder.stream()
                .filter(p -> p != excluding)
                .map(providers::get)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No fallback provider available"));
    }
}

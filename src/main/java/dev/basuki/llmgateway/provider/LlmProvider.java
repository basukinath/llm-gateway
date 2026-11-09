package dev.basuki.llmgateway.provider;

import dev.basuki.llmgateway.model.ChatRequest;
import dev.basuki.llmgateway.model.ChatResponse;
import dev.basuki.llmgateway.model.Provider;

public interface LlmProvider {

    Provider provider();

    String defaultModel();

    ChatResponse chat(ChatRequest request);

    boolean supports(String model);
}

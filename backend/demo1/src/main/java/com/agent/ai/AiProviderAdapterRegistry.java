package com.agent.ai;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiProviderAdapterRegistry {
    private final List<AiProviderAdapter> adapters;
    private final OpenAICompatibleProvider fallback;

    public AiProviderAdapterRegistry(List<AiProviderAdapter> adapters, OpenAICompatibleProvider fallback) {
        this.adapters = adapters;
        this.fallback = fallback;
    }

    public AiProviderAdapter forModel(AiModel model) {
        return adapters.stream()
                .filter(adapter -> adapter != fallback)
                .filter(adapter -> adapter.supports(model.getProviderName()))
                .findFirst()
                .orElse(fallback);
    }
}

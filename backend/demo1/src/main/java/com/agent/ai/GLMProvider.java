package com.agent.ai;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class GLMProvider extends OpenAiCompatibleProviderSupport {
    @Override
    public String providerType() { return "GLM"; }

    @Override
    public boolean supports(String providerName) {
        String provider = providerName == null ? "" : providerName.trim().toLowerCase(Locale.ROOT);
        return provider.contains("zhipu") || provider.contains("glm") || provider.contains("bigmodel");
    }

    @Override
    protected AiProviderRequest searchRequest(AiModel model, List<AiGatewayMessage> messages) {
        AiProviderRequest base = chatRequest(model, messages, false);
        Map<String, Object> payload = new LinkedHashMap<>(base.payload());
        payload.put("tools", List.of(Map.of("type", "web_search")));
        return new AiProviderRequest(base.url(), payload, base.accept());
    }
}

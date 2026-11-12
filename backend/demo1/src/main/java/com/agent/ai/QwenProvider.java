package com.agent.ai;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class QwenProvider extends OpenAiCompatibleProviderSupport {
    @Override
    public String providerType() { return "Qwen"; }

    @Override
    public boolean supports(String providerName) {
        String provider = providerName == null ? "" : providerName.trim().toLowerCase(Locale.ROOT);
        return provider.contains("qwen") || provider.contains("alibaba") || provider.contains("aliyun") || provider.contains("dashscope");
    }

    @Override
    protected AiProviderRequest searchRequest(AiModel model, List<AiGatewayMessage> messages) {
        AiProviderRequest base = chatRequest(model, messages, false);
        Map<String, Object> payload = new LinkedHashMap<>(base.payload());
        payload.put("enable_search", true);
        return new AiProviderRequest(base.url(), payload, base.accept());
    }
}

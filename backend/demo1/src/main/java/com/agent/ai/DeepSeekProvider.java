package com.agent.ai;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DeepSeekProvider extends OpenAiCompatibleProviderSupport {
    @Override
    public String providerType() { return "DeepSeek"; }

    @Override
    protected AiProviderRequest chatRequest(AiModel model, List<AiGatewayMessage> messages, boolean stream,
                                            AiRequestOverrides overrides) {
        AiProviderRequest base = super.chatRequest(model, messages, stream, overrides);
        Map<String, Object> payload = new LinkedHashMap<>(base.payload());
        // deepseek-flash now defaults to thinking mode. TopoPlan consumes the
        // final `content` field only; a complex topology plan can otherwise
        // spend the configured token budget entirely on reasoning_content and
        // finish with HTTP 200 plus an empty answer.
        payload.put("thinking", Map.of("type", "disabled"));
        return new AiProviderRequest(base.url(), payload, base.accept());
    }

    @Override
    public boolean supports(String providerName) {
        return providerName != null && providerName.trim().toLowerCase(Locale.ROOT).contains("deepseek");
    }
}

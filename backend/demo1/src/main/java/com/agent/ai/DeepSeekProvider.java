package com.agent.ai;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class DeepSeekProvider extends OpenAiCompatibleProviderSupport {
    @Override
    public String providerType() { return "DeepSeek"; }

    @Override
    public boolean supports(String providerName) {
        return providerName != null && providerName.trim().toLowerCase(Locale.ROOT).contains("deepseek");
    }
}

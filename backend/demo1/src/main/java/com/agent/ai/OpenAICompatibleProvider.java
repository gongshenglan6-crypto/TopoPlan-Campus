package com.agent.ai;

import org.springframework.stereotype.Component;

@Component
public class OpenAICompatibleProvider extends OpenAiCompatibleProviderSupport {
    @Override
    public String providerType() { return "OpenAI Compatible"; }

    @Override
    public boolean supports(String providerName) { return true; }
}

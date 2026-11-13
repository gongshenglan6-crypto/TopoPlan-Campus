package com.agent.ai;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineProviderStreamingRequestTest {

    @Test
    void everyOnlineChatProviderUsesNativeSseWithoutProviderSpecificUsageOptions() {
        List<AiProviderAdapter> providers = List.of(
                new OpenAIProvider(),
                new DeepSeekProvider(),
                new GLMProvider(),
                new QwenProvider(),
                new OpenAICompatibleProvider());

        for (AiProviderAdapter provider : providers) {
            AiProviderRequest request = provider.createRequest(
                    model(),
                    AiRequestType.CHAT,
                    List.of(new AiGatewayMessage("user", "请简要说明核心交换机的作用。")),
                    true,
                    new AiRequestOverrides(new BigDecimal("0.2"), new BigDecimal("0.8"), 256, 30));

            assertEquals(Boolean.TRUE, request.payload().get("stream"), provider.providerType());
            assertEquals("text/event-stream", request.accept(), provider.providerType());
            assertFalse(request.payload().containsKey("stream_options"), provider.providerType());
            assertTrue(request.url().endsWith("/v1/chat/completions"), provider.providerType());
        }
    }

    @Test
    void deepSeekDisablesThinkingSoFinalContentIsAlwaysProduced() {
        AiProviderRequest request = new DeepSeekProvider().createRequest(
                model(),
                AiRequestType.CHAT,
                List.of(new AiGatewayMessage("user", "生成三层办公楼网络拓扑 JSON。")),
                true,
                new AiRequestOverrides(new BigDecimal("0.2"), new BigDecimal("0.8"), 4096, 60));

        assertEquals(Map.of("type", "disabled"), request.payload().get("thinking"));
    }

    private AiModel model() {
        AiModel model = new AiModel("Online Test", "Online Test", "online-test", "CHAT",
                "https://provider.example.com/v1", "encrypted-key");
        model.setTemperature(new BigDecimal("0.7"));
        model.setTopP(new BigDecimal("0.95"));
        model.setMaxOutputTokens(1024);
        return model;
    }
}

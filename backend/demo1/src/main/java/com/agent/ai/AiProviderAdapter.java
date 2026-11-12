package com.agent.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface AiProviderAdapter {
    String providerType();

    boolean supports(String providerName);

    AiProviderRequest createRequest(AiModel model, AiRequestType requestType, List<AiGatewayMessage> messages, boolean stream);

    default AiProviderRequest createRequest(AiModel model, AiRequestType requestType, List<AiGatewayMessage> messages,
                                            boolean stream, AiRequestOverrides overrides) {
        return createRequest(model, requestType, messages, stream);
    }

    AiProviderResponse parseResponse(JsonNode body, AiRequestType requestType, List<AiGatewayMessage> messages);
}

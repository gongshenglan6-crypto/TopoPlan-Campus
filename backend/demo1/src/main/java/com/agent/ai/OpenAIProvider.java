package com.agent.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class OpenAIProvider extends OpenAiCompatibleProviderSupport {
    @Override
    public String providerType() { return "OpenAI"; }

    @Override
    public boolean supports(String providerName) {
        String provider = providerName == null ? "" : providerName.trim().toLowerCase(Locale.ROOT);
        return provider.equals("openai") || provider.equals("open ai");
    }

    @Override
    protected AiProviderRequest searchRequest(AiModel model, List<AiGatewayMessage> messages) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model.getModelId());
        payload.put("input", testInput(messages));
        payload.put("tools", List.of(Map.of("type", "web_search_preview")));
        return new AiProviderRequest(ProviderEndpointResolver.endpoint(model.getBaseUrl(), "responses"), payload, "application/json");
    }

    @Override
    protected AiProviderResponse searchResponse(JsonNode body, List<AiGatewayMessage> messages) {
        String content = body.path("output_text").asText("");
        if (content.isBlank()) content = collectText(body.path("output"));
        if (content.isBlank()) return super.searchResponse(body, messages);
        return response(content, body.path("usage"), messages, 0);
    }

    private String collectText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        if (node.isTextual()) return node.asText();
        StringBuilder result = new StringBuilder();
        if (node.isArray()) {
            for (JsonNode child : node) appendText(result, collectText(child));
        } else if (node.isObject()) {
            appendText(result, node.path("text").asText(""));
            appendText(result, collectText(node.path("content")));
            appendText(result, collectText(node.path("summary")));
        }
        return result.toString().trim();
    }

    private void appendText(StringBuilder target, String value) {
        if (value == null || value.isBlank()) return;
        if (target.length() > 0) target.append('\n');
        target.append(value.trim());
    }
}

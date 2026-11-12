package com.agent.admin.config;

import com.agent.common.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PromptRuntimeServiceTest {
    private final PromptRuntimeService service = new PromptRuntimeService(null, null, new ObjectMapper());

    @Test
    void normalizesStructuredJsonAndAcceptsCodeFences() {
        String content = service.validateStructuredOutput(Map.of(
                "outputFormat", "STRICT_JSON",
                "schemaJson", "{\"nodes\": [], \"edges\": []}"
        ), "```json\n{\"nodes\": [], \"edges\": []}\n```");

        assertEquals("{\"nodes\":[],\"edges\":[]}", content);
    }

    @Test
    void rejectsStructuredJsonWhenTheConfiguredShapeIsMissing() {
        assertThrows(ApiException.class, () -> service.validateStructuredOutput(Map.of(
                "outputFormat", "STRUCTURED_JSON",
                "schemaJson", "{\"nodes\": [], \"edges\": []}"
        ), "{\"nodes\": []}"));
    }

    @Test
    void leavesNonStructuredOutputUntouched() {
        assertEquals("# 网络方案", service.validateStructuredOutput(Map.of(
                "outputFormat", "MARKDOWN"
        ), "  # 网络方案  "));
    }
}

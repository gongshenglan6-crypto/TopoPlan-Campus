package com.agent.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agent.common.audit.AuditLog;
import com.agent.common.audit.AuditLogRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiModelTestIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AuditLogRepository auditLogs;

    @Test
    void modelTestsUseProviderAdaptersAndModelTypeSpecificEndpoints() throws Exception {
        CapturedRequests captured = new CapturedRequests();
        HttpServer provider = startProvider(captured);
        try {
            MockHttpSession session = login("admin", "admin");
            String baseUrl = "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1";

            long qwen = createModel(session, "Alibaba Cloud / Qwen", "qwen-max-real", "Qwen display name", "chat", baseUrl, "ai-chat");
            long glm = createModel(session, "Zhipu AI / GLM", "glm-4-plus-real", "GLM display name", "reasoning", baseUrl, "ai-chat");
            long openAi = createModel(session, "OpenAI", "gpt-4.1", "GPT-4.1", "chat", baseUrl, "ai-chat");
            long embedding = createModel(session, "OpenAI", "text-embedding-3-large", "Text Embedding 3 Large", "chat", baseUrl, "ai-chat");
            long search = createModel(session, "OpenAI Compatible", "topology-search-v1", "Topology Search", "search", baseUrl, "ai-chat");

            for (long id : new long[]{qwen, glm, openAi}) {
                mockMvc.perform(post("/api/admin/model-channels/" + id + "/test").with(csrf()).session(session))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.success").value(true))
                        .andExpect(jsonPath("$.data.requestType").value("CHAT"))
                        .andExpect(jsonPath("$.data.httpStatus").value(200));
            }

            mockMvc.perform(post("/api/admin/model-channels/" + embedding + "/test").with(csrf()).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.requestType").value("EMBEDDING"))
                    .andExpect(jsonPath("$.data.inputTokens").value(12));

            mockMvc.perform(post("/api/admin/model-channels/" + search + "/test").with(csrf()).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.requestType").value("SEARCH"));

            assertEquals("qwen-max-real", captured.body("/v1/chat/completions", "qwen-max-real").path("model").asText());
            assertEquals("glm-4-plus-real", captured.body("/v1/chat/completions", "glm-4-plus-real").path("model").asText());
            assertEquals("gpt-4.1", captured.body("/v1/chat/completions", "gpt-4.1").path("model").asText());
            JsonNode embeddingRequest = captured.body("/v1/embeddings", "text-embedding-3-large");
            assertEquals("text-embedding-3-large", embeddingRequest.path("model").asText());
            assertTrue(embeddingRequest.has("input"));
            assertFalse(embeddingRequest.has("messages"));
            assertEquals("topology-search-v1", captured.body("/v1/search", "topology-search-v1").path("model").asText());

            mockMvc.perform(get("/api/admin/model-channels").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[?(@.id == " + embedding + ")].modelType").value("embedding"))
                    .andExpect(jsonPath("$.data.items[?(@.id == " + embedding + ")].useCases[0]").value("knowledge-embedding"))
                    .andExpect(jsonPath("$.data.items[?(@.id == " + embedding + ")].lastTestStatus").value("PASSED"));

            AuditLog audit = modelTestAudit(openAi, "SUCCESS");
            assertTrue(audit.getDetail().contains("provider=OpenAI"));
            assertTrue(audit.getDetail().contains("model_id=gpt-4.1"));
            assertTrue(audit.getDetail().contains("url=" + baseUrl + "/chat/completions"));
            assertTrue(audit.getDetail().contains("http=200"));
            assertFalse(audit.getDetail().contains("test-key"));
        } finally {
            provider.stop(0);
        }
    }

    @Test
    void modelTestReturnsSpecificAuthenticationFailureAndPersistsIt() throws Exception {
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/v1/chat/completions", exchange -> respond(exchange, 401, "{\"error\":{\"message\":\"invalid api key test-key\"}}"));
        provider.start();
        try {
            MockHttpSession session = login("admin", "admin");
            long modelId = createModel(session, "OpenAI", "auth-check-model", "Auth check", "chat",
                    "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1", "ai-chat");

            mockMvc.perform(post("/api/admin/model-channels/" + modelId + "/test").with(csrf()).session(session))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("API Key 无效或已失效"))
                    .andExpect(jsonPath("$.details.httpStatus").value("401"))
                    .andExpect(jsonPath("$.details.requestType").value("CHAT"))
                    .andExpect(jsonPath("$.details.providerMessage").value("invalid api key [REDACTED]"));

            mockMvc.perform(get("/api/admin/model-channels").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[?(@.id == " + modelId + ")].lastTestStatus").value("FAILED"))
                    .andExpect(jsonPath("$.data.items[?(@.id == " + modelId + ")].lastTestErrorCode").value("AUTHENTICATION_ERROR"))
                    .andExpect(jsonPath("$.data.items[?(@.id == " + modelId + ")].lastTestErrorMessage").value("API Key 无效或已失效"));

            AuditLog audit = modelTestAudit(modelId, "FAILED");
            assertTrue(audit.getDetail().contains("error_type=AUTHENTICATION_ERROR"));
            assertTrue(audit.getDetail().contains("provider_message=invalid api key [REDACTED]"));
            assertFalse(audit.getDetail().contains("test-key"));
        } finally {
            provider.stop(0);
        }
    }

    @Test
    void qwenModelCodeIsNormalizedAndProviderModelErrorsAreMapped() throws Exception {
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/v1/chat/completions", exchange -> respond(exchange, 400,
                "{\"error\":{\"message\":\"Model does not exist. Please check it carefully.\"}}"));
        provider.start();
        try {
            MockHttpSession session = login("admin", "admin");
            long modelId = createModel(session, "Qwen", "Qwen2.5-7B-Instruct", "Qwen2.5-7B-Instruct", "chat",
                    "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1", "ai-chat");

            mockMvc.perform(get("/api/admin/model-channels").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[?(@.id == " + modelId + ")].modelId").value("qwen2.5-7b-instruct"));

            mockMvc.perform(post("/api/admin/model-channels/" + modelId + "/test").with(csrf()).session(session))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MODEL_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("模型 ID 不存在"))
                    .andExpect(jsonPath("$.details.httpStatus").value("400"));
        } finally {
            provider.stop(0);
        }
    }

    @Test
    void glmModelCodeIsNormalizedBeforeProviderRequest() throws Exception {
        CapturedRequests captured = new CapturedRequests();
        HttpServer provider = startProvider(captured);
        try {
            MockHttpSession session = login("admin", "admin");
            long modelId = createModel(session, "Zhipu AI / GLM", "GLM-4.7-Flash", "GLM 4.7 Flash", "chat",
                    "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1", "ai-chat");

            mockMvc.perform(get("/api/admin/model-channels").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[?(@.id == " + modelId + ")].modelId").value("glm-4.7-flash"));

            mockMvc.perform(post("/api/admin/model-channels/" + modelId + "/test").with(csrf()).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.success").value(true));

            assertEquals("glm-4.7-flash", captured.body("/v1/chat/completions", "glm-4.7-flash").path("model").asText());
        } finally {
            provider.stop(0);
        }
    }

    private HttpServer startProvider(CapturedRequests captured) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            captured.record("/v1/chat/completions", readJson(exchange));
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"connection test ok\"}}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":8}}");
        });
        server.createContext("/v1/embeddings", exchange -> {
            captured.record("/v1/embeddings", readJson(exchange));
            respond(exchange, 200, "{\"data\":[{\"embedding\":[0.01,0.02]}],\"usage\":{\"prompt_tokens\":12,\"total_tokens\":12}}");
        });
        server.createContext("/v1/search", exchange -> {
            captured.record("/v1/search", readJson(exchange));
            respond(exchange, 200, "{\"results\":[{\"title\":\"topology result\"}],\"usage\":{\"input_tokens\":12,\"output_tokens\":0}}");
        });
        server.start();
        return server;
    }

    private long createModel(MockHttpSession session, String provider, String modelId, String alias, String modelType, String baseUrl, String useCase) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "provider", provider,
                "modelId", modelId,
                "alias", alias,
                "modelType", modelType,
                "baseUrl", baseUrl,
                "apiKey", "test-key",
                "active", true,
                "useCases", java.util.List.of(useCase)
        ));
        MvcResult created = mockMvc.perform(post("/api/admin/model-channels").with(csrf()).session(session)
                        .contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private AuditLog modelTestAudit(long modelId, String result) {
        return auditLogs.findAll().stream()
                .filter(log -> "AI_MODEL".equals(log.getModule()))
                .filter(log -> "MODEL_TEST".equals(log.getAction()))
                .filter(log -> Long.valueOf(modelId).equals(log.getTargetId()))
                .filter(log -> result.equals(log.getResult()))
                .findFirst()
                .orElseThrow();
    }

    private JsonNode readJson(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
        return objectMapper.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static class CapturedRequests {
        private final Map<String, Map<String, JsonNode>> requests = new ConcurrentHashMap<>();

        void record(String path, JsonNode body) {
            requests.computeIfAbsent(path, ignored -> new ConcurrentHashMap<>()).put(body.path("model").asText(), body);
        }

        JsonNode body(String path, String modelId) {
            return requests.getOrDefault(path, Map.of()).get(modelId);
        }
    }
}

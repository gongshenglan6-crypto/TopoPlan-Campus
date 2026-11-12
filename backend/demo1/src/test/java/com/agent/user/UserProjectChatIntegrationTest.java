package com.agent.user;

import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agent.ai.AiConversationMessageRepository;
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

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProjectChatIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AiConversationMessageRepository conversationMessages;

    @Test
    void userCanPersistProjectsAndChatButCannotReadAnotherUsersProject() throws Exception {
        HttpServer provider = startOpenAiCompatibleProvider();
        try {
        MockHttpSession adminSession = login("admin", "admin");
        mockMvc.perform(post("/api/admin/model-channels").with(csrf()).session(adminSession)
                        .contentType(APPLICATION_JSON)
                        .content("{\"provider\":\"Local Test\",\"modelId\":\"topo-test\",\"alias\":\"Local Test Model\",\"baseUrl\":\"http://127.0.0.1:" + provider.getAddress().getPort() + "/v1\",\"apiKey\":\"test-key\",\"active\":true,\"priority\":0,\"useCases\":[\"ai-chat\"]}"))
                .andExpect(status().isOk());
        MvcResult secondaryModel = mockMvc.perform(post("/api/admin/model-channels").with(csrf()).session(adminSession)
                        .contentType(APPLICATION_JSON)
                        .content("{\"provider\":\"Local Test\",\"modelId\":\"topo-disabled-test\",\"alias\":\"Disabled Test Model\",\"baseUrl\":\"http://127.0.0.1:" + provider.getAddress().getPort() + "/v1\",\"apiKey\":\"test-key\",\"active\":true,\"priority\":9999,\"useCases\":[\"ai-chat\"]}"))
                .andExpect(status().isOk())
                .andReturn();
        long secondaryModelId = dataId(secondaryModel);

        MockHttpSession ownerSession = register("owner_" + System.nanoTime());
        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .with(csrf())
                        .session(ownerSession)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Office network\",\"type\":\"enterprise\",\"color\":\"#1a73e8\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Office network"))
                .andReturn();
        long projectId = dataId(projectResult);

        mockMvc.perform(get("/api/projects").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(projectId));

        MvcResult sessionResult = mockMvc.perform(post("/api/conversations")
                        .with(csrf())
                        .session(ownerSession)
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"新对话\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("新对话"))
                .andReturn();
        long chatSessionId = dataId(sessionResult);
        String chatToken = objectMapper.readTree(sessionResult.getResponse().getContentAsString()).path("data").path("publicId").asText();

        mockMvc.perform(patch("/api/admin/model-channels/" + secondaryModelId + "/status").with(csrf()).session(adminSession)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        MvcResult rejectedChat = mockMvc.perform(post("/api/conversations").with(csrf()).session(ownerSession)
                .contentType(APPLICATION_JSON).content("{\"title\":\"Rejected model test\"}"))
                .andExpect(status().isOk()).andReturn();
        String rejectedToken = objectMapper.readTree(rejectedChat.getResponse().getContentAsString()).path("data").path("publicId").asText();
        mockMvc.perform(post("/api/conversations/" + rejectedToken + "/messages").with(csrf()).session(ownerSession)
                        .contentType(APPLICATION_JSON)
                        .content("{\"content\":\"Use the disabled model\",\"modelId\":" + secondaryModelId + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODEL_DISABLED"));

        mockMvc.perform(patch("/api/admin/model-channels/" + secondaryModelId + "/status").with(csrf()).session(adminSession)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(post("/api/conversations/" + chatToken + "/messages").with(csrf()).session(ownerSession)
                        .contentType(APPLICATION_JSON)
                        .content("{\"content\":\"帮我规划一个三层办公楼网络\",\"modelId\":" + secondaryModelId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assistantMessage.content").value("核心交换机负责园区网络的高速汇聚与转发。"));

        mockMvc.perform(get("/api/conversations/" + chatToken).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("三层办公楼网络规划"));

        mockMvc.perform(post("/api/conversations/" + chatToken + "/messages")
                        .with(csrf())
                        .session(ownerSession)
                        .contentType(APPLICATION_JSON)
                        .content("{\"content\":\"Generate a network topology\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userMessage.role").value("user"))
                .andExpect(jsonPath("$.data.assistantMessage.role").value("assistant"))
                .andExpect(jsonPath("$.data.assistantMessage.content").value("核心交换机负责园区网络的高速汇聚与转发。"));

        mockMvc.perform(get("/api/chat/sessions/" + chatSessionId + "/messages").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(4));

        mockMvc.perform(get("/api/chat/conversations/" + chatSessionId + "/messages").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].role").value("user"))
                .andExpect(jsonPath("$.data.items[0].content").value("帮我规划一个三层办公楼网络"))
                .andExpect(jsonPath("$.data.items.length()").value(4));

        assertEquals(4, conversationMessages.findByConversationIdOrderByCreatedAtAsc(chatSessionId).size());
        assertTrue(conversationMessages.findByConversationIdOrderByCreatedAtAsc(chatSessionId).stream()
                .allMatch(message -> message.getUserId() != null
                        && message.getTotalTokens() >= message.getInputTokens() + message.getOutputTokens()));

        MockHttpSession anotherSession = register("another_" + System.nanoTime());
        mockMvc.perform(get("/api/projects/" + projectId).session(anotherSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_FORBIDDEN"));
        mockMvc.perform(get("/api/projects/" + projectId + "/topology").session(anotherSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT_FORBIDDEN"));
        mockMvc.perform(get("/api/conversations/" + chatToken).session(anotherSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_AVAILABLE"));
        mockMvc.perform(get("/api/conversations/" + chatToken + "/messages").session(anotherSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_AVAILABLE"));
        mockMvc.perform(patch("/api/conversations/" + chatToken).with(csrf()).session(anotherSession)
                        .contentType(APPLICATION_JSON).content("{\"title\":\"stolen\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_AVAILABLE"));
        mockMvc.perform(post("/api/conversations/" + chatToken + "/messages").with(csrf()).session(anotherSession)
                        .contentType(APPLICATION_JSON).content("{\"content\":\"stolen\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_AVAILABLE"));
        mockMvc.perform(get("/api/conversations").session(anotherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
        } finally {
            provider.stop(0);
        }
    }

    private HttpServer startOpenAiCompatibleProvider() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"核心交换机负责园区网络的高速汇聚与转发。\"}}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":16,\"total_tokens\":28}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"avatar\":\"\",\"nickname\":\"Integration User\",\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long dataId(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }
}

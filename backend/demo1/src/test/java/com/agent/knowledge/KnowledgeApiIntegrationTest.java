package com.agent.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KnowledgeApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void administratorCanPersistRetrieveConfigureAndDeleteKnowledge() throws Exception {
        MockHttpSession session = login();
        String name = "真实检索测试-" + System.nanoTime() + ".txt";
        MockMultipartFile file = new MockMultipartFile("file", name, "text/plain",
                "核心交换机负责园区网络的高速交换、三层路由汇聚和关键链路冗余。".getBytes(StandardCharsets.UTF_8));

        MvcResult uploaded = mockMvc.perform(multipart("/api/admin/knowledge/documents/upload")
                        .file(file).param("category", "交换技术").param("uploader", "系统管理员")
                        .with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.status").value("ready"))
                .andExpect(jsonPath("$.data.chunks").isNumber())
                .andReturn();
        long id = objectMapper.readTree(uploaded.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/admin/knowledge/retrieval-test").with(csrf()).session(session)
                        .contentType(APPLICATION_JSON)
                        .content("{\"query\":\"核心交换机\",\"category\":\"交换技术\",\"topK\":5,\"retrievalMethod\":\"hybrid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.results[0].source").value(name));

        mockMvc.perform(put("/api/admin/knowledge/rag-config").with(csrf()).session(session)
                        .contentType(APPLICATION_JSON).content("{\"topK\":3,\"chunkSize\":600}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topK").value(3))
                .andExpect(jsonPath("$.data.chunkSize").value(600));
        mockMvc.perform(get("/api/admin/knowledge/rag-config").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.topK").value(3));

        mockMvc.perform(delete("/api/admin/knowledge/documents/" + id).with(csrf()).session(session))
                .andExpect(status().isOk());
        JsonNode documents = objectMapper.readTree(mockMvc.perform(get("/api/admin/knowledge/documents").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("items");
        for (JsonNode document : documents) org.junit.jupiter.api.Assertions.assertNotEquals(name, document.path("name").asText());
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}

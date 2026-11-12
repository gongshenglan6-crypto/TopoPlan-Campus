package com.agent.file;

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

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectFileIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void projectFilesPersistMetadataAndRejectUnsupportedExtensions() throws Exception {
        MockHttpSession session = register("file_" + System.nanoTime());
        MvcResult project = mockMvc.perform(post("/api/projects").with(csrf()).session(session)
                        .contentType("application/json")
                        .content("{\"name\":\"File project\",\"type\":\"qa\",\"color\":\"#1a73e8\"}"))
                .andExpect(status().isOk()).andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).path("data").path("id").asLong();

        MockMultipartFile supported = new MockMultipartFile("file", "requirements.md", "text/markdown", "network requirements".getBytes());
        mockMvc.perform(multipart("/api/projects/" + projectId + "/files").file(supported).with(csrf()).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("requirements.md"));
        mockMvc.perform(get("/api/projects/" + projectId + "/files").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));

        MockMultipartFile unsupported = new MockMultipartFile("file", "payload.exe", "application/octet-stream", "bad".getBytes());
        mockMvc.perform(multipart("/api/projects/" + projectId + "/files").file(unsupported).with(csrf()).session(session))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void docxProjectMaterialIsExtractedForAiContext() throws Exception {
        MockHttpSession session = register("docx_" + System.nanoTime());
        MvcResult project = mockMvc.perform(post("/api/projects").with(csrf()).session(session)
                        .contentType("application/json")
                        .content("{\"name\":\"DOCX project\",\"type\":\"qa\",\"color\":\"#1a73e8\"}"))
                .andExpect(status().isOk()).andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).path("data").path("id").asLong();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>研发网络需要独立 VLAN</w:t></w:r></w:p></w:body></w:document>".getBytes());
            zip.closeEntry();
        }
        MockMultipartFile docx = new MockMultipartFile("file", "requirements.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes.toByteArray());
        mockMvc.perform(multipart("/api/projects/" + projectId + "/files").file(docx).with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已读取"))
                .andExpect(jsonPath("$.data.textAvailable").value(true))
                .andExpect(jsonPath("$.data.textLength").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    private MockHttpSession register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                        .content("{\"avatar\":\"\",\"nickname\":\"File User\",\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}

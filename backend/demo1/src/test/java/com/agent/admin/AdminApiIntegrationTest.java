package com.agent.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agent.admin.config.AdminConfig;
import com.agent.admin.config.AdminConfigRepository;
import com.agent.admin.config.SystemServiceHealthRepository;
import com.agent.login.LoginRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AdminConfigRepository adminConfigRepository;
    @Autowired SystemServiceHealthRepository systemServiceHealthRepository;
    @Autowired LoginRecordRepository loginRecordRepository;

    @Test
    void systemManagementUsesPersistedRulesAndLiveHealthChecks() throws Exception {
        MockHttpSession session = login("admin", "admin");

        mockMvc.perform(get("/api/admin/system-rules").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.ruleGroups.length()").value(5));
        org.junit.jupiter.api.Assertions.assertTrue(adminConfigRepository
                .findByModuleAndItemKey("system.rules", "default").isPresent());

        mockMvc.perform(get("/api/admin/monitoring?force=true").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkedAt").exists())
                .andExpect(jsonPath("$.data.metrics.todayLogCount").isNumber())
                .andExpect(jsonPath("$.data.services[?(@.name == 'mysql')].status").value("UP"))
                .andExpect(jsonPath("$.data.services[?(@.name == 'redis')]").isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(systemServiceHealthRepository.count() >= 4);
    }

    @Test
    void adminCanReadDashboardAndManageUsers() throws Exception {
        MockHttpSession session = login("admin", "admin");
        mockMvc.perform(get("/api/admin/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.length()").value(5));

        String username = "managed_" + System.nanoTime();
        MvcResult created = mockMvc.perform(post("/api/admin/users").with(csrf()).session(session)
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"initial-pass\",\"displayName\":\"Managed User\",\"email\":\"" + username + "@example.com\",\"role\":\"USER\",\"tier\":\"basic\",\"dailyQuota\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.dailyQuota").value(20))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/api/admin/users?keyword=" + username).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
        mockMvc.perform(post("/api/admin/users/" + id + "/status").with(csrf()).session(session)
                        .contentType(APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISABLED"));
        mockMvc.perform(post("/api/admin/users/" + id + "/password").with(csrf()).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty());
    }

    @Test
    void administratorCanReadFilteredPersistedLoginRecords() throws Exception {
        MockHttpSession adminSession = login("admin", "admin");
        String failedIdentifier = "admin_audit_" + System.nanoTime();

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .header("User-Agent", "Admin Audit Browser/2.0")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + failedIdentifier + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/login-records").session(adminSession)
                        .queryParam("keyword", failedIdentifier)
                        .queryParam("result", "失败")
                        .queryParam("page", "0")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].loginIdentifier").value(failedIdentifier))
                .andExpect(jsonPath("$.data.items[0].ipAddress").value("127.0.0.1"))
                .andExpect(jsonPath("$.data.items[0].failureReason").value("用户名或密码错误"));
        org.junit.jupiter.api.Assertions.assertTrue(loginRecordRepository.count() >= 2);
        mockMvc.perform(get("/api/admin/login-records/stats").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.today").isNumber())
                .andExpect(jsonPath("$.data.failure").isNumber());
    }

    @Test
    void administratorCanUpdateLoginIdentifiersAndPassword() throws Exception {
        MockHttpSession adminSession = login("admin", "admin");
        String username = "password_reset_" + System.nanoTime();
        String email = username + "@example.com";
        String updatedUsername = "topo_" + System.nanoTime();
        String updatedEmail = updatedUsername + "@example.com";
        MvcResult created = mockMvc.perform(post("/api/admin/users").with(csrf()).session(adminSession)
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"initial-password\",\"displayName\":\"Password Reset User\",\"email\":\"" + email + "\",\"role\":\"USER\",\"tier\":\"free\",\"dailyQuota\":10}"))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(patch("/api/admin/users/" + id).with(csrf()).session(adminSession)
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + updatedUsername + "\",\"displayName\":\"Password Reset User\",\"email\":\"" + updatedEmail + "\",\"role\":\"USER\",\"status\":\"ACTIVE\",\"tier\":\"free\",\"dailyQuota\":10,\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(updatedUsername))
                .andExpect(jsonPath("$.data.email").value(updatedEmail));

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + updatedUsername + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(updatedUsername));

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + updatedEmail + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(updatedUsername));
    }

    @Test
    void regularUserIsForbiddenFromAdminApi() throws Exception {
        String username = "ordinary_" + System.nanoTime();
        MvcResult result = mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"avatar\":\"\",\"nickname\":\"Ordinary\",\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mockMvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void administratorCanPersistAndDeleteRiskBlacklistEntries() throws Exception {
        MockHttpSession session = login("admin", "admin");
        String value = "blocked_" + System.nanoTime();
        MvcResult created = mockMvc.perform(post("/api/admin/risk-blacklist").with(csrf()).session(session)
                        .contentType(APPLICATION_JSON)
                        .content("{\"type\":\"keyword\",\"value\":\"" + value + "\",\"duration\":\"24h\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value(value))
                .andReturn();
        String key = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("key").asText();

        mockMvc.perform(delete("/api/admin/risk-blacklist/" + key).with(csrf()).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/risk-blacklist").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.value == '" + value + "')]").isEmpty());
    }

    @Test
    void securityRiskRulesEnforcePersistedBlacklistAndPersistEventHandling() throws Exception {
        MockHttpSession adminSession = login("admin", "admin");
        String keyword = "risk_keyword_" + System.nanoTime();
        MvcResult blacklist = mockMvc.perform(post("/api/admin/risk-blacklist").with(csrf()).session(adminSession)
                        .contentType(APPLICATION_JSON)
                        .content("{\"type\":\"keyword\",\"object\":\"" + keyword + "\",\"reason\":\"integration test\",\"duration\":\"24h\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.object").value(keyword))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andReturn();
        String blacklistId = objectMapper.readTree(blacklist.getResponse().getContentAsString()).path("data").path("key").asText();

        String username = "risk_user_" + System.nanoTime();
        MvcResult registration = mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"avatar\":\"\",\"nickname\":\"Risk User\",\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"risk-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession userSession = (MockHttpSession) registration.getRequest().getSession(false);

        MvcResult conversation = mockMvc.perform(post("/api/conversations").with(csrf()).session(userSession)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        long conversationId = objectMapper.readTree(conversation.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages").with(csrf()).session(userSession)
                        .contentType(APPLICATION_JSON)
                        .content("{\"content\":\"请处理 " + keyword + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RISK_BLOCKED"));

        MvcResult overview = mockMvc.perform(get("/api/admin/security/overview").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.length()").value(5))
                .andReturn();
        JsonNode events = objectMapper.readTree(overview.getResponse().getContentAsString()).path("data").path("events");
        JsonNode blocked = null;
        for (JsonNode event : events) {
            if ("RISK_BLOCK".equals(event.path("rule").asText()) && event.path("summary").asText().contains("关键词黑名单")) {
                blocked = event;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(blocked);
        String eventId = blocked.path("id").asText();

        mockMvc.perform(patch("/api/admin/security/events/" + eventId + "/status").with(csrf()).session(adminSession)
                        .contentType(APPLICATION_JSON).content("{\"status\":\"processed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("processed"));
        mockMvc.perform(get("/api/admin/security/overview").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[?(@.id == '" + eventId + "')].status").value("processed"));

        mockMvc.perform(delete("/api/admin/risk-blacklist/" + blacklistId).with(csrf()).session(adminSession))
                .andExpect(status().isOk());
    }

    @Test
    void administratorCanPersistModelApiKeyWithoutExposingIt() throws Exception {
        MockHttpSession session = login("admin", "admin");
        String modelId = "model_" + System.nanoTime();
        String apiKey = "secret-key-" + System.nanoTime();
        String payload = "{\"provider\":\"Test\",\"modelId\":\"" + modelId + "\",\"alias\":\"Test model\",\"modelType\":\"reasoning\",\"baseUrl\":\"https://example.com/v1\",\"apiKey\":\"" + apiKey + "\",\"active\":true,\"contextLimit\":256000,\"maxTokens\":8192,\"temperature\":0.65,\"topP\":0.88,\"rpm\":321,\"tpm\":654321,\"concurrency\":7,\"maxConcurrency\":45,\"timeout\":75,\"inputPrice\":1.25,\"outputPrice\":2.5,\"cachedTokenPrice\":0.3,\"currency\":\"CNY\",\"priceUnit\":1000000,\"quotaMultiplier\":1.6,\"priority\":4,\"userSelectable\":false,\"allowedTiers\":\"premium,enterprise\",\"useCases\":[\"ai-chat\",\"network-plan\"]}";

        MvcResult created = mockMvc.perform(post("/api/admin/model-channels").with(csrf()).session(session)
                .contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.rpm").value(321))
                .andExpect(jsonPath("$.data.tpm").value(654321))
                .andExpect(jsonPath("$.data.concurrency").value(7))
                .andExpect(jsonPath("$.data.quotaMultiplier").value(1.6))
                .andReturn();
        String key = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("key").asText();

        mockMvc.perform(get("/api/admin/model-channels").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.key == '" + key + "')].apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.items[?(@.key == '" + key + "')].apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.items[?(@.key == '" + key + "')].modelType").value("reasoning"))
                .andExpect(jsonPath("$.data.items[?(@.key == '" + key + "')].contextLimit").value(256000))
                .andExpect(jsonPath("$.data.items[?(@.key == '" + key + "')].maxTokens").value(8192))
                .andExpect(jsonPath("$.data.items[?(@.key == '" + key + "')].rpm").value(321))
                .andExpect(jsonPath("$.data.items[?(@.key == '" + key + "')].tpm").value(654321))
                .andExpect(jsonPath("$.data.items[?(@.key == '" + key + "')].concurrency").value(7))
                .andExpect(jsonPath("$.data.items[?(@.key == '" + key + "')].maxConcurrency").value(45))
                .andExpect(jsonPath("$.data.items[?(@.key == '" + key + "')].timeout").value(75))
                .andExpect(jsonPath("$.data.items[?(@.key == '" + key + "')].inputPrice").value(1.25))
                .andExpect(jsonPath("$.data.items[?(@.key == '" + key + "')].outputPrice").value(2.5))
                .andExpect(jsonPath("$.data.items[?(@.key == '" + key + "')].quotaMultiplier").value(1.6))
                .andExpect(jsonPath("$.data.items[?(@.key == '" + key + "')].priority").value(4))
                .andExpect(jsonPath("$.data.items[?(@.key == '" + key + "')].userSelectable").value(false))
                .andExpect(jsonPath("$.data.items[?(@.key == '" + key + "')].useCases.length()").value(2));

        AdminConfig record = adminConfigRepository.findByModuleAndItemKey("models.channels", key).orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(record.getPayload().contains(apiKey));
        org.junit.jupiter.api.Assertions.assertNotNull(record.getSecretPayload());
        org.junit.jupiter.api.Assertions.assertNotEquals(apiKey, record.getSecretPayload());

        mockMvc.perform(patch("/api/admin/model-channels/" + key).with(csrf()).session(session)
                        .contentType(APPLICATION_JSON).content("{\"alias\":\"Updated model\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alias").value("Updated model"))
                .andExpect(jsonPath("$.data.apiKeyConfigured").value(true));
    }

    @Test
    void administratorCanChangeModelStatusAndSeeAnAuditableDatabaseBackedResult() throws Exception {
        MockHttpSession session = login("admin", "admin");
        String defaultModelId = "default_model_" + System.nanoTime();
        String secondaryModelId = "secondary_model_" + System.nanoTime();

        MvcResult defaultModel = mockMvc.perform(post("/api/admin/model-channels").with(csrf()).session(session)
                        .contentType(APPLICATION_JSON)
                        .content("{\"provider\":\"Test\",\"modelId\":\"" + defaultModelId + "\",\"alias\":\"Default model\",\"baseUrl\":\"https://example.com/v1\",\"apiKey\":\"key-default\",\"active\":true,\"priority\":0,\"useCases\":[\"ai-chat\"]}"))
                .andExpect(status().isOk())
                .andReturn();
        long defaultId = objectMapper.readTree(defaultModel.getResponse().getContentAsString()).path("data").path("id").asLong();

        MvcResult secondary = mockMvc.perform(post("/api/admin/model-channels").with(csrf()).session(session)
                        .contentType(APPLICATION_JSON)
                        .content("{\"provider\":\"Test\",\"modelId\":\"" + secondaryModelId + "\",\"alias\":\"Secondary model\",\"baseUrl\":\"https://example.com/v1\",\"apiKey\":\"key-secondary\",\"active\":true,\"priority\":9999,\"useCases\":[\"ai-chat\"]}"))
                .andExpect(status().isOk())
                .andReturn();
        long secondaryId = objectMapper.readTree(secondary.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(patch("/api/admin/model-channels/" + defaultId + "/status").with(csrf()).session(session)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(patch("/api/admin/model-channels/" + secondaryId + "/status").with(csrf()).session(session)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODEL_LAST_ENABLED"))
                .andExpect(jsonPath("$.message").value("至少保留一个启用模型，无法停用当前模型。"));

        mockMvc.perform(patch("/api/admin/model-channels/" + defaultId + "/status").with(csrf()).session(session)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(patch("/api/admin/model-channels/" + secondaryId + "/status").with(csrf()).session(session)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(get("/api/admin/model-channels").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + secondaryId + ")].enabled").value(false));

        mockMvc.perform(get("/api/admin/audit-logs?keyword=" + secondaryModelId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].actor").value("admin"))
                .andExpect(jsonPath("$.data.items[0].action").value("MODEL_STATUS_CHANGED"))
                .andExpect(jsonPath("$.data.items[0].traceId").isNotEmpty());

        mockMvc.perform(patch("/api/admin/model-channels/" + secondaryId + "/status").with(csrf()).session(session)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void billingOverviewIsDerivedFromPersistedUsageInsteadOfManualMockValues() throws Exception {
        MockHttpSession session = login("admin", "admin");
        String payload = "{\"monthlyCost\":12684.72,\"monthlyChangePercent\":-6.2,\"dailyCosts\":[{\"day\":\"today\",\"value\":1338.72}],\"modelBreakdown\":[],\"topUsers\":[]}";

        mockMvc.perform(put("/api/admin/billing/overview").with(csrf()).session(session)
                        .contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyCost").value(0));
        mockMvc.perform(get("/api/admin/billing/overview").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyCost").value(0))
                .andExpect(jsonPath("$.data.callCount").isNumber());
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}

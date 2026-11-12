package com.agent.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agent.user.UserRepository;
import com.agent.login.LoginRecord;
import com.agent.login.LoginRecordRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import jakarta.servlet.http.Cookie;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AuthControllerIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    LoginRecordRepository loginRecordRepository;

    @org.junit.jupiter.api.BeforeEach
    void prepareOrdinaryTestAccount() {
        if (!userRepository.existsByUsernameIgnoreCase("demo@example.test")) {
            var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
            userRepository.save(new com.agent.user.User("demo@example.test", encoder.encode("admin"),
                    "Demo User", "demo@example.test", "USER"));
        }
    }

    @Test
    void anonymousAdminApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blockedRetriesDoNotExtendLoginLockout() throws Exception {
        String name = "cooldown_" + System.nanoTime();
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        var user = userRepository.save(new com.agent.user.User(name, encoder.encode("correct-password"), name,
                name + "@example.com", "USER"));
        for (int i = 0; i < 5; i++) {
            loginRecordRepository.save(new LoginRecord(user.getId(), name, name, "普通用户", "账号密码",
                    "127.0.0.1", "", "", "", "", "", "失败", "已拦截", "安全策略拦截"));
        }
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("username", name, "password", "correct-password"))))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanLoginAndRestoreSession() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void seededOrdinaryAccountCanLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"demo@example.test\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("demo@example.test"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void invalidCredentialsAreRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void successfulAndFailedLoginsArePersistedWithRequestMetadata() throws Exception {
        String failedIdentifier = "missing_" + System.nanoTime();

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128.0.0.0 Safari/537.36")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"demo@example.test\",\"password\":\"admin\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .header("User-Agent", "Audit Test Agent/1.0")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + failedIdentifier + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        LoginRecord success = loginRecordRepository.findAll().stream()
                .filter(record -> "demo@example.test".equals(record.getLoginIdentifier()) && "成功".equals(record.getResult()))
                .reduce((first, second) -> second)
                .orElseThrow();
        LoginRecord failure = loginRecordRepository.findAll().stream()
                .filter(record -> failedIdentifier.equals(record.getLoginIdentifier()))
                .findFirst()
                .orElseThrow();

        assertEquals("127.0.0.1", success.getIpAddress());
        assertEquals("Google Chrome 128.0.0.0", success.getBrowser());
        assertEquals("失败", failure.getResult());
        assertEquals("用户名或密码错误", failure.getFailureReason());
        assertEquals("Audit Test Agent/1.0", failure.getUserAgent());
    }

    @Test
    void registrationRestoresSessionAndLogoutInvalidatesIt() throws Exception {
        String username = "user_" + System.nanoTime();
        MvcResult register = mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"avatar\":\"\",\"nickname\":\"Test User\",\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) register.getRequest().getSession(false);

        assertNotNull(session);
        assertEquals(session.getId(), session.getAttribute("session_id"));
        assertEquals(username, session.getAttribute("username"));
        assertNotNull(session.getAttribute("user_id"));
        assertEquals("USER", session.getAttribute("role"));
        assertNotNull(session.getAttribute("login_time"));
        assertNotNull(session.getAttribute("last_active_time"));

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));
        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(0, result.getResponse().getCookie("TOPOSESSION").getMaxAge()));
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRotatesExistingSessionAndDisabledAccountCannotReuseIt() throws Exception {
        String username = "session_" + System.nanoTime();
        MockHttpSession csrfSession = new MockHttpSession();
        String previousId = csrfSession.getId();
        mockMvc.perform(get("/api/auth/csrf").session(csrfSession)).andExpect(status().isOk());

        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .session(csrfSession)
                        .contentType(APPLICATION_JSON)
                        .content("{\"avatar\":\"\",\"nickname\":\"Session User\",\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);
        assertNotEquals(previousId, session.getId());

        com.agent.user.User user = userRepository.findByUsernameIgnoreCase(username).orElseThrow();
        user.setStatus("DISABLED");
        userRepository.save(user);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void registrationAcceptsTheRawCsrfTokenReturnedForTheSpa() throws Exception {
        MockHttpSession csrfSession = new MockHttpSession();
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").session(csrfSession))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString())
                .path("data")
                .asText();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertEquals(36, csrfToken.length());
        assertEquals(csrfToken, csrfCookie.getValue());
        String username = "spa_" + System.nanoTime();

        mockMvc.perform(post("/api/auth/register")
                        .session(csrfSession)
                        .cookie(csrfCookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"avatar\":\"\",\"nickname\":\"SPA User\",\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));
    }

    @Test
    void authenticatedUserCanChangePasswordAndLoginWithTheNewPassword() throws Exception {
        String username = "password_" + System.nanoTime();
        String oldPassword = "old-secret";
        String newPassword = "new-secret";
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"avatar\":\"\",\"nickname\":\"Password User\",\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"" + oldPassword + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/change-password")
                        .with(csrf())
                        .session(session)
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + oldPassword + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + oldPassword + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));
    }

    @Test
    void authenticatedUserCanUpdateUsernameAndEmailAndUseEitherToLogIn() throws Exception {
        String username = "profile_update_" + System.nanoTime();
        String email = username + "@example.com";
        String updatedUsername = "topo_" + System.nanoTime();
        String updatedEmail = updatedUsername + "@example.com";
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"avatar\":\"\",\"nickname\":\"Profile User\",\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"profile-pass\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);

        mockMvc.perform(put("/api/auth/profile")
                        .with(csrf())
                        .session(session)
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + updatedUsername + "\",\"displayName\":\"Updated Profile\",\"email\":\"" + updatedEmail + "\",\"avatar\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(updatedUsername))
                .andExpect(jsonPath("$.data.email").value(updatedEmail));

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(updatedUsername))
                .andExpect(jsonPath("$.data.email").value(updatedEmail));
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + updatedUsername + "\",\"password\":\"profile-pass\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + updatedEmail + "\",\"password\":\"profile-pass\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void regularUserCannotAccessAdminApi() throws Exception {
        String username = "role_" + System.nanoTime();
        MvcResult register = mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"avatar\":\"\",\"nickname\":\"Role User\",\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) register.getRequest().getSession(false);
        mockMvc.perform(get("/api/admin/ping").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationPersistsProfileFieldsAndRejectsDuplicateEmail() throws Exception {
        String username = "profile_" + System.nanoTime();
        String email = username + "@example.com";
        String avatar = "data:image/png;base64,ZmFrZS1hdmF0YXI=";

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"avatar\":\"" + avatar + "\",\"nickname\":\"Profile User\",\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"profile-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.displayName").value("Profile User"));
        assertEquals(avatar, userRepository.findByUsernameIgnoreCase(username).orElseThrow().getAvatar());

        String duplicateUsername = "profile_duplicate_" + System.nanoTime();
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"avatar\":\"\",\"nickname\":\"Another User\",\"username\":\"" + duplicateUsername + "\",\"email\":\"" + email + "\",\"password\":\"profile-pass\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
    }
}

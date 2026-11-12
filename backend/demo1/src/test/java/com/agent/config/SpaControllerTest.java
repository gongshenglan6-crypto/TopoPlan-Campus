package com.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SpaControllerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SpaController()).build();

    @Test
    void refreshingKnownPagesServesTheSameApp() throws Exception {
        for (String path : new String[]{"/", "/login", "/admin", "/admin/models", "/projects", "/project/example", "/c/example"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void apiAndMissingAssetsAreNotReplacedWithHtml() throws Exception {
        for (String path : new String[]{"/api/not-found", "/assets/not-found.js", "/uploads/not-found.png"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }
}

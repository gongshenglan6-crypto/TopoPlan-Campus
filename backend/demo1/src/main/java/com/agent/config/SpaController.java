package com.agent.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serve the packaged Vue app on its known routes without masking API or asset errors. */
@Controller
public class SpaController {
    @GetMapping({"/", "/login", "/admin", "/admin/{page}", "/new", "/chat",
            "/chat/{id}", "/c/{id}", "/project/{id}", "/chat-search", "/projects",
            "/requirements", "/files", "/topology", "/report", "/risk", "/export", "/versions"})
    public String app() {
        return "forward:/index.html";
    }
}

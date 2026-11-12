package com.agent.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 60) String type,
        @Size(max = 16) String color,
        @Size(max = 20) String creationMode,
        @Size(max = 2000) String description,
        @Size(max = 80) String clientRequestId) {
}

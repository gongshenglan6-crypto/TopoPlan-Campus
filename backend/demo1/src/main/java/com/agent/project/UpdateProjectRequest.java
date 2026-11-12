package com.agent.project;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @Size(max = 160) String name,
        @Size(max = 60) String type,
        @Size(max = 16) String color,
        @Size(max = 40) String status,
        @Min(0) @Max(100000) Integer terminals,
        @Size(max = 20) String creationMode,
        @Size(max = 2000) String description) {
}

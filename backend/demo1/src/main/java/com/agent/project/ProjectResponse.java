package com.agent.project;

import java.time.Instant;

public record ProjectResponse(Long id, String publicId, String name, String type, String color, String creationMode, String description, String status,
                              int terminals, int files, String topologyVersion, Instant updatedAt) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getPublicId(), project.getName(), project.getType(), project.getColor(),
                project.getCreationMode(), project.getDescription(), project.getStatus(), project.getTerminals(),
                project.getFileCount(), project.getTopologyVersion(), project.getUpdatedAt());
    }
}

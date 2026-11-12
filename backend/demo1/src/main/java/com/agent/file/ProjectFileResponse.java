package com.agent.file;

import java.time.Instant;

public record ProjectFileResponse(Long id, String name, String format, long size, String status, boolean textAvailable,
                                  int textLength, String errorMessage, Instant createdAt) {
    public static ProjectFileResponse from(ProjectFile file) {
        String text = file.getExtractedText();
        return new ProjectFileResponse(file.getId(), file.getName(), file.getFormat(), file.getSize(), file.getStatus(),
                file.hasReadableText(), text.length(), file.getErrorMessage(), file.getCreatedAt());
    }
}

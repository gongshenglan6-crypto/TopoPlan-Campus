package com.agent.ai;

import java.util.Locale;

public enum AiRequestType {
    CHAT,
    EMBEDDING,
    SEARCH;

    public static AiRequestType forModel(AiModel model) {
        String type = model == null || model.getModelType() == null ? "" : model.getModelType().trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "embedding" -> EMBEDDING;
            case "search", "web_search", "web-search" -> SEARCH;
            default -> CHAT;
        };
    }
}

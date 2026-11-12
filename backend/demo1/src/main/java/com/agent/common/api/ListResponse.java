package com.agent.common.api;

import java.util.List;

public record ListResponse<T>(List<T> items, long total) {
    public ListResponse(List<T> items, long total, int page, int size) {
        this(items, total);
    }
    public static <T> ListResponse<T> of(List<T> items) {
        return new ListResponse<>(items, items.size());
    }
}

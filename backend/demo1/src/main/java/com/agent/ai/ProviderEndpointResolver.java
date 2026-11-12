package com.agent.ai;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class ProviderEndpointResolver {
    private ProviderEndpointResolver() {
    }

    static String endpoint(String baseUrl, String endpoint) {
        String base = normalizeBaseUrl(baseUrl);
        String suffix = endpoint == null ? "" : endpoint.trim().replaceAll("^/+", "");
        if (suffix.isBlank()) return base;

        String normalizedSuffix = "/" + suffix.toLowerCase(Locale.ROOT);
        String normalizedBase = base.toLowerCase(Locale.ROOT);
        if (normalizedBase.endsWith(normalizedSuffix)) return base;

        for (String knownEndpoint : new String[]{"/chat/completions", "/embeddings", "/responses", "/search"}) {
            if (normalizedBase.endsWith(knownEndpoint)) {
                base = base.substring(0, base.length() - knownEndpoint.length());
                break;
            }
        }
        return base + "/" + suffix;
    }

    static String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("/+$", "");
        if (normalized.isBlank()) throw new AiGatewayException("INVALID_ENDPOINT", "模型服务地址未配置");
        try {
            URI uri = new URI(normalized);
            if (uri.getScheme() == null || uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new AiGatewayException("INVALID_ENDPOINT", "模型接口地址错误");
            }
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new AiGatewayException("INVALID_ENDPOINT", "Base URL 不能包含查询参数或片段");
            }
            return normalized;
        } catch (URISyntaxException exception) {
            throw new AiGatewayException("INVALID_ENDPOINT", "模型接口地址错误");
        }
    }
}

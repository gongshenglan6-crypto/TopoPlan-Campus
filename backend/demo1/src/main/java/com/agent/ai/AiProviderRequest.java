package com.agent.ai;

import java.util.Map;

record AiProviderRequest(String url, Map<String, Object> payload, String accept) {
}

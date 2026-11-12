package com.agent.ai;

import java.math.BigDecimal;

/** Prompt-level overrides that are intentionally limited to a single model invocation. */
public record AiRequestOverrides(BigDecimal temperature, BigDecimal topP, Integer maxOutputTokens, Integer timeoutSeconds) {
    public static final AiRequestOverrides NONE = new AiRequestOverrides(null, null, null, null);

    public boolean isEmpty() {
        return temperature == null && topP == null && maxOutputTokens == null && timeoutSeconds == null;
    }
}

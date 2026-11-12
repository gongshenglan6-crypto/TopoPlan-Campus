package com.agent.ai;

public class AiGatewayException extends RuntimeException {
    private final String code;
    private final int httpStatus;
    private final String requestUrl;
    private final long latencyMs;
    private final String providerMessage;

    public AiGatewayException(String code, String message) { this(code, message, 0, null, 0); }
    public AiGatewayException(String code, String message, int httpStatus, String requestUrl, long latencyMs) {
        this(code, message, httpStatus, requestUrl, latencyMs, null);
    }
    public AiGatewayException(String code, String message, int httpStatus, String requestUrl, long latencyMs, String providerMessage) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.requestUrl = requestUrl;
        this.latencyMs = Math.max(0, latencyMs);
        this.providerMessage = providerMessage;
    }
    public String getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
    public String getRequestUrl() { return requestUrl; }
    public long getLatencyMs() { return latencyMs; }
    public String getProviderMessage() { return providerMessage; }
}

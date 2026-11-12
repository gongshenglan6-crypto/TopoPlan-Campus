package com.agent.ai;

import com.agent.common.api.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiGatewayService {
    private static final Logger log = LoggerFactory.getLogger(AiGatewayService.class);
    /**
     * Keep only a short provider prefix buffered. This lets the router reject a
     * broken model before the first malformed repetition reaches the browser,
     * while preserving real SSE output for normal responses.
     */
    private static final Pattern REPEATED_WORD = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])([\\p{L}\\p{N}]{2,32})(?:[ \\t]+\\1){3,}(?![\\p{L}\\p{N}])");
    private static final Pattern REPEATED_FRAGMENT = Pattern.compile("(?s)(.{3,24})(?:\\1){3,}");
    private static final Pattern PROTOCOL_LINE = Pattern.compile(
            "(?im)^\\s*(?:assistant|user|system|output|event|data|role|id|index|seq|finish_reason|usage)(?:\\s*[:=].*)?\\s*$");
    private static final Pattern PROTOCOL_PREFIX = Pattern.compile(
            "(?iu)^\\s*(?:assistant|user|system|output)(?:[ \\t]+|[:=])");

    private final AiModelService modelService;
    private final AiProviderAdapterRegistry adapters;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<Long, AtomicInteger> activeRequests = new ConcurrentHashMap<>();

    public AiGatewayService(AiModelService modelService, AiProviderAdapterRegistry adapters, ObjectMapper mapper) {
        this.modelService = modelService;
        this.adapters = adapters;
        this.mapper = mapper;
    }

    public AiGatewayResult complete(AiModel model, List<AiGatewayMessage> messages) {
        return complete(model, messages, AiRequestOverrides.NONE);
    }

    public AiGatewayResult complete(AiModel model, List<AiGatewayMessage> messages, AiRequestOverrides overrides) {
        return invoke(model, messages, false, ignored -> { }, true, false, AiRequestType.CHAT, overrides).result();
    }

    public AiGatewayResult stream(AiModel model, List<AiGatewayMessage> messages, Consumer<String> onDelta) {
        return stream(model, messages, onDelta, AiRequestOverrides.NONE);
    }

    public AiGatewayResult stream(AiModel model, List<AiGatewayMessage> messages, Consumer<String> onDelta,
                                  AiRequestOverrides overrides) {
        return invoke(model, messages, true, onDelta, true, false, AiRequestType.CHAT, overrides).result();
    }

    /** Run a real provider-side web search using the search model configured by the administrator. */
    public AiGatewayResult completeSearch(AiModel model, List<AiGatewayMessage> messages, AiRequestOverrides overrides) {
        return invoke(model, messages, false, ignored -> { }, true, false, AiRequestType.SEARCH, overrides).result();
    }

    public AiModelTestResult test(AiModel model) {
        AiRequestType requestType = AiRequestType.forModel(model);
        try {
            Invocation invocation = invoke(model, List.of(new AiGatewayMessage("user", "请执行模型连接测试并返回成功结果。")),
                    false, ignored -> { }, false, true, requestType, AiRequestOverrides.NONE);
            AiGatewayResult result = invocation.result();
            return new AiModelTestResult(model.getProviderName(), model.getModelId(), requestType.name(), invocation.httpStatus(),
                    result.latencyMs(), result.inputTokens(), result.outputTokens(), result.totalTokens(),
                    invocation.requestUrl(), invocation.adapter());
        } catch (AiGatewayException exception) {
            log.warn("AI model test failed provider={} modelId={} url={} httpStatus={} errorCode={} message={} latencyMs={}",
                    model.getProviderName(), model.getModelId(), exception.getRequestUrl(), exception.getHttpStatus(),
                    exception.getCode(), sanitize(exception.getProviderMessage() == null ? exception.getMessage() : exception.getProviderMessage()), exception.getLatencyMs());
            throw exception;
        }
    }

    private Invocation invoke(AiModel model, List<AiGatewayMessage> messages, boolean stream, Consumer<String> onDelta,
                              boolean requireEnabled, boolean testInvocation, AiRequestType requestType, AiRequestOverrides overrides) {
        if (requestType == AiRequestType.EMBEDDING && !testInvocation) {
            throw new AiGatewayException("MODEL_TYPE_INCOMPATIBLE", "当前业务不支持直接调用 Embedding 模型");
        }
        if (requireEnabled && (!model.isEnabled() || !"ACTIVE".equalsIgnoreCase(model.getStatus()))) {
            throw new AiGatewayException("MODEL_DISABLED", "所选模型已停用，暂时不能调用");
        }

        AtomicInteger counter = activeRequests.computeIfAbsent(model.getId(), ignored -> new AtomicInteger());
        int active = counter.incrementAndGet();
        if (active > model.getMaxConcurrency()) {
            counter.decrementAndGet();
            throw new AiGatewayException("RATE_LIMIT", "模型并发已达到上限");
        }

        long startedAt = System.nanoTime();
        String requestUrl = null;
        HttpURLConnection connection = null;
        try {
            AiProviderAdapter adapter = adapters.forModel(model);
            AiRequestOverrides requestOverrides = overrides == null ? AiRequestOverrides.NONE : overrides;
            AiProviderRequest providerRequest = adapter.createRequest(model, requestType, messages, stream, requestOverrides);
            requestUrl = providerRequest.url();
            if (testInvocation) {
                log.debug("AI model test request provider={} modelId={} requestType={} adapter={} url={}",
                        model.getProviderName(), model.getModelId(), requestType, adapter.providerType(), requestUrl);
            }

            String apiKey = modelService.apiKeyFor(model);
            connection = openConnection(requestUrl, providerRequest, apiKey, model, requestOverrides);
            if (stream) return parseStream(connection, messages, onDelta, startedAt, requestUrl, adapter.providerType(), apiKey);
            int status = connection.getResponseCode();
            String body = readResponseBody(connection, status);
            if (status < 200 || status >= 300) throw providerError(status, body, requestUrl, startedAt, apiKey);
            AiProviderResponse providerResponse = adapter.parseResponse(mapper.readTree(body), requestType, messages);
            validateGeneratedContent(providerResponse.content(), requestUrl);
            return new Invocation(new AiGatewayResult(providerResponse.content(), providerResponse.inputTokens(), providerResponse.outputTokens(), elapsed(startedAt)),
                    requestUrl, status, adapter.providerType());
        } catch (SocketTimeoutException exception) {
            throw new AiGatewayException("CONNECTION_TIMEOUT", "请求模型服务超时", 0, requestUrl, elapsed(startedAt));
        } catch (AiGatewayException exception) {
            throw withRequestDetails(exception, requestUrl, elapsed(startedAt));
        } catch (IllegalArgumentException exception) {
            throw new AiGatewayException("INVALID_ENDPOINT", "模型接口地址错误", 0, requestUrl, elapsed(startedAt));
        } catch (IOException exception) {
            throw networkError(exception, requestUrl, elapsed(startedAt));
        } catch (ApiException exception) {
            throw new AiGatewayException(exception.getCode(), exception.getMessage(), 0, requestUrl, elapsed(startedAt));
        } catch (Exception exception) {
            // Diagnose swallowed runtime failures without logging credentials,
            // exception messages or provider response bodies.
            StackTraceElement[] trace = exception.getStackTrace();
            log.warn("AI_GATEWAY_UNEXPECTED_FAILURE type={} location={} latencyMs={}",
                    exception.getClass().getName(), trace.length == 0 ? "unknown" : trace[0], elapsed(startedAt));
            throw new AiGatewayException("PROVIDER_ERROR", "模型服务响应无效", 0, requestUrl, elapsed(startedAt));
        } finally {
            if (connection != null) connection.disconnect();
            counter.decrementAndGet();
        }
    }

    private HttpURLConnection openConnection(String requestUrl, AiProviderRequest providerRequest, String apiKey,
                                             AiModel model, AiRequestOverrides overrides) throws IOException {
        URL url = URI.create(requestUrl).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        int timeoutSeconds = Math.max(1, overrides.timeoutSeconds() == null ? model.getTimeoutSeconds() : overrides.timeoutSeconds());
        int timeoutMillis = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, timeoutSeconds * 1000L));
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Connection", "close");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", providerRequest.accept());
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        byte[] payload = mapper.writeValueAsString(providerRequest.payload()).getBytes(StandardCharsets.UTF_8);
        // Buffer the request so HttpURLConnection retains 401/407 response bodies.
        // Request streaming mode can discard authentication diagnostics.
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload);
        }
        return connection;
    }

    private String readResponseBody(HttpURLConnection connection, int status) throws IOException {
        java.io.InputStream stream;
        if (status >= 400) {
            try {
                stream = connection.getInputStream();
            } catch (IOException ignored) {
                stream = connection.getErrorStream();
            }
        } else {
            stream = connection.getInputStream();
        }
        if (stream == null) return "";
        java.io.InputStream responseStream = stream;
        try (responseStream) {
            return new String(responseStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Invocation parseStream(HttpURLConnection response, List<AiGatewayMessage> messages,
                                   Consumer<String> onDelta, long startedAt, String requestUrl, String adapter, String apiKey) throws IOException {
        int status = response.getResponseCode();
        if (status < 200 || status >= 300) {
            throw providerError(status, readResponseBody(response, status), requestUrl, startedAt, apiKey);
        }
        StringBuilder content = new StringBuilder();
        JsonNode usage = null;
        StringBuilder eventData = new StringBuilder();
        StringBuilder pending = new StringBuilder();
        String eventName = "message";
        String lastRawDelta = null;
        boolean cumulativeMode = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    StreamChunkResult result = consumeStreamEvent(eventName, eventData.toString(), content, pending, lastRawDelta, cumulativeMode,
                            onDelta, requestUrl);
                    if (result.usage() != null) usage = result.usage();
                    lastRawDelta = result.lastRawDelta();
                    cumulativeMode = result.cumulativeMode();
                    eventData.setLength(0);
                    eventName = "message";
                    continue;
                }
                if (line.startsWith(":") || line.startsWith("id:") || line.startsWith("retry:")) continue;
                if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim().toLowerCase(Locale.ROOT);
                } else if (line.startsWith("data:")) {
                    if (eventData.length() > 0) eventData.append('\n');
                    String value = line.substring(5);
                    eventData.append(value.startsWith(" ") ? value.substring(1) : value);
                }
            }
            if (eventData.length() > 0) {
                StreamChunkResult result = consumeStreamEvent(eventName, eventData.toString(), content, pending, lastRawDelta, cumulativeMode,
                        onDelta, requestUrl);
                if (result.usage() != null) usage = result.usage();
            }
        }
        validateGeneratedContent(content.toString(), requestUrl);
        if (content.isEmpty()) throw new AiGatewayException("PROVIDER_ERROR", "模型未返回有效内容", 200, requestUrl, elapsed(startedAt));
        long inputTokens = usageValue(usage, "prompt_tokens", "input_tokens");
        long outputTokens = usageValue(usage, "completion_tokens", "output_tokens");
        long totalTokens = usageValue(usage, "total_tokens", "totalTokens");
        if (totalTokens > 0) {
            if (inputTokens <= 0 && outputTokens <= 0) inputTokens = estimateTokens(messages.stream().map(AiGatewayMessage::content).toList());
            if (inputTokens <= 0) inputTokens = Math.max(0, totalTokens - outputTokens);
            if (outputTokens <= 0 || inputTokens + outputTokens != totalTokens) outputTokens = Math.max(0, totalTokens - inputTokens);
        } else {
            if (inputTokens <= 0) inputTokens = estimateTokens(messages.stream().map(AiGatewayMessage::content).toList());
            if (outputTokens <= 0) outputTokens = estimateTokens(List.of(content.toString()));
        }
        return new Invocation(new AiGatewayResult(content.toString(), inputTokens, outputTokens, elapsed(startedAt)), requestUrl, status, adapter);
    }

    private StreamChunkResult consumeStreamEvent(String eventName, String data, StringBuilder content, StringBuilder pending,
                                                  String lastRawDelta, boolean cumulativeMode, Consumer<String> onDelta,
                                                  String requestUrl) {
        String payload = data == null ? "" : data.trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) return new StreamChunkResult(null, lastRawDelta, cumulativeMode);
        if (!(eventName == null || eventName.isBlank() || "message".equals(eventName) || "data".equals(eventName) || "token".equals(eventName) || "delta".equals(eventName))) {
            return new StreamChunkResult(null, lastRawDelta, cumulativeMode);
        }
        JsonNode chunk;
        try {
            chunk = mapper.readTree(payload);
        } catch (IOException exception) {
            log.warn("AI_STREAM_PROTOCOL_ERROR requestUrl={} reason=INVALID_JSON payloadLength={}", requestUrl, payload.length());
            throw new AiGatewayException("PROVIDER_PROTOCOL_ERROR", "模型流式响应格式异常");
        }
        JsonNode usage = chunk.path("usage");
        JsonNode choices = chunk.path("choices");
        if (!choices.isArray() || choices.isEmpty() || !choices.path(0).isObject()) {
            return new StreamChunkResult(usage, lastRawDelta, cumulativeMode);
        }
        JsonNode delta = choices.path(0).path("delta");
        if (!delta.isObject()) return new StreamChunkResult(usage, lastRawDelta, cumulativeMode);
        String rawDelta = extractTextDelta(delta.path("content"));
        if (rawDelta == null || rawDelta.isEmpty()) return new StreamChunkResult(usage, lastRawDelta, cumulativeMode);
        log.debug("[RAW_PROVIDER_DELTA] requestUrl={} length={} sample={}", requestUrl, rawDelta.length(), sample(rawDelta));

        // Some OpenAI-compatible gateways incorrectly send the whole cumulative
        // answer in delta.content. Convert it to a suffix before forwarding.
        String token = rawDelta;
        if (content.length() > 0 && rawDelta.startsWith(content.toString()) && rawDelta.length() > content.length()) {
            cumulativeMode = true;
            token = rawDelta.substring(content.length());
        }
        if (cumulativeMode && content.length() > 0 && rawDelta.startsWith(content.toString())) {
            token = rawDelta.substring(content.length());
        } else if (cumulativeMode && content.length() > 0 && content.toString().startsWith(rawDelta)) {
            token = "";
        }
        if (token.isEmpty()) return new StreamChunkResult(usage, rawDelta, cumulativeMode);
        if (isTransportArtifactToken(token, content.length() == 0)) {
            log.warn("AI_STREAM_CONTENT_ANOMALY requestUrl={} reason=TRANSPORT_ARTIFACT length={} sample={}",
                    requestUrl, token.length(), sample(token));
        }
        content.append(token);
        validateGeneratedContent(content.toString(), requestUrl);
        log.debug("[BACKEND_PARSED_TOKEN] requestUrl={} length={} sample={}", requestUrl, token.length(), sample(token));
        // Forward every provider delta immediately. The browser owns only a small
        // character queue for smooth rendering; it must never wait for a full answer.
        onDelta.accept(token);
        return new StreamChunkResult(usage, rawDelta, cumulativeMode);
    }

    /** Reject model-side protocol leakage and obvious decoding/repetition loops. */
    private void validateGeneratedContent(String content, String requestUrl) {
        String value = content == null ? "" : content.trim();
        if (value.isEmpty()) return;
        if (PROTOCOL_PREFIX.matcher(value).find() || containsProtocolLeak(value)) {
            log.warn("AI_STREAM_CONTENT_ANOMALY requestUrl={} reason=PROTOCOL_LEAK sample={}", requestUrl, sample(value));
            throw new AiGatewayException("PROVIDER_PROTOCOL_ERROR", "模型返回内容包含内部协议字段", 200, requestUrl, 0);
        }
        if (containsRepetitionLoop(value)) {
            log.warn("AI_STREAM_CONTENT_ANOMALY requestUrl={} reason=REPETITION_LOOP sample={}", requestUrl, sample(value));
            throw new AiGatewayException("PROVIDER_CONTENT_ANOMALY", "模型返回内容异常重复", 200, requestUrl, 0);
        }
    }

    private boolean containsProtocolLeak(String content) {
        int matches = 0;
        for (String line : content.split("\\R")) {
            if (PROTOCOL_LINE.matcher(line).matches()) matches++;
        }
        return matches >= 2;
    }

    private boolean containsRepetitionLoop(String content) {
        // Repetition loops normally begin in the latest provider suffix. Keep
        // validation bounded so a long, otherwise healthy answer is cheap to
        // inspect on every streamed delta.
        String window = content.length() > 512 ? content.substring(content.length() - 512) : content;
        Matcher word = REPEATED_WORD.matcher(window.replaceAll("\\s+", " "));
        if (word.find()) return true;
        Matcher fragment = REPEATED_FRAGMENT.matcher(window);
        if (fragment.find()) {
            String repeated = fragment.group(1) == null ? "" : fragment.group(1).trim();
            return repeated.length() >= 3 && repeated.matches(".*[\\p{L}\\p{N}].*");
        }
        return false;
    }

    private String extractTextDelta(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) return null;
        if (contentNode.isTextual()) return contentNode.asText();
        if (!contentNode.isArray()) return null;
        StringBuilder text = new StringBuilder();
        for (JsonNode item : contentNode) {
            if (!item.isObject()) continue;
            String value = item.path("text").asText("");
            if (!value.isEmpty()) text.append(value);
        }
        return text.toString();
    }

    private boolean isTransportArtifactToken(String token, boolean firstToken) {
        String value = token == null ? "" : token.trim();
        if (value.isEmpty()) return false;
        if (value.matches("(?is)^(?:assistant|user|system|output|data|event|role|content|id|index|seq|usage)\\s*[:=]?\\s*$")) return true;
        // Only reject known short protocol artefacts as a complete first token.
        return firstToken && value.matches("(?i)^(?:os\\d+|\\d{1,3})$");
    }

    private String sample(String value) {
        String normalized = value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
        if (normalized.length() > 48) normalized = normalized.substring(0, 48) + "...";
        return normalized;
    }

    private record StreamChunkResult(JsonNode usage, String lastRawDelta, boolean cumulativeMode) { }
    private AiGatewayException providerError(int status, String responseBody, String requestUrl, long startedAt, String apiKey) {
        String providerMessage = providerMessage(responseBody, apiKey);
        String normalized = providerMessage.toLowerCase(Locale.ROOT);
        String code;
        String message;
        if (status == 401 || status == 403) {
            code = "AUTHENTICATION_ERROR";
            message = "API Key 无效或已失效";
        } else if ((status == 400 || status == 404) && isModelNotFoundMessage(normalized)) {
            code = "MODEL_NOT_FOUND";
            message = "模型 ID 不存在";
        } else if (status == 404) {
            code = "INVALID_ENDPOINT";
            message = "模型接口地址错误";
        } else if (status == 408 || status == 504) {
            code = "CONNECTION_TIMEOUT";
            message = "请求模型服务超时";
        } else if (status == 429 && (normalized.contains("insufficient") || normalized.contains("balance") || normalized.contains("quota") || normalized.contains("余额") || normalized.contains("额度不足"))) {
            code = "INSUFFICIENT_BALANCE";
            message = "账户额度不足";
        } else if (status == 429) {
            code = "RATE_LIMIT";
            message = "请求频率受限";
        } else if (status >= 500) {
            code = "PROVIDER_5XX";
            message = providerMessage.isBlank() ? "模型服务暂时不可用" : providerMessage;
        } else {
            code = "PROVIDER_ERROR";
            message = providerMessage.isBlank() ? "模型服务返回异常状态" : providerMessage;
        }
        return new AiGatewayException(code, message, status, requestUrl, elapsed(startedAt), providerMessage);
    }

    private boolean isModelNotFoundMessage(String message) {
        return (message.contains("model") || message.contains("模型"))
                && (message.contains("does not exist") || message.contains("not found") || message.contains("不存在"));
    }

    private AiGatewayException networkError(IOException exception, String requestUrl, long latencyMs) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String reason = cause instanceof UnknownHostException ? "无法解析模型服务地址"
                : cause instanceof ConnectException ? "后端无法连接模型服务"
                : "后端无法访问模型服务";
        return new AiGatewayException("NETWORK_ERROR", reason, 0, requestUrl, latencyMs);
    }

    private AiGatewayException withRequestDetails(AiGatewayException exception, String requestUrl, long latencyMs) {
        if (exception.getRequestUrl() != null && exception.getLatencyMs() > 0) return exception;
        return new AiGatewayException(exception.getCode(), exception.getMessage(), exception.getHttpStatus(), requestUrl,
                exception.getLatencyMs() > 0 ? exception.getLatencyMs() : latencyMs, exception.getProviderMessage());
    }

    private String providerMessage(String responseBody, String apiKey) {
        if (responseBody == null || responseBody.isBlank()) return "";
        try {
            JsonNode root = mapper.readTree(responseBody);
            String message = root.path("error").path("message").asText("");
            if (message.isBlank()) message = root.path("message").asText("");
            if (message.isBlank()) message = root.path("error").asText("");
            return sanitize(message, apiKey);
        } catch (IOException ignored) {
            return sanitize(responseBody, apiKey);
        }
    }

    private static String sanitize(String message) {
        return sanitize(message, null);
    }

    private static String sanitize(String message, String apiKey) {
        if (message == null) return "";
        String trimmed = message.replaceAll("[\\r\\n]+", " ").trim();
        if (apiKey != null && !apiKey.isBlank()) trimmed = trimmed.replace(apiKey, "[REDACTED]");
        trimmed = trimmed.replaceAll("(?i)(bearer\\s+)[^\\s,;]+", "$1[REDACTED]");
        trimmed = trimmed.replaceAll("(?i)(api[_-]?key\\s*[=:]\\s*)[^\\s,;]+", "$1[REDACTED]");
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private long usageValue(JsonNode usage, String primary, String fallback) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) return 0;
        return usage.path(primary).asLong(usage.path(fallback).asLong(0));
    }

    public long estimateTokens(List<String> content) {
        long characters = content.stream().filter(java.util.Objects::nonNull).mapToLong(String::length).sum();
        return Math.max(1, (characters + 3) / 4);
    }

    private long elapsed(long startedAt) { return Math.max(1, (System.nanoTime() - startedAt) / 1_000_000); }

    private record Invocation(AiGatewayResult result, String requestUrl, int httpStatus, String adapter) { }
}

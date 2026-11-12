package com.agent.ai;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single runtime routing policy for chat/reasoning models. Candidates are already
 * ordered by the model service; this class owns retries, health transitions and
 * the attempted-model trail used by callers and diagnostics.
 */
@Service
public class AiModelRouter {
    private static final Logger log = LoggerFactory.getLogger(AiModelRouter.class);
    private final AiGatewayService gateway;
    private final AiModelService models;

    public AiModelRouter(AiGatewayService gateway, AiModelService models) {
        this.gateway = gateway;
        this.models = models;
    }

    public RouteResult complete(List<AiModel> candidates,
                                Function<AiModel, List<AiGatewayMessage>> messages,
                                Function<AiModel, AiRequestOverrides> overrides) {
        List<Long> attempted = new ArrayList<>();
        List<RouteFailure> failures = new ArrayList<>();
        AiGatewayException last = null;
        for (AiModel model : dedupe(candidates)) {
            if (!models.runtimeAvailable(model)) continue;
            attempted.add(model.getId());
            try {
                AiGatewayResult result = retryBeforeOutput(model,
                        () -> gateway.complete(model, messages.apply(model), overrides.apply(model)), new AtomicBoolean());
                models.recordRuntimeSuccess(model, result.latencyMs());
                return new RouteResult(model, result, attempted, !attempted.isEmpty() && !attempted.getFirst().equals(model.getId()), failures, false);
            } catch (AiGatewayException exception) {
                last = exception;
                failures.add(new RouteFailure(model.getId(), model.getModelId(), exception.getCode(), exception.getMessage(), exception.getLatencyMs()));
                if (!fallbackEligible(exception.getCode())) throw new AiRoutingException(exception, attempted, failures);
                models.recordRuntimeFailure(model, exception.getCode(), exception.getLatencyMs());
            } catch (RuntimeException exception) {
                last = new AiGatewayException("PROVIDER_ERROR", "模型服务响应无效");
                failures.add(new RouteFailure(model.getId(), model.getModelId(), last.getCode(), last.getMessage(), 0));
                models.recordRuntimeFailure(model, last.getCode(), 0);
            }
        }
        if (last == null) last = new AiGatewayException("MODEL_UNAVAILABLE", "当前没有可用的 AI 模型");
        throw new AiRoutingException(last, attempted, failures);
    }

    public RouteResult stream(List<AiModel> candidates,
                              Function<AiModel, List<AiGatewayMessage>> messages,
                              Function<AiModel, AiRequestOverrides> overrides,
                              BiConsumer<AiModel, String> onDelta) {
        List<Long> attempted = new ArrayList<>();
        List<RouteFailure> failures = new ArrayList<>();
        AiGatewayException last = null;
        for (AiModel model : dedupe(candidates)) {
            if (!models.runtimeAvailable(model)) continue;
            attempted.add(model.getId());
            java.util.concurrent.atomic.AtomicBoolean emitted = new java.util.concurrent.atomic.AtomicBoolean();
            try {
                AiGatewayResult result = retryBeforeOutput(model, () -> gateway.stream(model, messages.apply(model), token -> {
                    emitted.set(true);
                    onDelta.accept(model, token);
                }, overrides.apply(model)), emitted);
                models.recordRuntimeSuccess(model, result.latencyMs());
                return new RouteResult(model, result, attempted, attempted.size() > 1, failures, false);
            } catch (AiGatewayException exception) {
                if (emitted.get()) throw new AiRoutingException(new AiGatewayException("STREAM_INTERRUPTED", "AI 流式响应在首 Token 后中断"), attempted, failures);
                last = exception;
                failures.add(new RouteFailure(model.getId(), model.getModelId(), exception.getCode(), exception.getMessage(), exception.getLatencyMs()));
                if (!fallbackEligible(exception.getCode())) throw new AiRoutingException(exception, attempted, failures);
                models.recordRuntimeFailure(model, exception.getCode(), exception.getLatencyMs());
            } catch (RuntimeException exception) {
                if (emitted.get()) throw exception;
                last = new AiGatewayException("PROVIDER_ERROR", "模型服务响应无效");
                failures.add(new RouteFailure(model.getId(), model.getModelId(), last.getCode(), last.getMessage(), 0));
                models.recordRuntimeFailure(model, last.getCode(), 0);
            }
        }
        if (last == null) last = new AiGatewayException("MODEL_UNAVAILABLE", "当前没有可用的 AI 模型");
        throw new AiRoutingException(last, attempted, failures);
    }

    private AiGatewayResult retryBeforeOutput(AiModel model, Supplier<AiGatewayResult> call, AtomicBoolean emitted) {
        try {
            return call.get();
        } catch (AiGatewayException failure) {
            // Once any text is visible, another generation could concatenate a
            // different answer. Retry only a short transient failure, once.
            if (emitted.get() || failure.getLatencyMs() > 45_000 || !transientFailure(failure)) throw failure;
            log.info("AI_MODEL_RETRY modelId={} code={} retry=1", model.getId(), failure.getCode());
            try {
                Thread.sleep(400);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AiGatewayException("STREAM_INTERRUPTED", "AI 请求已中断");
            }
            return call.get();
        }
    }

    private boolean transientFailure(AiGatewayException failure) {
        return switch (failure.getCode()) {
            case "NETWORK_ERROR", "CONNECTION_TIMEOUT", "MODEL_TIMEOUT", "PROVIDER_5XX" -> true;
            case "PROVIDER_ERROR" -> failure.getHttpStatus() == 0;
            default -> false;
        };
    }

    private List<AiModel> dedupe(List<AiModel> candidates) {
        Set<Long> ids = new LinkedHashSet<>();
        List<AiModel> result = new ArrayList<>();
        if (candidates == null) return result;
        for (AiModel candidate : candidates) {
            if (candidate != null && candidate.getId() != null && ids.add(candidate.getId())) result.add(candidate);
        }
        return result;
    }

    private boolean fallbackEligible(String code) {
        if (code == null || code.isBlank()) return true;
        return switch (code.toUpperCase(java.util.Locale.ROOT)) {
            case "INVALID_REQUEST", "CONVERSATION_NOT_FOUND", "PROJECT_ACCESS_DENIED", "PROMPT_NOT_FOUND",
                    "PROMPT_UNAVAILABLE", "PROMPT_DISABLED", "PROMPT_EMPTY", "MODEL_TYPE_INCOMPATIBLE",
                    "MODEL_NOT_AVAILABLE", "INSUFFICIENT_BALANCE", "QUOTA_EXCEEDED", "STREAM_INTERRUPTED" -> false;
            default -> true;
        };
    }

    public record RouteResult(AiModel model, AiGatewayResult result, List<Long> attemptedModelIds,
                              boolean fallbackUsed, List<RouteFailure> failures, boolean interrupted) { }

    public record RouteFailure(Long modelId, String model, String code, String message, long latencyMs) { }

    public static class AiRoutingException extends AiGatewayException {
        private final List<Long> attemptedModelIds;
        private final List<RouteFailure> failures;

        public AiRoutingException(AiGatewayException cause, List<Long> attemptedModelIds, List<RouteFailure> failures) {
            super(cause.getCode(), cause.getMessage(), cause.getHttpStatus(), cause.getRequestUrl(), cause.getLatencyMs(), cause.getProviderMessage());
            this.attemptedModelIds = List.copyOf(attemptedModelIds);
            this.failures = List.copyOf(failures);
        }

        public List<Long> getAttemptedModelIds() { return attemptedModelIds; }
        public List<RouteFailure> getFailures() { return failures; }
    }
}

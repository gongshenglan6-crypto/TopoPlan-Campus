package com.agent.common.api;

import com.agent.ai.AiGatewayException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(error(exception.getCode(), exception.getMessage(), exception.getDetails(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        String traceId = UUID.randomUUID().toString();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
            if (isAiTopologyRequest(request)) {
                Object rejected = fieldError.getRejectedValue();
                log.warn("AI_TOPOLOGY_REQUEST_VALIDATION_FAILED traceId={} path={} field={} rejectedValueType={} validationMessage={}",
                        traceId, request.getRequestURI(), fieldError.getField(),
                        rejected == null ? "null" : rejected.getClass().getSimpleName(), fieldError.getDefaultMessage());
            }
        }
        return ResponseEntity.unprocessableEntity()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError("VALIDATION_ERROR", "请求参数校验失败", details, traceId));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleMissingRoute(NoResourceFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(error("NOT_FOUND", "请求的资源不存在", Map.of(), request));
    }

    @ExceptionHandler(AiGatewayException.class)
    ResponseEntity<ApiError> handleAiGateway(AiGatewayException exception, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.warn("AI request failed requestId={} path={} code={} providerStatus={} latencyMs={} url={} message={}",
                requestId, request.getRequestURI(), exception.getCode(), exception.getHttpStatus(), exception.getLatencyMs(),
                exception.getRequestUrl(), exception.getMessage());
        HttpStatus status = switch (exception.getCode()) {
            case "PROVIDER_TIMEOUT", "PROVIDER_UNAVAILABLE", "MODEL_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "PROVIDER_RATE_LIMIT" -> HttpStatus.TOO_MANY_REQUESTS;
            case "PROVIDER_AUTH_FAILED" -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError(exception.getCode(), exception.getMessage(), Map.of(), requestId));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.error("Unhandled API error requestId={} path={} message={}", requestId, request.getRequestURI(), exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError("INTERNAL_ERROR", "请求处理失败，请稍后重试", Map.of(), requestId));
    }

    private ApiError error(String code, String message, Map<String, String> details, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        return new ApiError(code, message, details, requestId);
    }

    private boolean isAiTopologyRequest(HttpServletRequest request) {
        String path = request == null ? "" : request.getRequestURI();
        return (path.contains("/api/projects/") && path.endsWith("/ai/plan"))
                || ("/api/chat/stream".equals(path) && "POST".equalsIgnoreCase(request.getMethod()));
    }
}

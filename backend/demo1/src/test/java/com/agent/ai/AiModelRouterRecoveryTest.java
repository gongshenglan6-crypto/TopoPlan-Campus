package com.agent.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiModelRouterRecoveryTest {
    AiGatewayService gateway = mock(AiGatewayService.class);
    AiModelService models = mock(AiModelService.class);
    AiModel model = mock(AiModel.class);
    AiModelRouter router = new AiModelRouter(gateway, models);
    AiGatewayResult answer = new AiGatewayResult("正常回复", 5, 5, 10);
    List<AiGatewayMessage> messages = List.of(new AiGatewayMessage("user", "你好"));

    @BeforeEach
    void setup() {
        when(model.getId()).thenReturn(10L);
        when(model.getModelId()).thenReturn("test-model");
        when(models.runtimeAvailable(model)).thenReturn(true);
    }

    private AiModelRouter.RouteResult stream(Consumer<String> text) {
        return router.stream(List.of(model), ignored -> messages, ignored -> AiRequestOverrides.NONE,
                (ignored, token) -> text.accept(token));
    }

    @Test
    void shortFailureBeforeFirstTokenRecoversWithoutMarkingChannelUnhealthy() {
        when(gateway.stream(eq(model), anyList(), any(), any()))
                .thenThrow(new AiGatewayException("PROVIDER_ERROR", "模型服务响应无效", 0, null, 19000))
                .thenReturn(answer);
        var result = stream(ignored -> {});
        assertEquals(answer, result.result());
        assertFalse(result.fallbackUsed());
        verify(gateway, times(2)).stream(eq(model), anyList(), any(), any());
        verify(models).recordRuntimeSuccess(model, 10);
        verify(models, never()).recordRuntimeFailure(any(), any(), anyLong());
    }

    @Test
    void repeatedFailureStopsAfterTwoCallsAndRecordsOneFailedRequest() {
        when(gateway.stream(eq(model), anyList(), any(), any()))
                .thenThrow(new AiGatewayException("NETWORK_ERROR", "连接失败"));
        assertThrows(AiModelRouter.AiRoutingException.class, () -> stream(ignored -> {}));
        verify(gateway, times(2)).stream(eq(model), anyList(), any(), any());
        verify(models, times(1)).recordRuntimeFailure(eq(model), eq("NETWORK_ERROR"), anyLong());
    }

    @Test
    void partialAnswerIsNeverRegeneratedOrConcatenated() {
        var received = new StringBuilder();
        doAnswer(invocation -> {
            Consumer<String> callback = invocation.getArgument(2);
            callback.accept("已收到的文本");
            throw new AiGatewayException("NETWORK_ERROR", "连接中断");
        }).when(gateway).stream(eq(model), anyList(), any(), any());
        var failure = assertThrows(AiModelRouter.AiRoutingException.class, () -> stream(received::append));
        assertEquals("STREAM_INTERRUPTED", failure.getCode());
        assertEquals("已收到的文本", received.toString());
        verify(gateway, times(1)).stream(eq(model), anyList(), any(), any());
    }

    @Test
    void invalidCredentialsAreNotRetried() {
        when(gateway.stream(eq(model), anyList(), any(), any()))
                .thenThrow(new AiGatewayException("AUTHENTICATION_ERROR", "密钥无效", 401, null, 1));
        assertThrows(AiModelRouter.AiRoutingException.class, () -> stream(ignored -> {}));
        verify(gateway, times(1)).stream(eq(model), anyList(), any(), any());
    }

    @Test
    void contentValidationFailureIsNotHiddenByRetrying() {
        when(gateway.stream(eq(model), anyList(), any(), any()))
                .thenThrow(new AiGatewayException("PROVIDER_CONTENT_ANOMALY", "内容异常"));
        assertThrows(AiModelRouter.AiRoutingException.class, () -> stream(ignored -> {}));
        verify(gateway, times(1)).stream(eq(model), anyList(), any(), any());
    }

    @Test
    void longTimeoutDoesNotStartAnotherLongRequest() {
        when(gateway.stream(eq(model), anyList(), any(), any()))
                .thenThrow(new AiGatewayException("CONNECTION_TIMEOUT", "超时", 0, null, 60000));
        assertThrows(AiModelRouter.AiRoutingException.class, () -> stream(ignored -> {}));
        verify(gateway, times(1)).stream(eq(model), anyList(), any(), any());
    }

    @Test
    void bufferedCompletionAlsoRecoversFromShortNetworkFailure() {
        when(gateway.complete(eq(model), anyList(), any()))
                .thenThrow(new AiGatewayException("NETWORK_ERROR", "连接失败"))
                .thenReturn(answer);
        var result = router.complete(List.of(model), ignored -> messages, ignored -> AiRequestOverrides.NONE);
        assertEquals(answer, result.result());
        verify(gateway, times(2)).complete(eq(model), anyList(), any());
    }
}

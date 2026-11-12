package com.agent.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiGatewayContentValidationTest {
    private final AiGatewayService gateway = new AiGatewayService(null, null, new ObjectMapper());
    private final Method validator = validator();

    @Test
    void acceptsNormalNetworkAnswer() {
        assertDoesNotThrow(() -> validate("OSPF（Open Shortest Path First，开放式最短路径优先）是一种链路状态型动态路由协议。"));
    }

    @Test
    void rejectsRepeatedProviderLoop() {
        assertThrows(AiGatewayException.class,
                () -> validate("OSPF（Open Shortest Path Path Path Path"));
    }

    @Test
    void rejectsProtocolPrefixLeak() {
        assertThrows(AiGatewayException.class,
                () -> validate("assistant 内部输出不应展示"));
    }

    private void validate(String content) throws Throwable {
        try {
            validator.invoke(gateway, content, "test://provider");
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private Method validator() {
        try {
            Method method = AiGatewayService.class.getDeclaredMethod("validateGeneratedContent", String.class, String.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}

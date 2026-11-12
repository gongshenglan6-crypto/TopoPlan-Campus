package com.agent.chat;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class ChatServiceOutputValidationTest {
    private final ChatService service = new ChatService(null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null);

    private String anomaly(String answer) throws Exception {
        Method method = ChatService.class.getDeclaredMethod("networkQaAnomaly", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, "请解释网络设计", answer);
    }

    @Test
    void acceptsMarkdownTablesAndSeparators() throws Exception {
        assertNull(anomaly("网络方案\n------------\n| 楼层 | VLAN |\n| ---------- | ---------- |\n| 三层 | 30 |"));
    }

    @Test
    void acceptsNetworkAddresses() throws Exception {
        assertNull(anomaly("默认路由 0.0.0.0/0，IPv6 地址 2001:db8:0000:0000:0000:0000:0000:0001。"));
    }

    @Test
    void rejectsRepeatedProse() throws Exception {
        assertEquals("SEVERE_REPETITION", anomaly("办公网络办公网络办公网络"));
    }

    @Test
    void stillDetectsProseLoopAfterMarkdownRuler() throws Exception {
        assertEquals("SEVERE_REPETITION", anomaly("------------\n办公网络办公网络办公网络"));
    }
}

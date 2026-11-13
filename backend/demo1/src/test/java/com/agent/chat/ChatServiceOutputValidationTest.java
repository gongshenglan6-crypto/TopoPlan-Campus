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

    @Test
    void topologyPlanningGetsEnoughTimeForTheFirstStreamToken() {
        AiOrchestrator.Decision planning = new AiOrchestrator.Decision(
                "TOPOLOGY_GENERATION", 0.92, "TOPOLOGY_GENERATION", "规划三层办公楼网络",
                "{}", 150, 0, true, true, true, true);

        assertEquals(60, ChatService.effectiveTimeoutSeconds(planning, 15, 15));
        assertEquals(90, ChatService.effectiveTimeoutSeconds(planning, 90, 15));
    }

    @Test
    void ordinaryChatKeepsItsConfiguredTimeout() {
        AiOrchestrator.Decision chat = new AiOrchestrator.Decision(
                "GENERAL_CHAT", 0.99, "TOPOLOGY_QA", "你好",
                "{}", 0, 0, false, false, false, false);

        assertEquals(15, ChatService.effectiveTimeoutSeconds(chat, 15, 60));
        assertEquals(45, ChatService.effectiveTimeoutSeconds(chat, null, 45));
    }
}

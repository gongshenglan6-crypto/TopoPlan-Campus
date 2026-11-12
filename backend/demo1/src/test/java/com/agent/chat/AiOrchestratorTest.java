package com.agent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiOrchestratorTest {
    private final AiOrchestrator orchestrator = new AiOrchestrator(new ObjectMapper());

    @Test
    void routesSimpleOspfQuestionToCleanNetworkQaWithoutProject() {
        AiOrchestrator.Decision decision = orchestrator.resolve("OSPF的作用是什么", true);
        assertEquals("NETWORK_QA", decision.intent());
        assertEquals("NETWORK_QA", decision.promptTaskType());
        assertFalse(decision.requiresProjectContext());
        assertTrue(decision.requiresKnowledgeBase());
    }

    @Test
    void keepsTopologySpecificQuestionOnProjectPath() {
        AiOrchestrator.Decision decision = orchestrator.resolve("为什么当前拓扑需要两台核心交换机", true);
        assertEquals("DESIGN_EXPLANATION", decision.intent());
        assertTrue(decision.requiresProjectContext());
    }

    @Test
    void doesNotUseRagForGreeting() {
        AiOrchestrator.Decision decision = orchestrator.resolve("你好", false);
        assertFalse(decision.requiresKnowledgeBase());
    }

    @Test
    void routesOfficeBuildingDesignRequestToTopologyPlanning() {
        AiOrchestrator.Decision decision = orchestrator.resolve(
                "请帮我设计一个三层办公楼网络，一楼有前台和会议室，二楼是办公室，三楼有财务和服务器机房，约150台终端，需要无线覆盖、内外网隔离和高可用。",
                true);
        assertEquals("NETWORK_PLANNING", decision.intent());
        assertEquals("NETWORK_PLANNING", decision.promptTaskType());
        assertTrue(decision.planning());
        assertTrue(decision.structuredRequirements().contains("\"userCount\":150"));
    }
}

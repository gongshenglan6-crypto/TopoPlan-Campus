package com.agent.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopologyCheckServiceTest {
    private final TopologyCheckService service = new TopologyCheckService(null, null, new ObjectMapper());
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void reportsOrphanDevicesAndDuplicateIpsFromTheDocument() throws Exception {
        TopologyCheckResponse response = service.evaluate(
                mapper.readTree("[{\"id\":\"router-1\",\"name\":\"Router01\",\"ip\":\"10.0.0.1\"},{\"id\":\"core-1\",\"name\":\"Core01\",\"ip\":\"10.0.0.1\"}]"),
                mapper.readTree("[]"));

        assertEquals(3, response.summary().high());
        assertTrue(response.issues().stream().anyMatch(issue -> "ORPHAN_NODE".equals(issue.ruleCode())));
        assertTrue(response.issues().stream().anyMatch(issue -> "DUPLICATE_IP".equals(issue.ruleCode())));
    }

    @Test
    void acceptsMultipleMembersInOneAggregationGroup() throws Exception {
        TopologyCheckResponse response = service.evaluate(
                mapper.readTree("[{\"id\":\"core-1\",\"name\":\"Core01\"},{\"id\":\"core-2\",\"name\":\"Core02\"}]"),
                mapper.readTree("["
                        + "{\"id\":\"e1\",\"source\":\"core-1\",\"target\":\"core-2\",\"sourcePort\":\"GE0/0/1\",\"targetPort\":\"GE0/0/1\",\"aggregationId\":\"lag-1\",\"speed\":\"10G\"},"
                        + "{\"id\":\"e2\",\"source\":\"core-1\",\"target\":\"core-2\",\"sourcePort\":\"GE0/0/2\",\"targetPort\":\"GE0/0/2\",\"aggregationId\":\"lag-1\",\"speed\":\"10G\"}"
                        + "]"));

        assertTrue(response.issues().stream().noneMatch(issue -> "DUPLICATE_EDGE".equals(issue.ruleCode())));
        assertTrue(response.issues().stream().noneMatch(issue -> "PORT_REUSED".equals(issue.ruleCode())));
        assertTrue(response.issues().stream().noneMatch(issue -> "AGGREGATION_SINGLE_MEMBER".equals(issue.ruleCode())));
    }

    @Test
    void reportsReuseOfOnePhysicalPortOutsideAggregation() throws Exception {
        TopologyCheckResponse response = service.evaluate(
                mapper.readTree("[{\"id\":\"core-1\",\"name\":\"Core01\"},{\"id\":\"access-1\",\"name\":\"Access01\"},{\"id\":\"access-2\",\"name\":\"Access02\"}]"),
                mapper.readTree("["
                        + "{\"id\":\"e1\",\"source\":\"core-1\",\"target\":\"access-1\",\"sourcePort\":\"GE0/0/1\",\"targetPort\":\"GE0/0/1\"},"
                        + "{\"id\":\"e2\",\"source\":\"core-1\",\"target\":\"access-2\",\"sourcePort\":\"GE0/0/1\",\"targetPort\":\"GE0/0/1\"}"
                        + "]"));

        assertEquals(1, response.issues().stream().filter(issue -> "PORT_REUSED".equals(issue.ruleCode())).count());
    }

    @Test
    void reportsHighAvailabilityAndServerZoneRisks() throws Exception {
        TopologyCheckResponse response = service.evaluate(
                mapper.readTree("["
                        + "{\"id\":\"core-a\",\"type\":\"core-switch\",\"name\":\"核心交换机A\"},"
                        + "{\"id\":\"core-b\",\"type\":\"core-switch\",\"name\":\"核心交换机B\"},"
                        + "{\"id\":\"ac-1\",\"type\":\"ac-controller\",\"name\":\"AC-01\"},"
                        + "{\"id\":\"server-1\",\"type\":\"server\",\"name\":\"应用服务器\"}"
                        + "]"),
                mapper.readTree("["
                        + "{\"id\":\"e1\",\"source\":\"core-a\",\"target\":\"core-b\",\"sourcePort\":\"GE0/0/1\",\"targetPort\":\"GE0/0/1\"},"
                        + "{\"id\":\"e2\",\"source\":\"ac-1\",\"target\":\"core-a\",\"sourcePort\":\"GE0/0/1\",\"targetPort\":\"GE0/0/2\"},"
                        + "{\"id\":\"e3\",\"source\":\"server-1\",\"target\":\"core-a\",\"sourcePort\":\"GE0/0/1\",\"targetPort\":\"GE0/0/3\"}"
                        + "]"));

        assertTrue(response.issues().stream().anyMatch(issue -> "DUAL_CORE_SINGLE_AC".equals(issue.ruleCode())));
        assertTrue(response.issues().stream().anyMatch(issue -> "AC_SINGLE_HOMING".equals(issue.ruleCode())));
        assertTrue(response.issues().stream().anyMatch(issue -> "CORE_PAIR_NO_REDUNDANCY".equals(issue.ruleCode())));
        assertTrue(response.issues().stream().anyMatch(issue -> "SERVER_DIRECT_CORE".equals(issue.ruleCode())));
        assertTrue(response.issues().stream().anyMatch(issue -> "SERVER_ACCESS_MISSING".equals(issue.ruleCode())));
    }

    @Test
    void acceptsDualAcServerAccessAndAggregatedCoreInterconnect() throws Exception {
        TopologyCheckResponse response = service.evaluate(
                mapper.readTree("["
                        + "{\"id\":\"core-a\",\"type\":\"core-switch\",\"name\":\"核心交换机A\"},"
                        + "{\"id\":\"core-b\",\"type\":\"core-switch\",\"name\":\"核心交换机B\"},"
                        + "{\"id\":\"ac-1\",\"type\":\"ac-controller\",\"name\":\"AC-01\"},"
                        + "{\"id\":\"ac-2\",\"type\":\"ac-controller\",\"name\":\"AC-02\"},"
                        + "{\"id\":\"server-sw\",\"type\":\"access-switch\",\"name\":\"服务器区接入交换机\"},"
                        + "{\"id\":\"server-1\",\"type\":\"server\",\"name\":\"应用服务器\"}"
                        + "]"),
                mapper.readTree("["
                        + "{\"id\":\"lag-1\",\"source\":\"core-a\",\"target\":\"core-b\",\"sourcePort\":\"GE0/0/1\",\"targetPort\":\"GE0/0/1\",\"aggregationId\":\"core-lag\",\"speed\":\"10G\"},"
                        + "{\"id\":\"lag-2\",\"source\":\"core-a\",\"target\":\"core-b\",\"sourcePort\":\"GE0/0/2\",\"targetPort\":\"GE0/0/2\",\"aggregationId\":\"core-lag\",\"speed\":\"10G\"},"
                        + "{\"id\":\"ac1-a\",\"source\":\"ac-1\",\"target\":\"core-a\",\"sourcePort\":\"GE0/0/1\",\"targetPort\":\"GE0/0/3\"},"
                        + "{\"id\":\"ac1-b\",\"source\":\"ac-1\",\"target\":\"core-b\",\"sourcePort\":\"GE0/0/2\",\"targetPort\":\"GE0/0/3\"},"
                        + "{\"id\":\"ac2-a\",\"source\":\"ac-2\",\"target\":\"core-a\",\"sourcePort\":\"GE0/0/1\",\"targetPort\":\"GE0/0/4\"},"
                        + "{\"id\":\"ac2-b\",\"source\":\"ac-2\",\"target\":\"core-b\",\"sourcePort\":\"GE0/0/2\",\"targetPort\":\"GE0/0/4\"},"
                        + "{\"id\":\"srv-a\",\"source\":\"server-sw\",\"target\":\"core-a\",\"sourcePort\":\"GE0/0/1\",\"targetPort\":\"GE0/0/5\"},"
                        + "{\"id\":\"srv-b\",\"source\":\"server-sw\",\"target\":\"core-b\",\"sourcePort\":\"GE0/0/2\",\"targetPort\":\"GE0/0/5\"},"
                        + "{\"id\":\"srv-node\",\"source\":\"server-1\",\"target\":\"server-sw\",\"sourcePort\":\"GE0/0/1\",\"targetPort\":\"GE0/0/3\"}"
                        + "]"));

        assertTrue(response.issues().stream().noneMatch(issue -> Set.of("DUAL_CORE_SINGLE_AC", "AC_SINGLE_HOMING", "CORE_PAIR_NO_REDUNDANCY", "CORE_PAIR_NO_LAG", "SERVER_DIRECT_CORE", "SERVER_ACCESS_MISSING", "SERVER_ACCESS_SINGLE_UPLINK").contains(issue.ruleCode())),
                () -> response.issues().stream().map(TopologyCheckIssue::ruleCode).toList().toString());
    }
}

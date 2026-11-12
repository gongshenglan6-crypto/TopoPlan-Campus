package com.agent.topology;

import java.util.List;

public record TopologyCheckIssue(
        String id, String ruleCode, String level, String category, String title,
        String description, List<String> affectedNodeIds, List<String> affectedEdgeIds,
        String reason, String impact, String suggestion, boolean canAutoFix, String fixType) {
}

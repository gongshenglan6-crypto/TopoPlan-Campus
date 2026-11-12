package com.agent.topology;

import java.util.List;

public record TopologyCheckResponse(TopologyCheckSummary summary, List<TopologyCheckIssue> issues) {
}

package com.agent.topology;

import java.time.Instant;

public record TopologyVersionResponse(int version, Instant createdAt) {
    public static TopologyVersionResponse from(TopologyVersion version) {
        return new TopologyVersionResponse(version.getVersion(), version.getCreatedAt());
    }
}

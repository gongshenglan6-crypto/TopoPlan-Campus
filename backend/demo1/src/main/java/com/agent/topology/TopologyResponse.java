package com.agent.topology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;

public record TopologyResponse(JsonNode nodes, JsonNode edges, double zoom, int version, Instant updatedAt) {
    public static TopologyResponse empty() {
        return new TopologyResponse(JsonNodeFactory.instance.arrayNode(), JsonNodeFactory.instance.arrayNode(), 1, 0, null);
    }

    public static TopologyResponse from(TopologyDocument document, ObjectMapper mapper) {
        try {
            return new TopologyResponse(mapper.readTree(document.getNodesJson()), mapper.readTree(document.getEdgesJson()),
                    document.getZoom(), document.getVersion(), document.getUpdatedAt());
        } catch (Exception exception) {
            return empty();
        }
    }
}

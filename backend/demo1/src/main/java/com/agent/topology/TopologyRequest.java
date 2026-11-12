package com.agent.topology;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record TopologyRequest(@NotNull JsonNode nodes, @NotNull JsonNode edges,
                              // The editor supports zooming down to 20%; keep
                              // API validation aligned with that UI range.
                              @DecimalMin("0.2") @DecimalMax("2.0") double zoom) {
}

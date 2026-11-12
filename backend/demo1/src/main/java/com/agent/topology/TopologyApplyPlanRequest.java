package com.agent.topology;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TopologyApplyPlanRequest(@NotBlank String planId, @NotNull JsonNode plan) {
}

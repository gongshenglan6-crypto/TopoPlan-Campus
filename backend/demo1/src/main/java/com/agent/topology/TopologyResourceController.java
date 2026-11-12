package com.agent.topology;

import com.agent.common.api.ApiResponse;
import com.agent.common.api.ListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/topology/resources")
public class TopologyResourceController {
    private final TopologyResourceService resources;

    public TopologyResourceController(TopologyResourceService resources) {
        this.resources = resources;
    }

    @GetMapping
    public ApiResponse<ListResponse<Map<String, Object>>> list() {
        return new ApiResponse<>(ListResponse.of(resources.listPublicResources()));
    }

    @GetMapping("/ai-available")
    public ApiResponse<ListResponse<Map<String, Object>>> aiAvailable() {
        return new ApiResponse<>(ListResponse.of(resources.listAiAvailableResources()));
    }

    @GetMapping("/{resourceId}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String resourceId) {
        return new ApiResponse<>(resources.getPublicResource(resourceId));
    }
}

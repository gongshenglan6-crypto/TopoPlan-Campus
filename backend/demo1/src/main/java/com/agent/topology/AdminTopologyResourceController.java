package com.agent.topology;

import com.agent.common.api.ApiResponse;
import com.agent.common.api.ListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/topology-resources")
public class AdminTopologyResourceController {
    private final TopologyResourceService resources;

    public AdminTopologyResourceController(TopologyResourceService resources) {
        this.resources = resources;
    }

    @GetMapping
    public ApiResponse<ListResponse<Map<String, Object>>> list() {
        return new ApiResponse<>(ListResponse.of(resources.listForAdmin()));
    }

    @GetMapping("/categories")
    public ApiResponse<List<String>> categories() {
        return new ApiResponse<>(resources.categories());
    }

    @GetMapping("/{resourceId}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String resourceId) {
        return new ApiResponse<>(resources.getForAdmin(resourceId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> values) {
        return new ApiResponse<>(resources.create(values));
    }

    @PatchMapping("/{resourceId}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String resourceId, @RequestBody Map<String, Object> values) {
        return new ApiResponse<>(resources.update(resourceId, values));
    }

    @PostMapping("/icon/upload")
    public ApiResponse<Map<String, Object>> uploadIcon(@RequestParam("file") MultipartFile file) {
        return new ApiResponse<>(resources.uploadIcon(file));
    }
}

package com.agent.topology;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/uploads/topology-icons")
public class TopologyResourceIconController {
    private final TopologyResourceService resources;

    public TopologyResourceIconController(TopologyResourceService resources) {
        this.resources = resources;
    }

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> get(@PathVariable String fileName) {
        Resource icon = resources.loadIcon(fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resources.iconContentType(fileName)))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(icon);
    }
}

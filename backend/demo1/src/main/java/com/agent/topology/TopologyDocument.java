package com.agent.topology;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "topologies")
public class TopologyDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private Long projectId;
    @Column(nullable = false)
    private Long ownerId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String nodesJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String edgesJson;
    @Column(nullable = false)
    private double zoom;
    @Column(nullable = false)
    private int version;
    @Column(nullable = false)
    private Instant updatedAt;

    protected TopologyDocument() {
    }

    public TopologyDocument(Long projectId, Long ownerId, String nodesJson, String edgesJson, double zoom, int version) {
        this.projectId = projectId;
        this.ownerId = ownerId;
        this.nodesJson = nodesJson;
        this.edgesJson = edgesJson;
        this.zoom = zoom;
        this.version = version;
        this.updatedAt = Instant.now();
    }

    public void update(String nodesJson, String edgesJson, double zoom, int version) {
        this.nodesJson = nodesJson;
        this.edgesJson = edgesJson;
        this.zoom = zoom;
        this.version = version;
        this.updatedAt = Instant.now();
    }
    public Long getProjectId() { return projectId; }
    public Long getOwnerId() { return ownerId; }
    public String getNodesJson() { return nodesJson; }
    public String getEdgesJson() { return edgesJson; }
    public double getZoom() { return zoom; }
    public int getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}

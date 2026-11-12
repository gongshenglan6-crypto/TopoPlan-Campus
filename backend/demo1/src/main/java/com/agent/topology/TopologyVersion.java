package com.agent.topology;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "topology_versions")
public class TopologyVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long projectId;
    @Column(nullable = false)
    private Long ownerId;
    @Column(nullable = false)
    private int version;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String nodesJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String edgesJson;
    @Column(nullable = false)
    private double zoom;
    @Column(nullable = false)
    private Instant createdAt;

    protected TopologyVersion() {
    }

    public TopologyVersion(Long projectId, Long ownerId, int version, String nodesJson, String edgesJson, double zoom) {
        this.projectId = projectId;
        this.ownerId = ownerId;
        this.version = version;
        this.nodesJson = nodesJson;
        this.edgesJson = edgesJson;
        this.zoom = zoom;
        this.createdAt = Instant.now();
    }

    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}

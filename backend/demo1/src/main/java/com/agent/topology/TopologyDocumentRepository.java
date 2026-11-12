package com.agent.topology;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TopologyDocumentRepository extends JpaRepository<TopologyDocument, Long> {
    Optional<TopologyDocument> findByProjectIdAndOwnerId(Long projectId, Long ownerId);
    void deleteByProjectIdAndOwnerId(Long projectId, Long ownerId);
}

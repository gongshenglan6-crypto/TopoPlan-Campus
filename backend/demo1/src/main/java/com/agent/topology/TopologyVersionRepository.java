package com.agent.topology;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TopologyVersionRepository extends JpaRepository<TopologyVersion, Long> {
    List<TopologyVersion> findByProjectIdAndOwnerIdOrderByVersionDesc(Long projectId, Long ownerId);
    void deleteByProjectIdAndOwnerId(Long projectId, Long ownerId);
}

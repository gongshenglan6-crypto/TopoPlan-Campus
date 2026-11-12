package com.agent.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
    Optional<Project> findByIdAndOwnerId(Long id, Long ownerId);
    Optional<Project> findByPublicId(String publicId);
    Optional<Project> findByPublicIdAndOwnerId(String publicId, Long ownerId);
    Optional<Project> findByOwnerIdAndCreationRequestId(Long ownerId, String creationRequestId);
}

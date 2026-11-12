package com.agent.admin.config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemServiceHealthRepository extends JpaRepository<SystemServiceHealth, Long> {
    Optional<SystemServiceHealth> findByServiceKey(String serviceKey);
}

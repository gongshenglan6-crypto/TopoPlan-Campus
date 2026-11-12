package com.agent.admin.config;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AdminConfigRepository extends JpaRepository<AdminConfig, Long> {
    List<AdminConfig> findByModuleOrderByUpdatedAtDesc(String module);
    Optional<AdminConfig> findByModuleAndItemKey(String module, String itemKey);
}

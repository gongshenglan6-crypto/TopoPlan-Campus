package com.agent.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AiModelTaskBindingRepository extends JpaRepository<AiModelTaskBinding, Long> {
    List<AiModelTaskBinding> findByModelId(Long modelId);
    List<AiModelTaskBinding> findByBusinessTypeAndEnabledTrue(String businessType);
    List<AiModelTaskBinding> findByModelIdIn(Collection<Long> modelIds);
    void deleteByModelId(Long modelId);
}

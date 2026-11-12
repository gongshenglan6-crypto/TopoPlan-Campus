package com.agent.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "ai_model_task_bindings", uniqueConstraints = @UniqueConstraint(name = "uk_ai_model_task", columnNames = {"model_id", "business_type"}))
public class AiModelTaskBinding {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "model_id", nullable = false)
    private Long modelId;
    @Column(name = "business_type", nullable = false, length = 64)
    private String businessType;
    @Column(nullable = false)
    private boolean enabled = true;

    protected AiModelTaskBinding() { }
    public AiModelTaskBinding(Long modelId, String businessType) { this.modelId = modelId; this.businessType = businessType; }
    public Long getModelId() { return modelId; }
    public String getBusinessType() { return businessType; }
    public boolean isEnabled() { return enabled; }
}

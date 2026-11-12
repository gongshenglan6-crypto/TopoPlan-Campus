package com.agent.admin.config;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "admin_configs", uniqueConstraints = @UniqueConstraint(name = "uk_admin_config_module_key", columnNames = {"module", "item_key"}))
public class AdminConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 60)
    private String module;
    @Column(name = "item_key", nullable = false, length = 120)
    private String itemKey;
    @Lob @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Lob @Column(name = "secret_payload", columnDefinition = "TEXT")
    private String secretPayload;
    @Column(nullable = false)
    private Instant updatedAt;

    protected AdminConfig() { }
    public AdminConfig(String module, String itemKey, String payload) {
        this.module = module; this.itemKey = itemKey; this.payload = payload; this.updatedAt = Instant.now();
    }
    public Long getId() { return id; }
    public String getModule() { return module; }
    public String getItemKey() { return itemKey; }
    public String getPayload() { return payload; }
    public String getSecretPayload() { return secretPayload; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setPayload(String payload) { this.payload = payload; this.updatedAt = Instant.now(); }
    public void setSecretPayload(String secretPayload) { this.secretPayload = secretPayload; this.updatedAt = Instant.now(); }
}

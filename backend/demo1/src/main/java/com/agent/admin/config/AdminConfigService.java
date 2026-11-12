package com.agent.admin.config;

import com.agent.common.api.ApiException;
import com.agent.common.audit.AuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

@Service
public class AdminConfigService {
    private final AdminConfigRepository repository;
    private final ObjectMapper mapper;
    private final AuditService auditService;
    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminConfigService(AdminConfigRepository repository, ObjectMapper mapper, AuditService auditService,
                               @Value("${topo.config-encryption-key}") String encryptionKey) {
        this.repository = repository; this.mapper = mapper; this.auditService = auditService;
        try {
            this.secretKey = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(encryptionKey.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String module) {
        return repository.findByModuleOrderByUpdatedAtDesc(module).stream().map(this::toMap).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String module, String key) {
        return repository.findByModuleAndItemKey(module, key).map(this::toMap).orElseGet(() -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("key", key); result.put("module", module); result.put("configured", false);
            return result;
        });
    }

    @Transactional
    public Map<String, Object> save(String module, String key, Map<String, Object> values) {
        Map<String, Object> payload = new LinkedHashMap<>(values == null ? Map.of() : values);
        String apiKey = payload.get("apiKey") instanceof String value ? value.trim() : null;
        payload.remove("apiKey");
        payload.remove("apiKeyConfigured");
        payload.remove("secret");
        AdminConfig record = repository.findByModuleAndItemKey(module, key).orElseGet(() -> new AdminConfig(module, key, "{}"));
        if (apiKey != null && !apiKey.isBlank() && !"configured".equalsIgnoreCase(apiKey)) {
            record.setSecretPayload(encrypt(apiKey));
        }
        record.setPayload(write(payload));
        AdminConfig saved = repository.save(record);
        auditService.record(module, "SAVE", saved.getId(), "SUCCESS", key);
        return toMap(saved);
    }

    @Transactional
    public void delete(String module, String key) {
        repository.findByModuleAndItemKey(module, key).ifPresent(record -> {
            repository.delete(record); auditService.record(module, "DELETE", record.getId(), "SUCCESS", key);
        });
    }

    private Map<String, Object> toMap(AdminConfig record) {
        Map<String, Object> result = new LinkedHashMap<>(read(record.getPayload()));
        result.put("id", record.getId()); result.put("key", record.getItemKey()); result.put("module", record.getModule());
        result.put("updatedAt", record.getUpdatedAt()); result.put("configured", true);
        result.put("apiKeyConfigured", record.getSecretPayload() != null && !record.getSecretPayload().isBlank());
        return result;
    }

    private String encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONFIG_ENCRYPT_ERROR", "Configuration secret cannot be stored");
        }
    }
    private Map<String, Object> read(String payload) {
        try { return mapper.readValue(payload, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONFIG_READ_ERROR", "Configuration is invalid"); }
    }
    private String write(Map<String, Object> payload) {
        try { return mapper.writeValueAsString(payload); }
        catch (JsonProcessingException exception) { throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CONFIG_WRITE_ERROR", "Configuration cannot be saved"); }
    }
}

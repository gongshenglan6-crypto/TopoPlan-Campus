package com.agent.ai;

import com.agent.common.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class ApiKeyCrypto {
    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyCrypto(@Value("${topo.config-encryption-key}") String encryptionKey) {
        try {
            secretKey = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(encryptionKey.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public String encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONFIG_ENCRYPT_ERROR", "模型密钥无法安全保存");
        }
    }

    public String decrypt(String value) {
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[12];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONFIG_DECRYPT_ERROR", "模型密钥无法读取");
        }
    }
}

package com.portoajuda.aplicacao_osc.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Gera OTPs e fingerprints com HMAC; o segredo nunca é exposto por toString. */
@Component
public class AdminAuthCodeSecurity {

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private final String pepper;
    private final SecureRandom random = new SecureRandom();
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    public AdminAuthCodeSecurity(@Value("${admin-auth.code-pepper:}") String pepper) {
        this.pepper = pepper == null ? "" : pepper;
    }

    @PostConstruct
    void validarConfiguracaoAoIniciar() {
        requirePepper();
    }

    public String generateCode() {
        requirePepper();
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    public String hashCode(UUID challengeId, String channel, String code) {
        return hmac("otp:" + challengeId + ":" + channel, code);
    }

    public boolean matchesCode(UUID challengeId, String channel, String candidate, String expectedHash) {
        if (candidate == null || expectedHash == null) {
            return false;
        }
        byte[] actual = hashCode(challengeId, channel, candidate).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }

    public String accountKey(String email) {
        return hmac("account", email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }

    public String ipKey(String ip) {
        return hmac("ip", ip == null || ip.isBlank() ? "unknown" : ip.trim());
    }

    public String phoneKey(String phone) {
        return hmac("phone", phone == null ? "" : phone);
    }

    public String hashIdentifier(String purpose, String value) {
        return hmac("identifier:" + purpose, value == null ? "" : value);
    }

    public byte[] encryptDeliveryCode(UUID challengeId, String channel, String code) {
        requirePepper();
        try {
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey(), "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD((challengeId + ":" + channel).getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(code.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
            return result;
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("Não foi possível proteger os dados de entrega.");
        }
    }

    public String decryptDeliveryCode(UUID challengeId, String channel, byte[] encryptedCode) {
        requirePepper();
        if (encryptedCode == null || encryptedCode.length <= GCM_NONCE_BYTES) {
            throw new IllegalStateException("O conteúdo da entrega protegida é inválido.");
        }
        try {
            byte[] nonce = java.util.Arrays.copyOfRange(encryptedCode, 0, GCM_NONCE_BYTES);
            byte[] ciphertext = java.util.Arrays.copyOfRange(
                    encryptedCode, GCM_NONCE_BYTES, encryptedCode.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey(), "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD((challengeId + ":" + channel).getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("Não foi possível abrir os dados de entrega.");
        }
    }

    private byte[] encryptionKey() throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal("porto-ajuda:admin-auth-delivery-encryption:v1"
                .getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(String purpose, String value) {
        requirePepper();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((purpose + ":" + value).getBytes(StandardCharsets.UTF_8));
            char[] result = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                int b = digest[i] & 0xff;
                result[i * 2] = HEX[b >>> 4];
                result[i * 2 + 1] = HEX[b & 0x0f];
            }
            return new String(result);
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("Não foi possível proteger o desafio.");
        }
    }

    private void requirePepper() {
        if (pepper.isBlank() || pepper.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "ADMIN_AUTH_CODE_PEPPER é obrigatório e deve conter pelo menos 32 bytes UTF-8. "
                            + "Configure-o como variável de ambiente ou em .env local ignorado pelo Git.");
        }
    }
}

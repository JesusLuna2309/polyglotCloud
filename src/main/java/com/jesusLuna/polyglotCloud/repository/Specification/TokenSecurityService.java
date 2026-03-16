package com.jesusLuna.polyglotCloud.repository.Specification;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.digests.SHAKEDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenSecurityService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String hmacSecret;

    public TokenSecurityService(@Value("${security.hmac.secret}") String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    public String deriveRedisKey(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive Redis key from token", e);
        }
    }

    public String generateSecureToken(String context) {
        // Generar 256 bits de entropía aleatoria
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);

        // Añadir timestamp para unicidad
        long timestamp = System.nanoTime();
        byte[] timestampBytes = String.valueOf(timestamp).getBytes(StandardCharsets.UTF_8);

        // Contexto para diferenciar tokens (ej: "refresh", "email_verification")
        byte[] contextBytes = context.getBytes(StandardCharsets.UTF_8);

        // SHAKE-256 (post-cuántico) para derivar el token final
        SHAKEDigest shake = new SHAKEDigest(256);
        shake.update(randomBytes, 0, randomBytes.length);
        shake.update(timestampBytes, 0, timestampBytes.length);
        shake.update(contextBytes, 0, contextBytes.length);

        byte[] tokenBytes = new byte[32]; // 256 bits, suficiente para un token seguro
        shake.doFinal(tokenBytes, 0, 32);

        // Base64 URL-safe sin padding
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    
}

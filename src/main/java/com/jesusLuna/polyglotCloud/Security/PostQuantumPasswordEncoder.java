package com.jesusLuna.polyglotCloud.Security;


import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import org.bouncycastle.crypto.digests.SHAKEDigest;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.springframework.stereotype.Component;

/**
 * Implementación de password encoder con consideraciones post-cuánticas
 * Usa Argon2id + SHAKE-256 para máxima resistencia cuántica
 */
@Component
public class PostQuantumPasswordEncoder {
    
    private static final int SALT_LENGTH = 32; // 256 bits
    private static final int HASH_LENGTH = 64; // 512 bits para resistencia cuántica
    private static final int MEMORY_COST = 65536; // 64 MB
    private static final int TIME_COST = 3; // 3 iteraciones
    private static final int PARALLELISM = 1; // 1 thread (puedes aumentar)
    
    private final SecureRandom secureRandom;
    
    public PostQuantumPasswordEncoder() {
        this.secureRandom = new SecureRandom();
    }
    
    /**
     * Hashea una contraseña usando Argon2id + SHAKE-256
     * @param rawPassword contraseña en texto plano
     * @return hash en formato base64 con salt incluido
     */
    public String encode(String rawPassword) {
        // Generar salt criptográficamente seguro
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        
        // Configurar Argon2id (resistente a ataques GPU y timing)
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(MEMORY_COST)
                .withIterations(TIME_COST)
                .withParallelism(PARALLELISM)
                .withSalt(salt)
                .build();
        
        // Generar hash con Argon2
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        
        byte[] argon2Hash = new byte[HASH_LENGTH / 2]; // 32 bytes
        generator.generateBytes(rawPassword.toCharArray(), argon2Hash);
        
        // Aplicar SHAKE-256 para resistencia cuántica adicional
        byte[] finalHash = applySHAKE256(argon2Hash, rawPassword.getBytes(StandardCharsets.UTF_8));
        
        // Combinar salt + hash para almacenamiento
        byte[] combined = new byte[salt.length + finalHash.length];
        System.arraycopy(salt, 0, combined, 0, salt.length);
        System.arraycopy(finalHash, 0, combined, salt.length, finalHash.length);
        
        return Base64.getEncoder().encodeToString(combined);
    }
    
    /**
     * Verifica si una contraseña coincide con el hash almacenado
     * @param rawPassword contraseña en texto plano
     * @param encodedPassword hash almacenado (base64)
     * @return true si coinciden
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        try {
            // Decodificar el hash almacenado
            byte[] combined = Base64.getDecoder().decode(encodedPassword);
            
            // Extraer salt y hash
            byte[] salt = new byte[SALT_LENGTH];
            byte[] storedHash = new byte[combined.length - SALT_LENGTH];
            
            System.arraycopy(combined, 0, salt, 0, SALT_LENGTH);
            System.arraycopy(combined, SALT_LENGTH, storedHash, 0, storedHash.length);
            
            // Recalcular hash con el mismo salt
            Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withMemoryAsKB(MEMORY_COST)
                    .withIterations(TIME_COST)
                    .withParallelism(PARALLELISM)
                    .withSalt(salt)
                    .build();
            
            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(params);
            
            byte[] argon2Hash = new byte[HASH_LENGTH / 2];
            generator.generateBytes(rawPassword.toCharArray(), argon2Hash);
            
            // Aplicar SHAKE-256
            byte[] calculatedHash = applySHAKE256(argon2Hash, rawPassword.getBytes(StandardCharsets.UTF_8));
            
            // Comparación time-constant para evitar timing attacks
            return constantTimeEquals(storedHash, calculatedHash);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Aplica SHAKE-256 para resistencia cuántica adicional
     */
    private byte[] applySHAKE256(byte[] argon2Hash, byte[] password) {
        SHAKEDigest shake = new SHAKEDigest(256);
        
        // Input: Argon2 hash + password original (para más entropía)
        shake.update(argon2Hash, 0, argon2Hash.length);
        shake.update(password, 0, password.length);
        
        // Output: 512 bits (64 bytes) para resistencia cuántica
        byte[] output = new byte[HASH_LENGTH];
        shake.doFinal(output, 0, HASH_LENGTH);
        
        return output;
    }
    
    /**
     * Comparación en tiempo constante para evitar timing attacks
     */
    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        
        return result == 0;
    }
    
    /**
     * Información sobre la configuración actual
     */
    public String getAlgorithmInfo() {
        return String.format(
            "Post-Quantum Password Encoder: Argon2id(m=%d,t=%d,p=%d) + SHAKE-256(%d bits)",
            MEMORY_COST, TIME_COST, PARALLELISM, HASH_LENGTH * 8
        );
    }
}
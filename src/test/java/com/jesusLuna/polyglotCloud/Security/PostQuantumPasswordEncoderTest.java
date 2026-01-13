package com.jesusLuna.polyglotCloud.security;

import org.junit.jupiter.api.Test;

import com.jesusLuna.polyglotCloud.security.PostQuantumPasswordEncoder;

import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class PostQuantumPasswordEncoderTest {
    
    private final PostQuantumPasswordEncoder encoder = new PostQuantumPasswordEncoder();
    
    @Test
    @DisplayName("Debe hashear y verificar contraseñas correctamente")
    void testPasswordEncodingAndMatching() {
        String rawPassword = "mi_super_contraseña_secreta_123!";
        
        // Hashear la contraseña
        String hashedPassword = encoder.encode(rawPassword);
        
        // Verificaciones básicas
        assertNotNull(hashedPassword);
        assertNotEquals(rawPassword, hashedPassword);
        assertTrue(hashedPassword.length() > 100); // Base64 del salt + hash
        
        // Debe verificar correctamente la contraseña original
        assertTrue(encoder.matches(rawPassword, hashedPassword));
        
        // No debe verificar contraseñas incorrectas
        assertFalse(encoder.matches("contraseña_incorrecta", hashedPassword));
        assertFalse(encoder.matches("", hashedPassword));
        assertFalse(encoder.matches("mi_super_contraseña_secreta_124!", hashedPassword));
    }
    
    @Test
    @DisplayName("Cada hash debe ser único (diferentes salts)")
    void testUniqueHashes() {
        String password = "misma_contraseña";
        
        String hash1 = encoder.encode(password);
        String hash2 = encoder.encode(password);
        
        // Los hashes deben ser diferentes (por el salt aleatorio)
        assertNotEquals(hash1, hash2);
        
        // Pero ambos deben verificar la misma contraseña
        assertTrue(encoder.matches(password, hash1));
        assertTrue(encoder.matches(password, hash2));
    }
    
    @Test
    @DisplayName("Debe manejar contraseñas con caracteres especiales")
    void testSpecialCharacters() {
        String[] passwords = {
            "contraseña_con_ñ_y_acentós",
            "🔐🚀 emoji_password 🛡️",
            "password with spaces and symbols !@#$%^&*()",
            "密码测试中文",
            "пароль_кириллица"
        };
        
        for (String password : passwords) {
            String hash = encoder.encode(password);
            assertTrue(encoder.matches(password, hash), 
                      "Falló con la contraseña: " + password);
        }
    }
    
    @Test
    @DisplayName("Debe mostrar información del algoritmo")
    void testAlgorithmInfo() {
        String info = encoder.getAlgorithmInfo();
        
        assertNotNull(info);
        assertTrue(info.contains("Post-Quantum"));
        assertTrue(info.contains("Argon2id"));
        assertTrue(info.contains("SHAKE-256"));
        assertTrue(info.contains("512 bits"));
        
        System.out.println("Algoritmo: " + info);
    }
    
    @Test
    @DisplayName("Debe rechazar hashes malformados")
    void testMalformedHashes() {
        assertFalse(encoder.matches("cualquier_contraseña", "hash_inválido"));
        assertFalse(encoder.matches("password", ""));
        assertFalse(encoder.matches("password", "dGVzdA==")); // Base64 pero muy corto
    }
}
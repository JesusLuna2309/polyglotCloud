package com.jesusLuna.polyglotCloud.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jesusLuna.polyglotCloud.exception.BusinessRuleException;
import com.jesusLuna.polyglotCloud.models.Translations.Translation;
import com.jesusLuna.polyglotCloud.models.enums.TranslationStatus;
import com.jesusLuna.polyglotCloud.repository.TranslationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TranslationDeduplicationService {

    private final TranslationRepository translationRepository;

    /**
     * Busca si existe una traducción completada idéntica
     * @param sourceLanguageId ID del idioma fuente
     * @param targetLanguageId ID del idioma destino
     * @param sourceCode Código fuente a traducir
     * @return Optional con la traducción existente si se encuentra
     */
    public Optional<Translation> findExistingTranslation(
            UUID sourceLanguageId, 
            UUID targetLanguageId, 
            String sourceCode) {
        
        log.debug("Searching for existing translation from {} to {} with code hash", 
                sourceLanguageId, targetLanguageId);
        
        // Generar hash del contenido
        String contentHash = generateContentHash(sourceLanguageId, targetLanguageId, sourceCode);
        
        // Buscar traducción existente completada con el mismo hash
        Optional<Translation> existing = translationRepository
                .findCompletedByContentHash(contentHash);
        
        if (existing.isPresent()) {
            log.info("Found existing completed translation with hash: {} for languages {} -> {}", 
                    contentHash, sourceLanguageId, targetLanguageId);
            
            // Validación defensiva contra colisiones
            Translation translation = existing.get();
            if (isActualDuplicate(translation, sourceLanguageId, targetLanguageId, sourceCode)) {
                return existing;
            } else {
                log.warn("Hash collision detected for content hash: {}. Proceeding with new translation.", 
                        contentHash);
                return Optional.empty();
            }
        }
        
        return Optional.empty();
    }

    /**
     * Genera un hash único para la combinación de idiomas y código
     * @param sourceLanguageId ID del idioma fuente
     * @param targetLanguageId ID del idioma destino  
     * @param sourceCode Código fuente
     * @return Hash SHA-256 en formato hexadecimal
     */
    public String generateContentHash(UUID sourceLanguageId, UUID targetLanguageId, String sourceCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Normalizar el código (remover espacios y saltos de línea extra)
            String normalizedCode = normalizecode(sourceCode);
            
            // Combinar: sourceLanguageId + targetLanguageId + normalizedCode
            String combined = sourceLanguageId.toString() + 
                             "|" + targetLanguageId.toString() + 
                             "|" + normalizedCode;
            
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            
            // Convertir a hexadecimal
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new BusinessRuleException("Hash generation failed");
        }
    }

    /**
     * Crea una copia de una traducción existente para un nuevo usuario
     * @param existingTranslation Traducción existente a reutilizar
     * @param newRequesterId ID del nuevo usuario solicitante
     * @return Nueva traducción basada en la existente
     */
    @Transactional
    public Translation reuseTranslation(Translation existingTranslation, UUID newRequesterId) {
        log.info("Reusing existing translation {} for user {}", 
                existingTranslation.getId(), newRequesterId);
        
        // Crear nueva traducción basada en la existente
        Translation newTranslation = Translation.builder()
                .sourceSnippet(existingTranslation.getSourceSnippet())
                .sourceLanguage(existingTranslation.getSourceLanguage())
                .targetLanguage(existingTranslation.getTargetLanguage())
                .sourceCode(existingTranslation.getSourceCode())
                .translatedCode(existingTranslation.getTranslatedCode())
                .status(TranslationStatus.COMPLETED)
                .processingTimeMs(0L) // Reuso instantáneo
                .translationNotes("Reused from existing translation ID: " + existingTranslation.getId())
                .contentHash(existingTranslation.getContentHash())
                .currentVersionNumber(1)
                .build();
        
        // Establecer timestamps
        newTranslation.setCompletedAt(java.time.Instant.now());
        
        Translation saved = translationRepository.save(newTranslation);
        
        log.info("Successfully reused translation with ID: {}", saved.getId());
        return saved;
    }

    /**
     * Normaliza el código para generar hashes consistentes
     * @param code Código original
     * @return Código normalizado
     */
    private String normalizecode(String code) {
        if (code == null) {
            return "";
        }
        
        return code
                // Normalizar saltos de línea
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                // Remover espacios extra al final de líneas
                .replaceAll("[ \\t]+\\n", "\n")
                // Remover líneas vacías múltiples consecutivas
                .replaceAll("\\n\\s*\\n\\s*\\n", "\n\n")
                // Trimear el inicio y final
                .strip();
    }

    /**
     * Validación defensiva contra colisiones de hash
     * Compara realmente el contenido para asegurar que es idéntico
     */
    private boolean isActualDuplicate(
            Translation existing, 
            UUID sourceLanguageId, 
            UUID targetLanguageId, 
            String sourceCode) {
        
        boolean languageMatch = existing.getSourceLanguage().getId().equals(sourceLanguageId) &&
                               existing.getTargetLanguage().getId().equals(targetLanguageId);
        
        boolean codeMatch = normalizecode(existing.getSourceCode())
                           .equals(normalizecode(sourceCode));
        
        return languageMatch && codeMatch;
    }
}
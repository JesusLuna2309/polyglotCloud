package com.jesusLuna.polyglotCloud.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jesusLuna.polyglotCloud.dto.TranslationDTO;
import com.jesusLuna.polyglotCloud.exception.ResourceNotFoundException;
import com.jesusLuna.polyglotCloud.models.Language;
import com.jesusLuna.polyglotCloud.models.Snippet;
import com.jesusLuna.polyglotCloud.models.Translation;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.models.enums.TranslationStatus;
import com.jesusLuna.polyglotCloud.repository.LanguageRepository;
import com.jesusLuna.polyglotCloud.repository.SnippetRepository;
import com.jesusLuna.polyglotCloud.repository.TranslationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TranslationService {
    private final TranslationRepository translationRepository;
    private final SnippetRepository snippetRepository;
    private final LanguageRepository languageRepository;
    private final CacheService cacheService;

    @Transactional
    public Translation requestTranslation(TranslationDTO.TranslationRequest request, User requestedBy) {
        log.info("Requesting translation from snippet {} to language {}", request.snippetId(), request.targetLanguage());

        // Validar snippet
        Snippet sourceSnippet = snippetRepository.findById(request.snippetId())
                .orElseThrow(() -> new ResourceNotFoundException("Snippet", "id", request.snippetId()));

        // Validar idioma destino
        Language targetLanguage = languageRepository.findByNameIgnoreCase(request.targetLanguage())
                .orElseThrow(() -> new ResourceNotFoundException("Language", "name", request.targetLanguage()));

        // Crear traducción
        Translation translation = Translation.builder()
                .sourceSnippet(sourceSnippet)
                .sourceLanguage(sourceSnippet.getLanguage())
                .targetLanguage(targetLanguage)
                .requestedBy(requestedBy)
                .sourceCode(sourceSnippet.getContent())
                .status(TranslationStatus.PENDING)
                .build();

        Translation saved = translationRepository.save(translation);

        // Procesar asíncronamente
        processTranslationAsync(saved.getId());

        return saved;
    }

    @Cacheable(value = "translation", key = "#id")
    public Translation getTranslationById(UUID id) {
        log.debug("Fetching translation with id: {}", id);
        return translationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Translation", "id", id));
    }

    public Page<Translation> getUserTranslations(UUID userId, Pageable pageable) {
        log.debug("Fetching translations for user: {}", userId);
        return translationRepository.findByRequestedByIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Async("translationExecutor")
    @Transactional
    public void processTranslationAsync(UUID translationId) {
        log.info("Starting async processing for translation: {}", translationId);
        
        Instant startTime = Instant.now();
        
        try {
            Translation translation = translationRepository.findById(translationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Translation", "id", translationId));

            // Marcar como procesando
            translation.setStatus(TranslationStatus.PROCESSING);
            translationRepository.save(translation);

            // Simular traducción (aquí irías con OpenAI/Claude/etc)
            String translatedCode = performTranslation(
                    translation.getSourceCode(),
                    translation.getSourceLanguage().getName(),
                    translation.getTargetLanguage().getName()
            );

            // Calcular tiempo de procesamiento
            long processingTime = Instant.now().toEpochMilli() - startTime.toEpochMilli();

            // Actualizar como completado
            translation.setTranslatedCode(translatedCode);
            translation.setStatus(TranslationStatus.COMPLETED);
            translation.setProcessingTimeMs(processingTime);
            translation.setCompletedAt(Instant.now());

            translationRepository.save(translation);

            // Limpiar cache
            cacheService.delete("translation::" + translationId);

            log.info("Translation {} completed successfully in {}ms", translationId, processingTime);

        } catch (Exception e) {
            log.error("Translation {} failed", translationId, e);
            
            // Marcar como fallido
            Translation translation = translationRepository.findById(translationId).orElse(null);
            if (translation != null) {
                translation.setStatus(TranslationStatus.FAILED);
                translation.setErrorMessage(e.getMessage());
                translation.setCompletedAt(Instant.now());
                
                long processingTime = Instant.now().toEpochMilli() - startTime.toEpochMilli();
                translation.setProcessingTimeMs(processingTime);
                
                translationRepository.save(translation);

                // Limpiar cache
                cacheService.delete("translation::" + translationId);
            }
        }
    }

    private String performTranslation(String sourceCode, String fromLanguage, String toLanguage) {
        log.debug("Translating from {} to {}", fromLanguage, toLanguage);
        
        // Simular tiempo de procesamiento
        try {
            Thread.sleep(2000 + (long)(Math.random() * 3000)); // 2-5 segundos
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Translation interrupted", e);
        }

        // Aquí implementarías la integración real con OpenAI, Claude, etc.
        // Por ahora retornamos una traducción simulada
        return String.format("""
                // Translated from %s to %s
                // Original code:
                // %s
                
                // Translated equivalent:
                %s
                """, 
                fromLanguage, 
                toLanguage, 
                sourceCode,
                generateMockTranslation(sourceCode, toLanguage));
    }

    private String generateMockTranslation(String sourceCode, String targetLanguage) {
        // Mock translation basado en el lenguaje objetivo
        return switch (targetLanguage.toLowerCase()) {
            case "python" -> "# Python equivalent\nprint(\"Hello, World!\")";
            case "javascript" -> "// JavaScript equivalent\nconsole.log(\"Hello, World!\");";
            case "java" -> "// Java equivalent\nSystem.out.println(\"Hello, World!\");";
            case "go" -> "// Go equivalent\nfmt.Println(\"Hello, World!\")";
            default -> "// " + targetLanguage + " equivalent\n// Translation not available for this language";
        };
    }
}

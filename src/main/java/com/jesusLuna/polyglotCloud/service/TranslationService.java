package com.jesusLuna.polyglotCloud.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jesusLuna.polyglotCloud.DTO.TranslationDTO;
import com.jesusLuna.polyglotCloud.Exception.ForbiddenAccessException;
import com.jesusLuna.polyglotCloud.Exception.ResourceNotFoundException;
import com.jesusLuna.polyglotCloud.mapper.TranslationMapper;
import com.jesusLuna.polyglotCloud.models.Language;
import com.jesusLuna.polyglotCloud.models.Snippet;
import com.jesusLuna.polyglotCloud.models.Translations.Translation;
import com.jesusLuna.polyglotCloud.models.Translations.TranslationVersion;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.models.enums.TranslationStatus;
import com.jesusLuna.polyglotCloud.repository.LanguageRepository;
import com.jesusLuna.polyglotCloud.repository.SnippetRepository;
import com.jesusLuna.polyglotCloud.repository.TranslationRepository;
import com.jesusLuna.polyglotCloud.repository.TranslationVersionRepository;
import com.jesusLuna.polyglotCloud.repository.UserRepository;

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
    private final TranslationVersionRepository versionRepository;
    private final CacheService cacheService;
    private final TranslationMapper translationMapper;
    private final UserRepository userRepository;

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
                .currentVersionNumber(1)
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

    /**
     * Obtiene traducciones por estado con paginación
     */
    @Transactional(readOnly = true)
    public Page<TranslationDTO.TranslationResponse> getTranslationsByStatus(
            TranslationStatus status, Pageable pageable) {
        
        log.debug("Getting translations with status: {}", status);
        
        Page<Translation> translations = translationRepository.findByStatus(status, pageable);
        
        return translations.map(translationMapper::toResponse);
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

            Translation savedTranslation = translationRepository.save(translation);

            // Crear versión inicial automáticamente
            createInitialVersion(savedTranslation, translatedCode);

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

    private void createInitialVersion(Translation translation, String translatedCode) {
        log.debug("Creating initial version for translation {}", translation.getId());
        
        TranslationVersion initialVersion = TranslationVersion.builder()
                .translation(translation)
                .versionNumber(1)
                .translatedCode(translatedCode)
                .author(translation.getRequestedBy()) // El autor original es quien crea la primera versión
                .changeNotes("Initial AI-generated translation")
                .isCurrentVersion(true)
                .build();

        versionRepository.save(initialVersion);
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
        return generateMockTranslation(sourceCode, toLanguage);
    }

    @Transactional
    public TranslationDTO.TranslationResponse submitForReview(UUID translationId, UUID userId) {
        Translation translation = getTranslationById(translationId);
        
        // Verificar que es el autor
        if (!translation.getRequestedBy().getId().equals(userId)) {
            throw new ForbiddenAccessException("Only the author can submit for review");
        }
        
        // Cambiar estado
        translation.changeStatus(TranslationStatus.UNDER_REVIEW, null, null);
        
        return translationMapper.toResponse(translationRepository.save(translation));
    }

    @Transactional
    @PreAuthorize("hasRole('MODERATOR')")
    public TranslationDTO.TranslationResponse approveTranslation(UUID translationId, UUID reviewerId, String notes) {
        Translation translation = getTranslationById(translationId);
        User reviewer = userRepository.findById(reviewerId)
            .orElseThrow(() -> new ResourceNotFoundException("Reviewer not found"));
        
        translation.changeStatus(TranslationStatus.APPROVED, reviewer, notes);
        
        return translationMapper.toResponse(translationRepository.save(translation));
    }

    @Transactional 
    @PreAuthorize("hasRole('MODERATOR')")
    public TranslationDTO.TranslationResponse rejectTranslation(UUID translationId, UUID reviewerId, String notes) {
        Translation translation = getTranslationById(translationId);
        User reviewer = userRepository.findById(reviewerId)
            .orElseThrow(() -> new ResourceNotFoundException("Reviewer not found"));
        
        translation.changeStatus(TranslationStatus.REJECTED, reviewer, notes);
        
        return translationMapper.toResponse(translationRepository.save(translation));
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

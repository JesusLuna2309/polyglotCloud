package com.jesusLuna.polyglotCloud.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jesusLuna.polyglotCloud.dto.TranslationVersionDTO;
import com.jesusLuna.polyglotCloud.exception.BusinessRuleException;
import com.jesusLuna.polyglotCloud.exception.ForbiddenAccessException;
import com.jesusLuna.polyglotCloud.exception.ResourceNotFoundException;
import com.jesusLuna.polyglotCloud.mapper.TranslationVersionMapper;
import com.jesusLuna.polyglotCloud.models.Translations.Translation;
import com.jesusLuna.polyglotCloud.models.Translations.TranslationVersion;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.repository.TranslationRepository;
import com.jesusLuna.polyglotCloud.repository.TranslationVersionRepository;
import com.jesusLuna.polyglotCloud.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TranslationVersionService {

    private final TranslationVersionRepository versionRepository;
    private final TranslationRepository translationRepository;
    private final UserRepository userRepository;
    private final TranslationVersionMapper versionMapper;
    private final CacheService cacheService;

    @Transactional
    public TranslationVersionDTO.VersionResponse createVersion(
            UUID translationId, 
            TranslationVersionDTO.CreateVersionRequest request, 
            UUID authorId) {
        
        log.info("Creating new version for translation {} by user {}", translationId, authorId);

        // Validar traducción
        Translation translation = translationRepository.findById(translationId)
                .orElseThrow(() -> new ResourceNotFoundException("Translation", "id", translationId));

        // Validar permisos: solo el autor original o moderadores pueden crear versiones
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", authorId));

        if (!translation.getRequestedBy().getId().equals(authorId) && !author.getRole().isModerator()) {
            throw new ForbiddenAccessException("Only the original author or moderators can create versions");
        }

        // Validar que no sea igual a la versión actual
        TranslationVersion currentVersion = translation.getCurrentVersion();
        if (currentVersion != null && 
            currentVersion.getTranslatedCode().equals(request.translatedCode())) {
            throw new BusinessRuleException("New version cannot be identical to current version");
        }

        // Desmarcar versión actual
        if (currentVersion != null) {
            versionRepository.unmarkCurrentVersionsForTranslation(translationId);
        }

        // Obtener siguiente número de versión
        Integer nextVersionNumber = versionRepository.findMaxVersionNumberByTranslationId(translationId) + 1;

        // Crear nueva versión
        TranslationVersion newVersion = TranslationVersion.builder()
                .translation(translation)
                .versionNumber(nextVersionNumber)
                .translatedCode(request.translatedCode())
                .author(author)
                .changeNotes(request.changeNotes())
                .isCurrentVersion(true)
                .build();

        TranslationVersion savedVersion = versionRepository.save(newVersion);

        // Actualizar traducción
        translation.setTranslatedCode(request.translatedCode());
        translation.updateCurrentVersionNumber();
        translationRepository.save(translation);

        // Limpiar cache
        cacheService.delete("translation::" + translationId);

        log.info("Created version {} for translation {}", nextVersionNumber, translationId);
        return versionMapper.toResponse(savedVersion);
    }

    public TranslationVersionDTO.VersionResponse getVersion(UUID translationId, Integer versionNumber) {
        log.debug("Fetching version {} for translation {}", versionNumber, translationId);
        
        TranslationVersion version = versionRepository
                .findByTranslationIdAndVersionNumber(translationId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Version", "number", versionNumber + " for translation " + translationId));

        return versionMapper.toResponse(version);
    }

    public TranslationVersionDTO.VersionHistory getVersionHistory(UUID translationId, Pageable pageable) {
        log.debug("Fetching version history for translation {}", translationId);
        
        // Verificar que la traducción existe
        if (!translationRepository.existsById(translationId)) {
            throw new ResourceNotFoundException("Translation", "id", translationId);
        }

        List<TranslationVersion> versions = versionRepository
                .findByTranslationIdOrderByVersionNumberAsc(translationId);

        return versionMapper.toHistory(versions);
    }

    public Page<TranslationVersionDTO.VersionSummary> getVersionsPaginated(
            UUID translationId, Pageable pageable) {
        
        log.debug("Fetching paginated versions for translation {}", translationId);
        
        // Verificar que la traducción existe
        if (!translationRepository.existsById(translationId)) {
            throw new ResourceNotFoundException("Translation", "id", translationId);
        }

        Page<TranslationVersion> versions = versionRepository
                .findByTranslationIdOrderByVersionNumberDesc(translationId, pageable);

        return versions.map(versionMapper::toSummary);
    }

    public TranslationVersionDTO.VersionResponse getCurrentVersion(UUID translationId) {
        log.debug("Fetching current version for translation {}", translationId);
        
        TranslationVersion currentVersion = versionRepository
                .findByTranslationIdAndIsCurrentVersionTrue(translationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Current version not found for translation", "id", translationId));

        return versionMapper.toResponse(currentVersion);
    }

    @Transactional
    public TranslationVersionDTO.VersionResponse revertToVersion(
            UUID translationId, 
            Integer versionNumber, 
            UUID userId) {
        
        log.info("Reverting translation {} to version {} by user {}", 
                translationId, versionNumber, userId);

        // Validar traducción y permisos
        Translation translation = translationRepository.findById(translationId)
                .orElseThrow(() -> new ResourceNotFoundException("Translation", "id", translationId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!translation.getRequestedBy().getId().equals(userId) && !user.getRole().isModerator()) {
            throw new ForbiddenAccessException("Only the original author or moderators can revert versions");
        }

        // Encontrar la versión objetivo
        TranslationVersion targetVersion = versionRepository
                .findByTranslationIdAndVersionNumber(translationId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Version", "number", versionNumber + " for translation " + translationId));

        // Desmarcar versión actual
        versionRepository.unmarkCurrentVersionsForTranslation(translationId);

        // Marcar la versión objetivo como actual
        targetVersion.markAsCurrent();
        versionRepository.save(targetVersion);

        // Actualizar traducción
        translation.setTranslatedCode(targetVersion.getTranslatedCode());
        translation.setCurrentVersionNumber(versionNumber);
        translationRepository.save(translation);

        // Limpiar cache
        cacheService.delete("translation::" + translationId);

        log.info("Reverted translation {} to version {}", translationId, versionNumber);
        return versionMapper.toResponse(targetVersion);
    }
}
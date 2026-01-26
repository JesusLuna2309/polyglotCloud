package com.jesusLuna.polyglotCloud.service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jesusLuna.polyglotCloud.dto.SnippetDTO;
import com.jesusLuna.polyglotCloud.exception.BusinessRuleException;
import com.jesusLuna.polyglotCloud.exception.ForbiddenAccessException;
import com.jesusLuna.polyglotCloud.exception.ResourceNotFoundException;
import com.jesusLuna.polyglotCloud.models.Language;
import com.jesusLuna.polyglotCloud.models.Snippet;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.models.enums.SnippetStatus;
import com.jesusLuna.polyglotCloud.repository.LanguageRepository;
import com.jesusLuna.polyglotCloud.repository.SnippetRepository;
import com.jesusLuna.polyglotCloud.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SnippetService {

    private final SnippetRepository snippetRepository;
    private final LanguageRepository languageRepository;
    private final UserRepository userRepository;
    private final CacheService cacheService;


    @Transactional
    public Snippet createSnippet(SnippetDTO.SnippetCreateRequest request, UUID userId) {
        log.debug("Creating snippet with title: {} for user: {}", request.title(), userId);

        // Validar que el título sea único
        if (snippetRepository.existsByTitle(request.title())) {
            throw new BusinessRuleException("A snippet with this title already exists");
        }

        // Validar y obtener el lenguaje
        Language language = languageRepository.findById(request.languageId())
                .orElseThrow(() -> new ResourceNotFoundException("Language", "id", request.languageId()));

         // Necesitas inyectar UserRepository arriba si no lo tienes ya
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Crear el snippet
        Snippet snippet = Snippet.builder()
                .title(request.title())
                .content(request.code())
                .description(request.description())
                .language(language)
                .user(user)
                .status(SnippetStatus.DRAFT)
                .isPublic(Boolean.TRUE.equals(request.isPublic()))
                .build();

        Snippet saved = snippetRepository.save(snippet);
        log.info("Snippet created successfully with id: {}", saved.getId());
        return saved;
    }

    public Snippet getSnippetById(UUID id) {
        String cacheKey = "snippet:" + id;
        
        // Intentar obtener del caché
        Snippet cached = (Snippet) cacheService.get(cacheKey);
        if (cached != null) {
            log.debug("Snippet found in cache: {}", id);
            return cached;
        }

        // Si no está en caché, buscar en BD
        Snippet snippet = snippetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Snippet", "id", id));
        
        // Guardar en caché por 10 minutos
        cacheService.save(cacheKey, snippet, 10, TimeUnit.MINUTES);
        
        return snippet;
    }

    public Page<Snippet> listPublicSnippets(Pageable pageable) {
        log.debug("Listing public snippets with pageable: {}", pageable);
        return snippetRepository.findAllPublic(pageable);
    }

    public Page<Snippet> listSnippetsByStatus(SnippetStatus status, Pageable pageable) {
        log.debug("Listing snippets by status: {} with pageable: {}", status, pageable);
        return snippetRepository.findByStatus(status, pageable);
    }

    public Page<Snippet> listSnippetsByUser(UUID userId, Pageable pageable) {
        log.debug("Listing snippets for user: {} with pageable: {}", userId, pageable);
        return snippetRepository.findByUserId(userId, pageable);
    }

    public Page<Snippet> listSnippetsByUserAndStatus(SnippetStatus status, UUID userId,  Pageable pageable){
        log.debug("Listing snippets with status: {} for user: {} with pageable: {}", status, userId, pageable);
        return snippetRepository.findByUserIdAndStatus(userId, status, pageable);
    }

    public Page<Snippet> searchSnippets(SnippetDTO.SnippetSearchFilters filters, Pageable pageable) {
        log.debug("Searching snippets with filters: {}", filters);
        return snippetRepository.searchSnippets(
                filters.query(),
                filters.userId(),
                filters.languageId(),
                filters.status(),
                filters.isPublic(),
                pageable
        );
    }

    public Page<Snippet> searchSnippets(
            String query,
            UUID userId,
            UUID languageId,
            SnippetStatus status,
            Boolean isPublic,
            Pageable pageable) {
        
        log.debug("Searching snippets with query: '{}', userId: {}, languageId: {}, status: {}, isPublic: {}", 
                query, userId, languageId, status, isPublic);

        // Usamos el método unificado y optimizado del repositorio
        return snippetRepository.searchSnippets(query, userId, languageId, status, isPublic, pageable);
    }

    @Transactional
    public Snippet updateSnippet(UUID id, SnippetDTO.SnippetUpdateRequest request, UUID userId) {
        log.debug("Updating snippet with id: {} for user: {}", id, userId);

        Snippet snippet = getSnippetById(id);

        // Validar ownership
        if (!snippet.belongsTo(userId)) {
            throw new ForbiddenAccessException("User does not own this snippet");
        }

        // Validar que el snippet puede ser editado
        if (!snippet.canEdit()) {
            throw new IllegalStateException("Snippet cannot be edited in its current state: " + snippet.getStatus());
        }

        // Actualizar campos no nulos
        if (request.title() != null && !request.title().equals(snippet.getTitle())) {
            if (snippetRepository.existsByTitle(request.title())) {
                throw new BusinessRuleException("A snippet with this title already exists");
            }
            snippet.setTitle(request.title());
        }
        if (request.code() != null) {
            snippet.setContent(request.code());
        }
        if (request.description() != null) {
            snippet.setDescription(request.description());
        }
        if (request.languageId() != null) {
            Language language = languageRepository.findById(request.languageId())
                    .orElseThrow(() -> new ResourceNotFoundException("Language", "id", request.languageId()));
            snippet.setLanguage(language);
        }
        if (request.status() != null) {
            snippet.setStatus(request.status());
        }
        if (request.isPublic() != null) {
            snippet.setPublic(request.isPublic());
        }

        Snippet updated = snippetRepository.save(snippet);
        log.info("Snippet updated successfully with id: {}", updated.getId());
        return updated;
    }

    @Transactional
    public Snippet publishSnippet(UUID id, boolean makePublic, UUID userId) {
        log.debug("Publishing snippet with id: {} (makePublic: {}) for user: {}", id, makePublic, userId);

        Snippet snippet = getSnippetById(id);

        if (!snippet.belongsTo(userId)) {
            throw new ForbiddenAccessException("User does not own this snippet");
        }

        snippet.publish(makePublic);
        Snippet published = snippetRepository.save(snippet);
        log.info("Snippet published successfully with id: {}", published.getId());
        return published;
    }

    @Transactional
    public void deleteSnippet(UUID id, UUID userId) {
        log.debug("Soft deleting snippet with id: {} for user: {}", id, userId);

        Snippet snippet = getSnippetById(id);

        if (!snippet.belongsTo(userId)) {
            throw new ForbiddenAccessException("User does not own this snippet");
        }

        snippet.softDelete();
        snippetRepository.save(snippet);
        log.info("Snippet soft deleted successfully with id: {}", id);
        cacheService.delete("snippet:" + id);
        cacheService.deletePattern("snippets:*"); // Invalidar listas
    }

    @Transactional
    public Snippet archiveSnippet(UUID id, UUID userId) {
        log.debug("Archiving snippet with id: {} for user: {}", id, userId);

        Snippet snippet = getSnippetById(id);

        if (!snippet.belongsTo(userId)) {
            throw new ForbiddenAccessException("User does not own this snippet");
        }

        snippet.archive();
        Snippet archived = snippetRepository.save(snippet);
        log.info("Snippet archived successfully with id: {}", archived.getId());
        return archived;
    }

    @Transactional
public Snippet translateSnippet(UUID originalId, SnippetDTO.SnippetTranslateRequest request, UUID userId) {
    log.debug("Creating translation of snippet {} to language {} for user: {}",
            originalId, request.languageId(), userId);

    // 1. Obtener el snippet original
    Snippet originalSnippet = getSnippetById(originalId);

    // 2. Verificar permisos: Solo el autor puede crear traducciones
    if (!originalSnippet.belongsTo(userId)) {
        throw new ForbiddenAccessException("Only the author can create translations of this snippet");
    }

    // 3. Obtener el lenguaje de destino
    Language targetLanguage = languageRepository.findById(request.languageId())
            .orElseThrow(() -> new ResourceNotFoundException("Language", "id", request.languageId()));

    // 4. Validar que no sea el mismo lenguaje
    if (originalSnippet.getLanguage().getId().equals(request.languageId())) {
        throw new BusinessRuleException("Cannot translate to the same language");
    }

    // 5. Verificar que no exista ya una traducción a este lenguaje
    // (Opcional: Si quieres permitir solo una traducción por lenguaje)
    boolean translationExists = snippetRepository.existsByOriginalSnippetIdAndLanguageId(
        originalSnippet.getId(), request.languageId()
    );
    if (translationExists) {
        throw new BusinessRuleException("A translation to this language already exists");
    }

    // 6. Validar título único
    if (snippetRepository.existsByTitle(request.title())) {
        throw new BusinessRuleException("A snippet with this title already exists");
    }

    // 7. Obtener el usuario
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

    // 8. Crear la traducción
    Snippet translation = Snippet.builder()
            .title(request.title())
            .content(request.code())
            .description(request.description() != null ? request.description() : originalSnippet.getDescription())
            .language(targetLanguage)
            .user(user)
            .originalSnippet(originalSnippet) // IMPORTANTE: Vincular con el original
            .status(SnippetStatus.DRAFT) // Siempre empieza como borrador
            .isPublic(request.isPublic() != null ? Boolean.TRUE.equals(request.isPublic()) : originalSnippet.isPublic()) // Hereda visibilidad
            .build();

    Snippet savedTranslation = snippetRepository.save(translation);
    
    log.info("Translation created successfully with id: {} for original snippet: {}",
            savedTranslation.getId(), originalId);
    
    return savedTranslation;
}



    public boolean isOwner(UUID snippetId, UUID userId) {
        return snippetRepository.existsByIdAndUserId(snippetId, userId);
    }
}
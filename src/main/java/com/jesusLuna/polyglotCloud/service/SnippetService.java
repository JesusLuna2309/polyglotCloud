package com.jesusLuna.polyglotCloud.service;

import java.util.UUID;

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
import com.jesusLuna.polyglotCloud.repository.UserRespository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SnippetService {

    private final SnippetRepository snippetRepository;
    private final LanguageRepository languageRepository;
    private final UserRespository userRepository;

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
                .isPublic(request.isPublic() != null ? request.isPublic() : false)
                .build();

        Snippet saved = snippetRepository.save(snippet);
        log.info("Snippet created successfully with id: {}", saved.getId());
        return saved;
    }

    public Snippet getSnippetById(UUID id) {
        log.debug("Fetching snippet with id: {}", id);
        return snippetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Snippet", "id", id));
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

    public boolean isOwner(UUID snippetId, UUID userId) {
        return snippetRepository.existsByIdAndUserId(snippetId, userId);
    }
}
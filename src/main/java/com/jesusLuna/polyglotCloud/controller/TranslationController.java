package com.jesusLuna.polyglotCloud.controller;

import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jesusLuna.polyglotCloud.dto.TranslationDTO;
import com.jesusLuna.polyglotCloud.mapper.TranslationMapper;
import com.jesusLuna.polyglotCloud.models.Translation;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.repository.UserRepository;
import com.jesusLuna.polyglotCloud.service.TranslationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/translations")
@Tag(name = "Translation", description = "Async code translation endpoints")
public class TranslationController {

    private final TranslationService translationService;
    private final TranslationMapper translationMapper;
    private final UserRepository userRepository;

    @PostMapping
    @Operation(
        summary = "Request code translation", 
        description = "Requests asynchronous translation of a code snippet to another programming language"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Translation request accepted and queued for processing"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Snippet or target language not found"),
        @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public ResponseEntity<TranslationDTO.TranslationStatusResponse> requestTranslation(
            @Valid @RequestBody TranslationDTO.TranslationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Translation request received from user: {} for snippet: {} to language: {}", 
                userDetails.getUsername(), request.snippetId(), request.targetLanguage());

        User user = userRepository.findByUsernameAndDeletedAtIsNull(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Translation translation = translationService.requestTranslation(request, user);
        
        TranslationDTO.TranslationStatusResponse response = translationMapper.toStatusResponse(translation);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get translation status and result", 
        description = "Retrieves the current status and result (if completed) of a translation request"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Translation found and returned"),
        @ApiResponse(responseCode = "404", description = "Translation not found"),
        @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public ResponseEntity<TranslationDTO.TranslationResponse> getTranslation(
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        log.debug("Getting translation {} for user: {}", id, userDetails.getUsername());

        Translation translation = translationService.getTranslationById(id);
        
        // Verificar que el usuario puede ver esta traducción
        if (!translation.getRequestedBy().getUsername().equals(userDetails.getUsername())) {
            log.warn("User {} tried to access translation {} owned by {}", 
                    userDetails.getUsername(), id, translation.getRequestedBy().getUsername());
            return ResponseEntity.notFound().build();
        }

        TranslationDTO.TranslationResponse response = translationMapper.toResponse(translation);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(
        summary = "Get user's translations",
        description = "Retrieves a paginated list of translations requested by the authenticated user"
    )
    @ApiResponse(responseCode = "200", description = "Translations retrieved successfully")
    public ResponseEntity<Page<TranslationDTO.TranslationStatusResponse>> getUserTranslations(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        log.debug("Getting translations for user: {}", userDetails.getUsername());

        User user = userRepository.findByUsernameAndDeletedAtIsNull(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Page<Translation> translations = translationService.getUserTranslations(user.getId(), pageable);
        
        Page<TranslationDTO.TranslationStatusResponse> response = translations.map(translationMapper::toStatusResponse);
        
        return ResponseEntity.ok(response);
    }
}

package com.jesusLuna.polyglotCloud.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jesusLuna.polyglotCloud.DTO.TranslationVersionDTO;
import com.jesusLuna.polyglotCloud.service.TranslationVersionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/translations/{translationId}/versions")
@Tag(name = "Translation Versions", description = "Translation versioning system endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class TranslationVersionController {

    private final TranslationVersionService versionService;

    @PostMapping
    @Operation(
        summary = "Create new translation version",
        description = "Creates a new version of a translation with improvements or corrections"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Version created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Translation not found"),
        @ApiResponse(responseCode = "409", description = "Version identical to current version")
    })
    public ResponseEntity<TranslationVersionDTO.VersionResponse> createVersion(
            @PathVariable UUID translationId,
            @Valid @RequestBody TranslationVersionDTO.CreateVersionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        UUID authorId = UUID.fromString(userDetails.getUsername());
        
        log.info("Creating version for translation {} by user {}", translationId, authorId);
        
        TranslationVersionDTO.VersionResponse response = versionService
                .createVersion(translationId, request, authorId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(
        summary = "Get translation version history",
        description = "Retrieves the complete version history of a translation"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Version history retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Translation not found")
    })
    public ResponseEntity<TranslationVersionDTO.VersionHistory> getVersionHistory(
            @PathVariable UUID translationId) {

        log.debug("Fetching version history for translation {}", translationId);
        
        TranslationVersionDTO.VersionHistory history = versionService
                .getVersionHistory(translationId, Pageable.unpaged());
        
        return ResponseEntity.ok(history);
    }

    @GetMapping("/paginated")
    @Operation(
        summary = "Get paginated translation versions",
        description = "Retrieves translation versions with pagination support"
    )
    @ApiResponse(responseCode = "200", description = "Versions retrieved successfully")
    public ResponseEntity<Page<TranslationVersionDTO.VersionSummary>> getVersionsPaginated(
            @PathVariable UUID translationId,
            @PageableDefault(size = 10, sort = "versionNumber", direction = Sort.Direction.DESC) 
            Pageable pageable) {

        log.debug("Fetching paginated versions for translation {}", translationId);
        
        Page<TranslationVersionDTO.VersionSummary> versions = versionService
                .getVersionsPaginated(translationId, pageable);
        
        return ResponseEntity.ok(versions);
    }

    @GetMapping("/{versionNumber}")
    @Operation(
        summary = "Get specific translation version",
        description = "Retrieves a specific version of a translation by version number"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Version retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Translation or version not found")
    })
    public ResponseEntity<TranslationVersionDTO.VersionResponse> getVersion(
            @PathVariable UUID translationId,
            @PathVariable Integer versionNumber) {

        log.debug("Fetching version {} for translation {}", versionNumber, translationId);
        
        TranslationVersionDTO.VersionResponse version = versionService
                .getVersion(translationId, versionNumber);
        
        return ResponseEntity.ok(version);
    }

    @GetMapping("/current")
    @Operation(
        summary = "Get current translation version",
        description = "Retrieves the current (active) version of a translation"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current version retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Translation or current version not found")
    })
    public ResponseEntity<TranslationVersionDTO.VersionResponse> getCurrentVersion(
            @PathVariable UUID translationId) {

        log.debug("Fetching current version for translation {}", translationId);
        
        TranslationVersionDTO.VersionResponse version = versionService
                .getCurrentVersion(translationId);
        
        return ResponseEntity.ok(version);
    }

    @PutMapping("/{versionNumber}/revert")
    @Operation(
        summary = "Revert to specific version",
        description = "Reverts the translation to a specific previous version"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reverted successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Translation or version not found")
    })
    public ResponseEntity<TranslationVersionDTO.VersionResponse> revertToVersion(
            @PathVariable UUID translationId,
            @PathVariable Integer versionNumber,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = UUID.fromString(userDetails.getUsername());
        
        log.info("Reverting translation {} to version {} by user {}", 
                translationId, versionNumber, userId);
        
        TranslationVersionDTO.VersionResponse response = versionService
                .revertToVersion(translationId, versionNumber, userId);
        
        return ResponseEntity.ok(response);
    }
}

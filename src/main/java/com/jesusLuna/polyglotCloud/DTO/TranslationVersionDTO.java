package com.jesusLuna.polyglotCloud.DTO;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TranslationVersionDTO {

    public record CreateVersionRequest(
            @NotBlank(message = "Translated code is required")
            String translatedCode,
            
            @Size(max = 1000, message = "Change notes cannot exceed 1000 characters")
            String changeNotes
    ) {}

    public record VersionResponse(
            UUID id,
            UUID translationId,
            Integer versionNumber,
            String translatedCode,
            String authorName,
            UUID authorId,
            String changeNotes,
            Boolean isCurrentVersion,
            Instant createdAt
    ) {}

    public record VersionSummary(
            UUID id,
            Integer versionNumber,
            String authorName,
            UUID authorId,
            String changeNotes,
            Boolean isCurrentVersion,
            Instant createdAt
    ) {}

    public record VersionHistory(
            UUID translationId,
            Integer totalVersions,
            Integer currentVersionNumber,
            java.util.List<VersionSummary> versions
    ) {}
}
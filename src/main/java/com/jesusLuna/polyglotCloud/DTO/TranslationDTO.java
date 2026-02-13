package com.jesusLuna.polyglotCloud.dto;

import java.time.Instant;
import java.util.UUID;

import com.jesusLuna.polyglotCloud.models.enums.TranslationStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TranslationDTO {
        public record TranslationRequest(
                @NotNull(message = "Snippet ID is required")
                UUID snippetId,
                
                @NotBlank(message = "Target language is required")
                String targetLanguage
        ) {}

        public record TranslationResponse(
                UUID id,
                UUID snippetId,
                String sourceLanguage,
                String targetLanguage,
                String sourceCode,
                String translatedCode,
                TranslationStatus status,
                String errorMessage,
                String translationNotes,
                Long processingTimeMs,
                Instant createdAt,
                Instant updatedAt,
                Instant completedAt
        ) {}

        public record TranslationStatusResponse(
                UUID id,
                TranslationStatus status,
                String errorMessage,
                Long processingTimeMs,
                Instant createdAt,
                Instant updatedAt,
                Instant completedAt
        ) {}

        /**
         * Request para acciones de moderación
         */
        public record ModerationRequest(
        @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
        String notes
        ) {}
}

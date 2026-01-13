package com.jesusLuna.polyglotCloud.dto;

import java.time.Instant;
import java.util.UUID;

import com.jesusLuna.polyglotCloud.models.enums.SnippetStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SnippetDTO {
                public record SnippetDetailResponse(
                UUID id,
                String title,
                String code,        // El contenido del código (lo más importante aquí)
                String description,
                // CAMBIO CLAVE: Devolvemos objetos o nombres, no solo IDs sueltos
                String languageName,
                String authorName,
                UUID authorId,       // Útil por si quieren hacer clic en el perfil del autor
                SnippetStatus status,
                boolean isPublic,
                Instant createdAt,
                Instant updatedAt
        ) {}

        public record SnippetPublicResponse(
                UUID id,
                String title,
                String languageName, // O el Enum Language
                Instant createdAt,
                String authorName // <--- IMPORTANTE: Quién lo creó
        ) {}
        public record SnippetSummaryResponse(
                UUID id,
                String title,
                String description,
                UUID languageId,
                UUID userId,
                SnippetStatus status,
                boolean isPublic,
                Instant createdAt,
                Instant updatedAt
        ) {}

        public record SnippetCreateRequest(
                @NotBlank(message = "Title is required")
                @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
                String title,
                
                @NotBlank(message = "Code is required")
                String code,
                
                @Size(max = 1000, message = "Description cannot exceed 1000 characters")
                String description,
                
                @NotNull(message = "Language ID is required")
                UUID languageId,
                
                Boolean isPublic  // Optional, defaults to false
        ) {}

        public record SnippetUpdateRequest(
                @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
                String title,
                
                String code,
                
                @Size(max = 1000, message = "Description cannot exceed 1000 characters")
                String description,
                
                UUID languageId,
                
                SnippetStatus status,
                
                Boolean isPublic
        ) {}

        public record SnippetPublishRequest(
                @NotNull(message = "Make public flag is required")
                Boolean makePublic
        ) {}

        public record SnippetSearchFilters(
                String query,           // Busca en title y description
                UUID userId,            // Filtra por usuario
                UUID languageId,        // Filtra por lenguaje
                SnippetStatus status,   // Filtra por estado
                Boolean isPublic        // Filtra por visibilidad
        ) {}
}
package com.jesusLuna.polyglotCloud.models;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import com.jesusLuna.polyglotCloud.models.enums.SnippetStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
@Entity
@Table(name = "snippets")
@Data
@Builder
public class Snippet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Title is required")
    @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    @Column(nullable = false, unique = true, length = 255)
    private String title;

    @NotBlank(message = "Code is required")
    @Column(nullable = false)
    private String content;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Column(length = 1000)
    private String description;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean isPublic = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SnippetStatus status = SnippetStatus.DRAFT;

    @NotNull(message = "Language is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "language_id", nullable = false)
    private Language language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public void archive() {
        this.status = SnippetStatus.ARCHIVED;
        this.isPublic = false;
    }

    public void softDelete() {
        this.status = SnippetStatus.DELETED;
        this.isPublic = false;
    }

    public void publish(boolean makePublic) {
        this.status = SnippetStatus.PUBLISHED;
        if (makePublic) {
            this.isPublic = true;
        }
    }

    public boolean canEdit() {
        return this.status.isEditable();
    }

    public boolean belongsTo(UUID userId) {
        return this.user.getId() != null && this.user.getId().equals(userId);
    }
}
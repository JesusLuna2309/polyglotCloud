package com.jesusLuna.polyglotCloud.models;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.jesusLuna.polyglotCloud.models.enums.SnippetStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@Entity
@Table(name = "snippets")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Snippet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Title is required")
    @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    @Column(nullable = false, unique = true, length = 255)
    private String title;

    @Size(message = "Description cannot exceed 65535 characters")  // Sin límite max
    @Column(columnDefinition = "TEXT")
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

    @NotNull(message = "User is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

        // Referencia al snippet original (si este es una traducción)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_snippet_id")
    private Snippet originalSnippet;

    // Todas las traducciones de este snippet (si este es el original)
    @OneToMany(mappedBy = "originalSnippet", fetch = FetchType.LAZY)
    private Set<Snippet> translations;

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
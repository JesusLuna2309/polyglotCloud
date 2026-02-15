package com.jesusLuna.polyglotCloud.models.Translations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.jesusLuna.polyglotCloud.models.Language;
import com.jesusLuna.polyglotCloud.models.Snippet;
import com.jesusLuna.polyglotCloud.models.User;
import com.jesusLuna.polyglotCloud.models.enums.TranslationStatus;
import com.jesusLuna.polyglotCloud.models.Translations.TranslationVersion;

import jakarta.persistence.CascadeType;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "translations")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Translation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snippet_id", nullable = false)
    private Snippet sourceSnippet;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_language_id", nullable = false)
    private Language sourceLanguage;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_language_id", nullable = false)
    private Language targetLanguage;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "source_code", columnDefinition = "TEXT", nullable = false)
    private String sourceCode;

    @Column(name = "translated_code", columnDefinition = "TEXT")
    private String translatedCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TranslationStatus status = TranslationStatus.PENDING;

        @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;
    
    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;
    
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "error_message")
    @Size(max = 1000)
    private String errorMessage;

    @Column(name = "translation_notes", columnDefinition = "TEXT")
    private String translationNotes;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "translation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TranslationVersion> versions = new ArrayList<>();

    @Column(name = "current_version_number", nullable = false)
    @Builder.Default
    private Integer currentVersionNumber = 1;

        public void changeStatus(TranslationStatus newStatus, User reviewer, String notes) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                String.format("Cannot transition from %s to %s", this.status, newStatus)
            );
        }
        
        this.status = newStatus;
        
        // Si es una acción de moderación, registrar revisor
        if (newStatus == TranslationStatus.APPROVED || newStatus == TranslationStatus.REJECTED) {
            this.reviewedBy = reviewer;
            this.reviewNotes = notes;
            this.reviewedAt = Instant.now();
        }
    }

    /**
     * Obtiene la versión actual de la traducción
     */
    public TranslationVersion getCurrentVersion() {
        return versions.stream()
                .filter(TranslationVersion::getIsCurrentVersion)
                .findFirst()
                .orElse(null);
    }

    /**
     * Actualiza el número de versión actual
     */
    public void updateCurrentVersionNumber() {
        this.currentVersionNumber = versions.size();
    }

    /**
     * Obtiene el código traducido actual (de la versión actual)
     */
    public String getCurrentTranslatedCode() {
        TranslationVersion currentVersion = getCurrentVersion();
        return currentVersion != null ? currentVersion.getTranslatedCode() : this.translatedCode;
    }
}

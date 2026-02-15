package com.jesusLuna.polyglotCloud.models.Translations;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.jesusLuna.polyglotCloud.models.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "translation_versions")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslationVersion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "translation_id", nullable = false)
    private Translation translation;

    @NotNull
    @Column(nullable = false)
    @Positive
    private Integer versionNumber;

    @NotNull
    @Column(name = "translated_code", columnDefinition = "TEXT", nullable = false)
    private String translatedCode;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "change_notes", columnDefinition = "TEXT")
    private String changeNotes;

    @Column(name = "is_current_version", nullable = false)
    @Builder.Default
    private Boolean isCurrentVersion = false;

    @Column(name = "upvotes_count", nullable = false)
    @Builder.Default
    private Integer upvotesCount = 0;

    @Column(name = "downvotes_count", nullable = false)
    @Builder.Default
    private Integer downvotesCount = 0;

    @Column(name = "total_score", nullable = false)
    @Builder.Default
    private Integer totalScore = 0;

    @OneToMany(mappedBy = "translationVersion", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TranslationVote> votes = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Marca esta versión como la actual y desmarca las demás
     */
    public void markAsCurrent() {
        this.isCurrentVersion = true;
    }

    /**
     * Desmarca esta versión como actual
     */
    public void unmarkAsCurrent() {
        this.isCurrentVersion = false;
    }

    /**
     * Recalcula las puntuaciones basándose en los votos
     */
    public void recalculateScores() {
        this.upvotesCount = (int) votes.stream()
                .filter(vote -> vote.getVoteType().isUpvote())
                .count();
        
        this.downvotesCount = (int) votes.stream()
                .filter(vote -> vote.getVoteType().isDownvote())
                .count();
        
        this.totalScore = upvotesCount - downvotesCount;
    }

    /**
     * Obtiene la puntuación neta (upvotes - downvotes)
     */
    public Integer getNetScore() {
        return totalScore;
    }

    /**
     * Obtiene el total de votos (upvotes + downvotes)
     */
    public Integer getTotalVotes() {
        return upvotesCount + downvotesCount;
    }

    /**
     * Calcula el porcentaje de aprobación
     */
    public Double getApprovalRate() {
        int totalVotes = getTotalVotes();
        if (totalVotes == 0) {
            return 0.0;
        }
        return (double) upvotesCount / totalVotes * 100;
    }

    /**
     * Verifica si esta versión tiene suficiente puntuación para auto-aprobación
     */
    public boolean hasHighEnoughScoreForAutoApproval(int threshold) {
        return totalScore >= threshold && upvotesCount >= 3; // Mínimo 3 votos positivos
    }
}

package com.jesusLuna.polyglotCloud.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jesusLuna.polyglotCloud.models.Translations.TranslationVote;
import com.jesusLuna.polyglotCloud.models.enums.VoteType;

@Repository
public interface TranslationVoteRepository extends JpaRepository<TranslationVote, UUID> {

    @EntityGraph(attributePaths = {"translationVersion", "user"})
    Optional<TranslationVote> findById(UUID id);

    @EntityGraph(attributePaths = {"user"})
    Optional<TranslationVote> findByTranslationVersionIdAndUserId(
            @Param("versionId") UUID versionId, 
            @Param("userId") UUID userId
    );

    @EntityGraph(attributePaths = {"user"})
    List<TranslationVote> findByTranslationVersionIdOrderByCreatedAtDesc(
            @Param("versionId") UUID versionId
    );

    @EntityGraph(attributePaths = {"user"})
    Page<TranslationVote> findByTranslationVersionIdOrderByCreatedAtDesc(
            @Param("versionId") UUID versionId, 
            Pageable pageable
    );

    @Query("SELECT COUNT(tv) FROM TranslationVote tv WHERE tv.translationVersion.id = :versionId AND tv.voteType = :voteType")
    long countByTranslationVersionIdAndVoteType(
            @Param("versionId") UUID versionId, 
            @Param("voteType") VoteType voteType
    );

    @Query("SELECT COUNT(tv) FROM TranslationVote tv WHERE tv.translationVersion.id = :versionId")
    long countByTranslationVersionId(@Param("versionId") UUID versionId);

    @Query("SELECT COALESCE(SUM(CASE WHEN tv.voteType = 'UPVOTE' THEN 1 WHEN tv.voteType = 'DOWNVOTE' THEN -1 ELSE 0 END), 0) " +
        "FROM TranslationVote tv WHERE tv.translationVersion.id = :versionId")
    int calculateNetScore(@Param("versionId") UUID versionId);

    @Query("SELECT tv FROM TranslationVote tv WHERE tv.user.id = :userId ORDER BY tv.createdAt DESC")
    Page<TranslationVote> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT tv FROM TranslationVote tv WHERE tv.translationVersion.id IN :versionIds AND tv.user.id = :userId")
    List<TranslationVote> findByVersionIdsAndUserId(
            @Param("versionIds") List<UUID> versionIds,
            @Param("userId") UUID userId
    );

    boolean existsByTranslationVersionIdAndUserId(
            @Param("versionId") UUID versionId,
            @Param("userId") UUID userId
    );
}
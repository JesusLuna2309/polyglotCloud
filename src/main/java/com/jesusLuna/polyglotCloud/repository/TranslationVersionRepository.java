package com.jesusLuna.polyglotCloud.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jesusLuna.polyglotCloud.models.Translations.TranslationVersion;

@Repository
public interface TranslationVersionRepository extends JpaRepository<TranslationVersion, UUID> {

    @EntityGraph(attributePaths = {"translation", "author"})
    Optional<TranslationVersion> findById(UUID id);

    @EntityGraph(attributePaths = {"author"})
    List<TranslationVersion> findByTranslationIdOrderByVersionNumberAsc(@Param("translationId") UUID translationId);

    @EntityGraph(attributePaths = {"author"})
    Page<TranslationVersion> findByTranslationIdOrderByVersionNumberDesc(@Param("translationId") UUID translationId, Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    Optional<TranslationVersion> findByTranslationIdAndVersionNumber(@Param("translationId") UUID translationId, @Param("versionNumber") Integer versionNumber);

    Optional<TranslationVersion> findByTranslationIdAndIsCurrentVersionTrue(@Param("translationId") UUID translationId);

    @Query("SELECT COALESCE(MAX(tv.versionNumber), 0) FROM TranslationVersion tv WHERE tv.translation.id = :translationId")
    Integer findMaxVersionNumberByTranslationId(@Param("translationId") UUID translationId);

    @Modifying
    @Query("UPDATE TranslationVersion tv SET tv.isCurrentVersion = false WHERE tv.translation.id = :translationId AND tv.isCurrentVersion = true")
    int unmarkCurrentVersionsForTranslation(@Param("translationId") UUID translationId);

    @Query("SELECT COUNT(tv) FROM TranslationVersion tv WHERE tv.translation.id = :translationId")
    long countByTranslationId(@Param("translationId") UUID translationId);

    @Query("SELECT tv FROM TranslationVersion tv WHERE tv.author.id = :authorId ORDER BY tv.createdAt DESC")
    Page<TranslationVersion> findByAuthorIdOrderByCreatedAtDesc(@Param("authorId") UUID authorId, Pageable pageable);
}
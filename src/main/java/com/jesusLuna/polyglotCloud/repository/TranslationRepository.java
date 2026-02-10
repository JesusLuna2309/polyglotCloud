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

import com.jesusLuna.polyglotCloud.models.Translation;
import com.jesusLuna.polyglotCloud.models.enums.TranslationStatus;

@Repository
public interface TranslationRepository extends JpaRepository<Translation, UUID> {

    @EntityGraph(attributePaths = {"sourceSnippet", "sourceLanguage", "targetLanguage", "requestedBy"})
    Optional<Translation> findById(UUID id);

    @Query("SELECT t FROM Translation t WHERE t.requestedBy.id = :userId ORDER BY t.createdAt DESC")
    Page<Translation> findByRequestedByIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT t FROM Translation t WHERE t.status = :status ORDER BY t.createdAt ASC")
    List<Translation> findByStatusOrderByCreatedAtAsc(@Param("status") TranslationStatus status);

    @Query("SELECT COUNT(t) FROM Translation t WHERE t.requestedBy.id = :userId AND t.status = :status")
    long countByRequestedByIdAndStatus(@Param("userId") UUID userId, @Param("status") TranslationStatus status);

    @Query("SELECT t FROM Translation t WHERE t.sourceSnippet.id = :snippetId ORDER BY t.createdAt DESC")
    List<Translation> findBySourceSnippetIdOrderByCreatedAtDesc(@Param("snippetId") UUID snippetId);
}
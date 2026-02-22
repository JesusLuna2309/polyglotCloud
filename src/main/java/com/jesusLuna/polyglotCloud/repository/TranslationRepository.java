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

import com.jesusLuna.polyglotCloud.models.Translations.Translation;
import com.jesusLuna.polyglotCloud.models.enums.TranslationStatus;

@Repository
public interface TranslationRepository extends JpaRepository<Translation, UUID> {

    @EntityGraph(attributePaths = {"sourceSnippet", "sourceLanguage", "targetLanguage", "requestedBy"})
    Optional<Translation> findById(UUID id);



    @Query("SELECT t FROM Translation t WHERE t.requestedBy.id = :userId ORDER BY t.createdAt DESC")
    Page<Translation> findByRequestedByIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    Page<Translation> findByStatus(TranslationStatus status, Pageable pageable);


    @Query("SELECT t FROM Translation t WHERE t.status = :status ORDER BY t.createdAt ASC")
    List<Translation> findByStatusOrderByCreatedAtAsc(@Param("status") TranslationStatus status);

    @Query("SELECT COUNT(t) FROM Translation t WHERE t.requestedBy.id = :userId AND t.status = :status")
    long countByRequestedByIdAndStatus(@Param("userId") UUID userId, @Param("status") TranslationStatus status);

    @Query("SELECT t FROM Translation t WHERE t.sourceSnippet.id = :snippetId ORDER BY t.createdAt DESC")
    List<Translation> findBySourceSnippetIdOrderByCreatedAtDesc(@Param("snippetId") UUID snippetId);

    @Query("SELECT t FROM Translation t " +
           "WHERE t.contentHash = :contentHash " +
           "AND t.status = 'COMPLETED' " +
           "AND t.createdAt = (" +
               "SELECT MAX(t2.createdAt) FROM Translation t2 " +
               "WHERE t2.contentHash = :contentHash AND t2.status = 'COMPLETED'" +
           ")")
    Optional<Translation> findCompletedByContentHash(@Param("contentHash") String contentHash);

    /**
     * Encuentra todas las traducciones con el mismo hash (para detectar duplicados)
     */
    @Query("SELECT t FROM Translation t WHERE t.contentHash = :contentHash ORDER BY t.createdAt ASC")
    List<Translation> findAllByContentHash(@Param("contentHash") String contentHash);

    /**
     * Verifica si existe una traducción completada con un hash específico
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Translation t WHERE t.contentHash = :contentHash AND t.status = 'COMPLETED'")
    boolean existsCompletedByContentHash(@Param("contentHash") String contentHash);
}
package com.jesusLuna.polyglotCloud.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jesusLuna.polyglotCloud.models.Snippet;
import com.jesusLuna.polyglotCloud.models.enums.SnippetStatus;

@Repository
public interface SnippetRepository extends JpaRepository<Snippet, UUID> {

    // Búsquedas básicas y paginadas
    Optional<Snippet> findByTitle(String title);
    @EntityGraph(attributePaths = {"user","language"})
    Page<Snippet> findByUserId(UUID userId, Pageable pageable);
    @EntityGraph(attributePaths = {"user","language"})
    Page<Snippet> findByLanguageId(UUID languageId, Pageable pageable);

    //TODO: Mejora final cachear la tabla language
    @EntityGraph(attributePaths = {"user", "language"})
    Page<Snippet> findByStatus(SnippetStatus status, Pageable pageable);

    // Búsqueda de Snippets Públicos
    @Query("""
        SELECT s FROM Snippet s
        JOIN FETCH s.user
        JOIN FETCH s.language
        WHERE s.status = 'PUBLISHED' AND s.isPublic = true
        ORDER BY s.createdAt DESC
    """)
    Page<Snippet> findAllPublic(Pageable pageable);

    @EntityGraph( attributePaths = {"user", "language"})
    Page<Snippet> findByUserIdAndStatus(UUID userId, SnippetStatus status, Pageable pageable);

    /**
     * 🔍 Buscador Principal (Search Engine)
     * Filtra por texto (título/descripción), usuario, lenguaje, estado y visibilidad.
     */
    @Query("""
        SELECT s FROM Snippet s
        JOIN FETCH s.user
        JOIN FETCH s.language
        WHERE (:query IS NULL OR
            LOWER(s.title) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(s.description) LIKE LOWER(CONCAT('%', :query, '%')))
        AND (:userId IS NULL OR s.user.id = :userId)
        AND (:languageId IS NULL OR s.language.id = :languageId)
        AND (:status IS NULL OR s.status = :status)
        AND (:isPublic IS NULL OR s.isPublic = :isPublic)
    """)
    Page<Snippet> searchSnippets(
        @Param("query") String query,
        @Param("userId") UUID userId,
        @Param("languageId") UUID languageId,
        @Param("status") SnippetStatus status,
        @Param("isPublic") Boolean isPublic,
        Pageable pageable);

    @Query(value = """
            SELECT s.*,
            ts_rank(s.search_vector, plainto_tsquery('english', :searchQuery)) as rank
            FROM snippets s
            WHERE s.search_vector @@ plainto_tsquery('english', :searchQuery)
            AND s.status = 'PUBLISHED'
            AND s.is_public = true
            ORDER BY rank DESC, s.created_at DESC
            """, nativeQuery = true)
    Page<Snippet> fullTextSearch(@Param("searchQuery") String searchQuery, Pageable pageable);

    @Query(value = """
            SELECT s.*,
            ts_rank(s.search_vector, plainto_tsquery('english', :searchQuery)) as rank
            FROM snippets s
            WHERE s.search_vector @@ plainto_tsquery('english', :searchQuery)
            AND (:userId IS NULL OR s.user_id = CAST(:userId AS uuid))
            AND (:languageId IS NULL OR s.language_id = CAST(:languageId AS uuid))
            AND (:status IS NULL OR s.status = :status)
            ORDER BY rank DESC, s.created_at DESC
            """, nativeQuery = true)
    Page<Snippet> fullTextSearchWithFilters(
            @Param("searchQuery") String searchQuery,
            @Param("userId") String userId,
            @Param("languageId") String languageId,
            @Param("status") String status,
            Pageable pageable);

    @Query("""
            SELECT s FROM Snippet s
            JOIN FETCH s.user
            JOIN FETCH s.language
            WHERE s.language.id = :languageId
            AND s.status = 'PUBLISHED'
            AND s.isPublic = true
            ORDER BY s.createdAt DESC
            """)
    Page<Snippet> findPublicByLanguage(@Param("languageId") UUID languageId, Pageable pageable);

    boolean existsByTitle(String title);

    // En SnippetRepository.java
    @EntityGraph(attributePaths = {"user", "language"})
    Page<Snippet> findByOriginalSnippetId(UUID originalSnippetId, Pageable pageable);

    boolean existsByOriginalSnippetIdAndLanguageId(UUID originalSnippetId, UUID languageId);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Snippet s WHERE s.id = :id AND s.user.id = :userId")
    boolean existsByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("SELECT COUNT(s) FROM Snippet s WHERE s.status = 'PUBLISHED' AND s.isPublic = true")
    long countPublicSnippets();


}
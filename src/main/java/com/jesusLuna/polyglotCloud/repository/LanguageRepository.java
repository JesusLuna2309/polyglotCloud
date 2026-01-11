package com.jesusLuna.polyglotCloud.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.jesusLuna.polyglotCloud.models.Language;

@Repository
public interface LanguageRepository  extends JpaRepository<Language, UUID>{

    Optional<Language> findByCode(String code);
    Optional<Language> findByName(String name);
    List<Language> findByActiveTrueOrderByNameAsc();

    /**
     * 📊 Estadísticas
     * Retorna: [languageId, languageName, snippetCount]
     */
    @Query("""
        SELECT l.id, l.name, COUNT(s.id)
        FROM Language l
        LEFT JOIN Snippet s ON s.language.id = l.id AND s.status = 'PUBLISHED' AND s.isPublic = true
        WHERE l.active = true
        GROUP BY l.id, l.name
        ORDER BY COUNT(s.id) DESC
    """)
    List<Object[]> getLanguageStatistics();

}

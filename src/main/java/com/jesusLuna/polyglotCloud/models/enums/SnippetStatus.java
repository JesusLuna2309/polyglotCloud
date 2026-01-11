package com.jesusLuna.polyglotCloud.models.enums;

public enum SnippetStatus {
 /**
     * Estado de borrador. El snippet está en proceso de edición.
     */
    DRAFT,
    
    /**
     * Estado publicado. El snippet está disponible según su configuración de privacidad.
     */
    PUBLISHED,
    
    /**
     * Estado archivado. El snippet no es visible públicamente pero se mantiene en el sistema.
     */
    ARCHIVED,
    
    /**
     * Estado eliminado. El snippet ha sido marcado como eliminado (soft delete).
     */
    DELETED;
    
    /**
     * Verifica si el snippet está en un estado visible para el público.
     * Solo los snippets con estado PUBLISHED pueden ser visibles públicamente.
     *
     * @return {@code true} si el estado es PUBLISHED, {@code false} de lo contrario.
     */
    public boolean isPublishable() {
        return this == PUBLISHED;
    }
    
    /**
     * Verifica si el snippet está en un estado editable.
     * Los snippets DRAFT y PUBLISHED pueden ser editados.
     *
     * @return {@code true} si el estado es DRAFT o PUBLISHED, {@code false} de lo contrario.
     */
    public boolean isEditable() {
        return this == DRAFT || this == PUBLISHED;
    }
    
    /**
     * Verifica si el snippet está activo (no eliminado ni archivado).
     *
     * @return {@code true} si el estado es DRAFT o PUBLISHED, {@code false} de lo contrario.
     */
    public boolean isActive() {
        return this == DRAFT || this == PUBLISHED;
    }
}

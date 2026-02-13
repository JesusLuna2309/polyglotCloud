package com.jesusLuna.polyglotCloud.models.enums;

public enum TranslationStatus {
/**
     * Traducción solicitada, pendiente de procesamiento
     */
    PENDING,
    
    /**
     * Traducción en proceso
     */
    PROCESSING,
    
    /**
     * Traducción completada exitosamente
     */
    COMPLETED,
    
    /**
     * Traducción falló
     */
    FAILED,

    DRAFT,        // Borrador, aún no enviado para revisión
    UNDER_REVIEW, // En proceso de moderación
    APPROVED,     // Aprobado por moderador
    REJECTED;   // Rechazado por moderador

    public boolean canTransitionTo(TranslationStatus newStatus) {
        return switch (this) {
            case PENDING -> newStatus == PROCESSING || newStatus == FAILED;
            case PROCESSING -> newStatus == COMPLETED || newStatus == FAILED;
            case COMPLETED -> newStatus == DRAFT || newStatus == UNDER_REVIEW;
            case FAILED -> newStatus == PENDING; // Reintento
            
            // Nuevos flujos de moderación
            case DRAFT -> newStatus == UNDER_REVIEW;
            case UNDER_REVIEW -> newStatus == APPROVED || newStatus == REJECTED;
            case APPROVED -> false; // Estado final
            case REJECTED -> newStatus == DRAFT; // Puede volver a borrador
        };
    }
    
    public boolean isCompleted() {
        return this == COMPLETED;
    }
    
    public boolean isFailed() {
        return this == FAILED;
    }
    
    public boolean isInProgress() {
        return this == PROCESSING;
    }
    
    public boolean isPending() {
        return this == PENDING;
    }

        // Estados que permiten edición
    public boolean isEditable() {
        return this == DRAFT || this == REJECTED;
    }
    
    // Estados finales
    public boolean isFinal() {
        return this == APPROVED || this == FAILED;
    }
}

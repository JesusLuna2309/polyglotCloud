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
    FAILED;
    
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
}

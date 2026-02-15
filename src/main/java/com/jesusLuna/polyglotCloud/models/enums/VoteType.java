package com.jesusLuna.polyglotCloud.models.enums;

public enum VoteType {
    /**
     * Voto positivo (+1)
     */
    UPVOTE(1),
    
    /**
     * Voto negativo (-1)
     */
    DOWNVOTE(-1);

    private final int value;

    VoteType(int value) {
        this.value = value;
    }

    /**
     * Obtiene el valor numérico del voto para cálculos
     * @return +1 para UPVOTE, -1 para DOWNVOTE
     */
    public int getValue() {
        return value;
    }

    /**
     * Verifica si es un voto positivo
     */
    public boolean isUpvote() {
        return this == UPVOTE;
    }

    /**
     * Verifica si es un voto negativo
     */
    public boolean isDownvote() {
        return this == DOWNVOTE;
    }

    /**
     * Obtiene el tipo de voto opuesto
     */
    public VoteType opposite() {
        return this == UPVOTE ? DOWNVOTE : UPVOTE;
    }
}
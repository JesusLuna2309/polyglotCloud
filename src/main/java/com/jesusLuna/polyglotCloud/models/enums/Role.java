package com.jesusLuna.polyglotCloud.models.enums;

public enum Role {
    /**
     * Nivel de rol de usuario básico.
     */
    USER(0),
    /**
     * Nivel de rol de moderador.
     */
    MODERATOR(1),
    /**
     * Nivel de rol de administrador.
     */
    ADMIN(2);

    private final int level;

    Role(int level) {
        this.level = level;
    }

    /**
     * Verifica si este rol es un administrador.
     *
     * @return {@code true} si el rol es {@link #ADMIN}, {@code false} de lo
     *         contrario.
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }

    /**
     * Verifica si este rol es un moderador o superior.
     *
     * @return {@code true} si el nivel del rol es igual o superior al de
     *         {@link #MODERATOR},
     *         {@code false} de lo contrario.
     */
    public boolean isModerator() {
        return this.level >= MODERATOR.level;
    }

    /**
     * Verifica si este rol es un usuario básico.
     *
     * @return {@code true} si el rol es {@link #USER}, {@code false} de lo
     *         contrario.
     */
    public boolean isUser() {
        return this == USER;
    }

    /**
     * Verifica si este rol tiene un nivel de permiso igual o superior
     * al rol requerido.
     * <p>
     * Es útil para comprobaciones de permisos jerárquicos. Por ejemplo,
     * un ADMIN tiene permiso para acceder a una función que requiere un MODERATOR.
     * </p>
     *
     * @param requiredRole El rol mínimo necesario.
     * @return {@code true} si el nivel de este rol es igual o superior al
     *         requerido,
     *         {@code false} de lo contrario.
     */
    public boolean hasPermission(Role requiredRole) {
        return this.level >= requiredRole.level;
    }
}
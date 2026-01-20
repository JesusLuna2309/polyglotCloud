-- ==============================================================================
-- 1. ÍNDICES PARCIALES
-- Solo indexamos lo que realmente consultamos frecuentemente
-- ==============================================================================

-- Eliminar índices básicos poco eficientes
DROP INDEX IF EXISTS idx_users_active;
DROP INDEX IF EXISTS idx_users_email_verified;

-- Índice parcial: Solo usuarios activos (más eficiente)
-- Reasoning: Raramente consultamos usuarios inactivos
CREATE INDEX idx_users_active_only ON users(id) WHERE is_active = true;

-- Índice parcial: Solo usuarios con email no verificado (para notificaciones)
-- Reasoning: Para encontrar rápido a quién mandar reminder de verificación
CREATE INDEX idx_users_email_unverified ON users(email, created_at) WHERE email_verified = false;

-- Índice parcial: Solo usuarios bloqueados (para reportes de seguridad)
CREATE INDEX idx_users_locked ON users(locked_until, failed_login_attempts) WHERE locked_until IS NOT NULL;

-- ==============================================================================
-- 2. NORMALIZACIÓN DE EMAILS
-- Prevenir juan@gmail.com vs Juan@Gmail.Com
-- ==============================================================================

-- Índice único basado en función para emails case-insensitive
CREATE UNIQUE INDEX idx_users_email_lower ON users (LOWER(email));

-- También para usernames (prevenir JuanPerez vs juanperez)
CREATE UNIQUE INDEX idx_users_username_lower ON users (LOWER(username));

-- ==============================================================================
-- 3. BÚSQUEDA DE TEXTO (Trigramas)
-- Para el search del admin: buscar "fer" → "fernando", "alfer", "fercho"
-- ==============================================================================

-- Habilitar extensión de trigramas
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Índice GIN para búsquedas fuzzy en username y email
CREATE INDEX idx_users_username_search ON users USING GIN (username gin_trgm_ops);
CREATE INDEX idx_users_email_search ON users USING GIN (email gin_trgm_ops);

-- Índice combinado para búsquedas de texto completo
CREATE INDEX idx_users_full_text_search ON users USING GIN (
    (COALESCE(username, '') || ' ' || COALESCE(email, '')) gin_trgm_ops
);

-- ==============================================================================
-- 4. CONSTRAINTS DE CHECK
-- La base de datos protege la integridad incluso si el backend falla
-- ==============================================================================

-- Validación de formato de email a nivel DB
ALTER TABLE users 
ADD CONSTRAINT check_email_format 
CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$');

-- Validación de username (solo caracteres válidos)
ALTER TABLE users 
ADD CONSTRAINT check_username_format 
CHECK (username ~* '^[a-zA-Z0-9._-]+$' AND LENGTH(username) >= 3);

-- Validaciones numéricas
ALTER TABLE users 
ADD CONSTRAINT check_failed_attempts_positive 
CHECK (failed_login_attempts >= 0 AND failed_login_attempts <= 50);

-- Validación de rol (solo valores enum válidos)
ALTER TABLE users 
ADD CONSTRAINT check_role_valid 
CHECK (role IN ('USER', 'ADMIN', 'MODERATOR'));

-- Validación temporal: locked_until debe ser futuro si existe
ALTER TABLE users 
ADD CONSTRAINT check_lock_future 
CHECK (locked_until IS NULL OR locked_until > CURRENT_TIMESTAMP - INTERVAL '1 day');

-- ==============================================================================
-- 5. SOFT DELETES
-- ==============================================================================

-- Agregar columna de soft delete
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP DEFAULT NULL;

-- Índice parcial para consultas normales (excluir borrados)
CREATE INDEX idx_users_not_deleted ON users(id, email, username) WHERE deleted_at IS NULL;

-- Índice para auditoría de eliminaciones
CREATE INDEX idx_users_deleted_audit ON users(deleted_at, id) WHERE deleted_at IS NOT NULL;

-- ==============================================================================
-- 6. ÍNDICES COMPUESTOS (Query Performance)
-- ==============================================================================

-- Para login: buscar por email/username + activo + no eliminado
CREATE INDEX idx_users_login_lookup ON users(email, username) 
WHERE is_active = true AND deleted_at IS NULL;

-- Para dashboard admin: usuarios recientes activos
CREATE INDEX idx_users_admin_recent ON users(created_at DESC, role) 
WHERE is_active = true AND deleted_at IS NULL;

-- Para seguridad: intentos fallidos recientes
CREATE INDEX idx_users_security_monitor ON users(failed_login_attempts, last_login_at) 
WHERE failed_login_attempts > 0 AND deleted_at IS NULL;

-- ==============================================================================
-- 7. FUNCIONES AUXILIARES
-- Funciones útiles para operaciones comunes
-- ==============================================================================

-- Función para soft delete
CREATE OR REPLACE FUNCTION soft_delete_user(user_uuid UUID)
RETURNS VOID AS $$
BEGIN
    UPDATE users 
    SET deleted_at = CURRENT_TIMESTAMP,
        is_active = false,
        email_verification_token = NULL,
        password_reset_token = NULL
    WHERE id = user_uuid AND deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

-- Función para restaurar usuario eliminado
CREATE OR REPLACE FUNCTION restore_user(user_uuid UUID)
RETURNS VOID AS $$
BEGIN
    UPDATE users 
    SET deleted_at = NULL,
        is_active = true
    WHERE id = user_uuid AND deleted_at IS NOT NULL;
END;
$$ LANGUAGE plpgsql;

-- ==============================================================================
-- 8. ESTADÍSTICAS Y MONITOREO 
-- Views para monitoring y estadísticas
-- ==============================================================================

-- View para estadísticas de usuarios (para dashboards)
CREATE VIEW user_stats AS
SELECT 
    COUNT(*) FILTER (WHERE deleted_at IS NULL) as active_users,
    COUNT(*) FILTER (WHERE email_verified = false AND deleted_at IS NULL) as unverified_users,
    COUNT(*) FILTER (WHERE locked_until IS NOT NULL AND deleted_at IS NULL) as locked_users,
    COUNT(*) FILTER (WHERE created_at > CURRENT_DATE - INTERVAL '30 days' AND deleted_at IS NULL) as new_users_30d,
    COUNT(*) FILTER (WHERE deleted_at IS NOT NULL) as deleted_users
FROM users;

-- View para análisis de seguridad
CREATE VIEW security_stats AS
SELECT 
    COUNT(*) FILTER (WHERE failed_login_attempts >= 3) as users_with_failed_attempts,
    AVG(failed_login_attempts) as avg_failed_attempts,
    COUNT(*) FILTER (WHERE locked_until IS NOT NULL) as currently_locked,
    MAX(failed_login_attempts) as max_failed_attempts
FROM users 
WHERE deleted_at IS NULL;

-- ==============================================================================
-- COMENTARIOS FINALES
-- ==============================================================================

COMMENT ON INDEX idx_users_active_only IS 'Partial index: Only active users for performance';
COMMENT ON INDEX idx_users_email_lower IS 'Case-insensitive unique email index';
COMMENT ON INDEX idx_users_username_search IS 'Trigram search for fuzzy username matching';
COMMENT ON CONSTRAINT check_email_format IS 'Email format validation at database level';
COMMENT ON COLUMN users.deleted_at IS 'Soft delete timestamp - NULL means not deleted';
COMMENT ON FUNCTION soft_delete_user IS 'Safely soft-delete user with cleanup';
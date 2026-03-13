-- Comentarios indicando que la tabla está deprecada
COMMENT ON TABLE refresh_tokens IS 'DEPRECATED: Refresh tokens moved to Redis for better performance. This table is kept for historical data only.';

-- Crear tabla de auditoría para eventos importantes de refresh tokens (opcional)
CREATE TABLE refresh_token_audit (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(50) NOT NULL, -- 'CREATED', 'ROTATED', 'REVOKED', 'EXPIRED'
    token_id VARCHAR(100), -- ID del token (no el token completo por seguridad)
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB -- información adicional
);

-- Índices para auditoría
CREATE INDEX idx_refresh_token_audit_user_id ON refresh_token_audit(user_id);
CREATE INDEX idx_refresh_token_audit_action ON refresh_token_audit(action);
CREATE INDEX idx_refresh_token_audit_created_at ON refresh_token_audit(created_at);

-- Función para limpiar datos antiguos de refresh_tokens (opcional)
CREATE OR REPLACE FUNCTION cleanup_old_refresh_tokens()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    -- Mantener solo tokens de los últimos 30 días para auditoría
    DELETE FROM refresh_tokens 
    WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '30 days';
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    INSERT INTO refresh_token_audit (user_id, action, metadata) 
    VALUES (
        '00000000-0000-0000-0000-000000000000'::UUID, 
        'CLEANUP', 
        jsonb_build_object('deleted_count', deleted_count, 'cleanup_date', CURRENT_TIMESTAMP)
    );
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Trigger automático de limpieza (ejecutar semanalmente)
-- En producción, esto se puede manejar como un job programado
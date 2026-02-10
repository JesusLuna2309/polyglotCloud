CREATE TABLE translations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    snippet_id UUID NOT NULL REFERENCES snippets(id) ON DELETE CASCADE,
    source_language_id UUID NOT NULL REFERENCES languages(id),
    target_language_id UUID NOT NULL REFERENCES languages(id),
    requested_by UUID NOT NULL REFERENCES users(id),
    source_code TEXT NOT NULL,
    translated_code TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(1000),
    translation_notes TEXT,
    processing_time_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- Índices para optimización
CREATE INDEX idx_translations_snippet_id ON translations(snippet_id);
CREATE INDEX idx_translations_requested_by ON translations(requested_by);
CREATE INDEX idx_translations_status ON translations(status);
CREATE INDEX idx_translations_created_at ON translations(created_at);

-- Trigger para actualizar updated_at
CREATE TRIGGER update_translations_updated_at 
    BEFORE UPDATE ON translations 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();
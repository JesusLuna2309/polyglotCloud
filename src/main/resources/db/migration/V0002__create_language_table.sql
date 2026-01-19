CREATE TABLE languages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(50) NOT NULL UNIQUE,
    extension VARCHAR(10) NOT NULL,
    mime_type VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Índices
CREATE INDEX idx_languages_name ON languages(name);
CREATE INDEX idx_languages_extension ON languages(extension);
CREATE INDEX idx_languages_active ON languages(is_active);

-- Trigger para updated_at
CREATE TRIGGER update_languages_updated_at 
    BEFORE UPDATE ON languages 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();
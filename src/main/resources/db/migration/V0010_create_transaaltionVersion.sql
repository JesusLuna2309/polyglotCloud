-- Create translation_versions table
CREATE TABLE translation_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    translation_id UUID NOT NULL REFERENCES translations(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    translated_code TEXT NOT NULL,
    author_id UUID NOT NULL REFERENCES users(id),
    change_notes TEXT,
    is_current_version BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_translation_version UNIQUE (translation_id, version_number),
    CONSTRAINT chk_version_number_positive CHECK (version_number > 0)
);

-- Add current_version_number to translations table
ALTER TABLE translations
ADD COLUMN current_version_number INTEGER NOT NULL DEFAULT 1;

-- Indices for performance
CREATE INDEX idx_translation_versions_translation_id ON translation_versions(translation_id);
CREATE INDEX idx_translation_versions_author_id ON translation_versions(author_id);
CREATE INDEX idx_translation_versions_version_number ON translation_versions(version_number);
CREATE INDEX idx_translation_versions_current ON translation_versions(translation_id, is_current_version) WHERE is_current_version = true;
CREATE INDEX idx_translation_versions_created_at ON translation_versions(created_at);

-- Trigger para actualizar updated_at en translations cuando se crea una versión
CREATE OR REPLACE FUNCTION update_translation_on_version_insert()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE translations 
    SET updated_at = CURRENT_TIMESTAMP 
    WHERE id = NEW.translation_id;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER trigger_update_translation_on_version_insert
    AFTER INSERT ON translation_versions
    FOR EACH ROW
    EXECUTE FUNCTION update_translation_on_version_insert();
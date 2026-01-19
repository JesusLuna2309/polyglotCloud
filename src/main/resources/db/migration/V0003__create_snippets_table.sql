CREATE TABLE snippets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(255) NOT NULL UNIQUE,
    content TEXT NOT NULL,
    description TEXT,
    user_id UUID NOT NULL,
    language_id UUID NOT NULL,
    original_snippet_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    is_public BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign Keys
    CONSTRAINT fk_snippets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_snippets_language FOREIGN KEY (language_id) REFERENCES languages(id) ON DELETE RESTRICT,
    CONSTRAINT fk_snippets_original FOREIGN KEY (original_snippet_id) REFERENCES snippets(id) ON DELETE SET NULL
);

-- Índices para optimización
CREATE INDEX idx_snippets_user_id ON snippets(user_id);
CREATE INDEX idx_snippets_language_id ON snippets(language_id);
CREATE INDEX idx_snippets_original_snippet_id ON snippets(original_snippet_id);
CREATE INDEX idx_snippets_status ON snippets(status);
CREATE INDEX idx_snippets_public ON snippets(is_public);
CREATE INDEX idx_snippets_title ON snippets(title);
CREATE INDEX idx_snippets_created_at ON snippets(created_at);

-- Índice compuesto para búsquedas frecuentes
CREATE INDEX idx_snippets_user_status ON snippets(user_id, status);
CREATE INDEX idx_snippets_public_status ON snippets(is_public, status);

-- Trigger para updated_at
CREATE TRIGGER update_snippets_updated_at 
    BEFORE UPDATE ON snippets 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();
-- Add vote counting columns to translation_versions table
ALTER TABLE translation_versions 
ADD COLUMN upvotes_count INTEGER NOT NULL DEFAULT 0,
ADD COLUMN downvotes_count INTEGER NOT NULL DEFAULT 0,
ADD COLUMN total_score INTEGER NOT NULL DEFAULT 0;

-- Create translation_votes table
CREATE TABLE translation_votes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    translation_version_id UUID NOT NULL REFERENCES translation_versions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vote_type VARCHAR(20) NOT NULL CHECK (vote_type IN ('UPVOTE', 'DOWNVOTE')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_translation_vote_user_version UNIQUE (translation_version_id, user_id)
);

-- Indices for performance
CREATE INDEX idx_translation_votes_version_id ON translation_votes(translation_version_id);
CREATE INDEX idx_translation_votes_user_id ON translation_votes(user_id);
CREATE INDEX idx_translation_votes_vote_type ON translation_votes(vote_type);
CREATE INDEX idx_translation_votes_created_at ON translation_votes(created_at);

-- Index on translation_versions for voting queries
CREATE INDEX idx_translation_versions_score ON translation_versions(total_score DESC);
CREATE INDEX idx_translation_versions_upvotes ON translation_versions(upvotes_count DESC);

-- Function to update vote counts when votes are added/updated/deleted
CREATE OR REPLACE FUNCTION update_version_vote_counts()
RETURNS TRIGGER AS $$
BEGIN
    -- Update the translation_version vote counts
    UPDATE translation_versions 
    SET 
        upvotes_count = (
            SELECT COUNT(*) 
            FROM translation_votes 
            WHERE translation_version_id = COALESCE(NEW.translation_version_id, OLD.translation_version_id) 
            AND vote_type = 'UPVOTE'
        ),
        downvotes_count = (
            SELECT COUNT(*) 
            FROM translation_votes 
            WHERE translation_version_id = COALESCE(NEW.translation_version_id, OLD.translation_version_id) 
            AND vote_type = 'DOWNVOTE'
        ),
        total_score = (
            SELECT COALESCE(SUM(
                CASE 
                    WHEN vote_type = 'UPVOTE' THEN 1 
                    WHEN vote_type = 'DOWNVOTE' THEN -1 
                    ELSE 0 
                END
            ), 0)
            FROM translation_votes 
            WHERE translation_version_id = COALESCE(NEW.translation_version_id, OLD.translation_version_id)
        )
    WHERE id = COALESCE(NEW.translation_version_id, OLD.translation_version_id);
    
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- Trigger to automatically update vote counts
CREATE TRIGGER trigger_update_version_vote_counts
    AFTER INSERT OR UPDATE OR DELETE ON translation_votes
    FOR EACH ROW
    EXECUTE FUNCTION update_version_vote_counts();

-- Trigger para actualizar updated_at en votes
CREATE TRIGGER update_translation_votes_updated_at 
    BEFORE UPDATE ON translation_votes 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();
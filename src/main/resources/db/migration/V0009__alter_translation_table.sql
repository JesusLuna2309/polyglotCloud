-- Add moderation fields to translations table
ALTER TABLE translations 
ADD COLUMN reviewed_by UUID REFERENCES users(id),
ADD COLUMN review_notes TEXT,
ADD COLUMN reviewed_at TIMESTAMP;

-- Create index for performance
CREATE INDEX idx_translations_reviewed_by ON translations(reviewed_by);
CREATE INDEX idx_translations_status ON translations(status);

-- Add content_hash field for deduplication
ALTER TABLE translations 
ADD COLUMN content_hash VARCHAR(64) NOT NULL DEFAULT '';

-- Create index for fast hash lookups
CREATE INDEX idx_translations_content_hash ON translations(content_hash);

-- Create composite index for hash + status queries
CREATE INDEX idx_translations_hash_status ON translations(content_hash, status) 
WHERE status = 'COMPLETED';

-- Update existing translations with placeholder hash (will be recalculated)
UPDATE translations 
SET content_hash = 'legacy-' || id::text 
WHERE content_hash = '';
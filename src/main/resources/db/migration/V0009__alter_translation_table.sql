-- Add moderation fields to translations table
ALTER TABLE translations 
ADD COLUMN reviewed_by UUID REFERENCES users(id),
ADD COLUMN review_notes TEXT,
ADD COLUMN reviewed_at TIMESTAMP;

-- Create index for performance
CREATE INDEX idx_translations_reviewed_by ON translations(reviewed_by);
CREATE INDEX idx_translations_status ON translations(status);
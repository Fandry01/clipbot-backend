-- Ensure projects can store the normalized source URL used for reuse heuristics
ALTER TABLE project
    ADD COLUMN IF NOT EXISTS normalized_source_url VARCHAR(2048);

CREATE INDEX IF NOT EXISTS idx_project_normalized_source_url
    ON project (normalized_source_url);

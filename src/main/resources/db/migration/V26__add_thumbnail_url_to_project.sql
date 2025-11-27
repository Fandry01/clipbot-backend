-- Adds thumbnail_url to project to align with entity mapping
ALTER TABLE project
    ADD COLUMN IF NOT EXISTS thumbnail_url VARCHAR(2048);

-- Optional index if queries filter on thumbnail presence; safe no-op when absent
CREATE INDEX IF NOT EXISTS idx_project_thumbnail_url ON project(thumbnail_url);

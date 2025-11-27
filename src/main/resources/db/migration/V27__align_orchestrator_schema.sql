-- Aligns project and one-click orchestration schema with entity mappings and lookup patterns
-- 1) Normalize column type for response payload to text (idempotent when already text)
ALTER TABLE one_click_orchestration
    ALTER COLUMN response_payload TYPE TEXT;

-- 2) Ensure normalized_source_url can hold full values and is indexed with owner for reuse lookups
ALTER TABLE project
    ALTER COLUMN normalized_source_url TYPE VARCHAR(2048);

CREATE INDEX IF NOT EXISTS idx_project_owner_normalized_source_url
    ON project(owner_id, normalized_source_url);

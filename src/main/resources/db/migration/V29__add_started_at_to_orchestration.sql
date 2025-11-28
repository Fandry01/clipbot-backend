-- Add started_at to align one_click_orchestration table with entity
ALTER TABLE one_click_orchestration
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ;

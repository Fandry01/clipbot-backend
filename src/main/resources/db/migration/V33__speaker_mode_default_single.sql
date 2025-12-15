-- Set explicit default to SINGLE and backfill existing rows
ALTER TABLE media ALTER COLUMN speaker_mode SET DEFAULT 'SINGLE';
UPDATE media SET speaker_mode = 'SINGLE' WHERE speaker_mode IS NULL OR speaker_mode = 'AUTO';

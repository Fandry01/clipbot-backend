ALTER TABLE media
  ADD COLUMN IF NOT EXISTS speaker_mode varchar(16) NOT NULL DEFAULT 'AUTO';

ALTER TABLE media
  ADD COLUMN IF NOT EXISTS speaker_count_detected integer;
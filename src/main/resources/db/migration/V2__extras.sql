-- src/main/resources/db/migration/V2__extras.sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Transcript: snelle text search
CREATE INDEX IF NOT EXISTS transcript_text_trgm
  ON transcript USING GIN (text gin_trgm_ops);

-- Segment: handige indexen
CREATE INDEX IF NOT EXISTS idx_segment_media ON segment(media_id);
CREATE INDEX IF NOT EXISTS idx_segment_media_score ON segment(media_id, score);

-- Media: lijst per eigenaar
CREATE INDEX IF NOT EXISTS idx_media_owner_created ON media(owner_id, created_at);

-- Clip: lijst per media
CREATE INDEX IF NOT EXISTS idx_clip_media_created ON clip(media_id, created_at);

-- Asset: veelgebruikte filters
CREATE INDEX IF NOT EXISTS idx_asset_owner_created ON asset(owner_id, created_at);
CREATE INDEX IF NOT EXISTS idx_asset_clip ON asset(related_clip_id);

-- Job: queue pick
CREATE INDEX IF NOT EXISTS idx_job_status_created ON job(status, created_at);
CREATE INDEX IF NOT EXISTS idx_job_media ON job(media_id);

-- Segment: extra veiligheidscheck (als die nog niet bestaat)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'segment_end_gt_start'
  ) THEN
    ALTER TABLE segment
      ADD CONSTRAINT segment_end_gt_start CHECK (end_ms > start_ms);
  END IF;
END$$;

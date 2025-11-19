ALTER TABLE clip
    ADD COLUMN IF NOT EXISTS profile_hash TEXT;

UPDATE clip SET profile_hash = '' WHERE profile_hash IS NULL;

ALTER TABLE clip
    ALTER COLUMN profile_hash SET NOT NULL;

ALTER TABLE clip
    ALTER COLUMN profile_hash SET DEFAULT '';

ALTER TABLE clip
    ADD COLUMN IF NOT EXISTS score NUMERIC(6,3);

CREATE UNIQUE INDEX IF NOT EXISTS ux_clip_media_range_profile
    ON clip(media_id, start_ms, end_ms, profile_hash);

CREATE TABLE IF NOT EXISTS render_stats (
    id UUID PRIMARY KEY,
    kind TEXT NOT NULL,
    avg_ms BIGINT NOT NULL DEFAULT 0,
    count BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_render_stats_kind ON render_stats(kind);

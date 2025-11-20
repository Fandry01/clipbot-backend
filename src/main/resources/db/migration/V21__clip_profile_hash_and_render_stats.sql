-- V21__clip_profile_hash_and_render_stats.sql

-- 1) Nieuwe kolommen
ALTER TABLE clip
    ADD COLUMN IF NOT EXISTS profile_hash TEXT;

UPDATE clip SET profile_hash = '' WHERE profile_hash IS NULL;

ALTER TABLE clip
    ALTER COLUMN profile_hash SET NOT NULL;

ALTER TABLE clip
    ALTER COLUMN profile_hash SET DEFAULT '';

ALTER TABLE clip
    ADD COLUMN IF NOT EXISTS score NUMERIC(6,3);

-- 2a) Repoint FK's van duplicates -> keeper (CTE per statement!)
WITH grp AS (
    SELECT
        c.id,
        c.media_id,
        c.start_ms,
        c.end_ms,
        c.profile_hash,
        c.created_at,
        ROW_NUMBER() OVER (
            PARTITION BY c.media_id, c.start_ms, c.end_ms, c.profile_hash
            ORDER BY c.created_at NULLS LAST, c.id
        ) AS rn,
        FIRST_VALUE(c.id) OVER (
            PARTITION BY c.media_id, c.start_ms, c.end_ms, c.profile_hash
            ORDER BY c.created_at NULLS LAST, c.id
        ) AS keep_id
    FROM clip c
),
dups AS (
    SELECT id AS old_id, keep_id
    FROM grp
    WHERE rn > 1 AND id <> keep_id
)
UPDATE asset a
SET related_clip_id = d.keep_id
FROM dups d
WHERE a.related_clip_id = d.old_id;

-- 2b) Verwijder duplicates (CTE opnieuw definiëren)
WITH grp AS (
    SELECT
        c.id,
        c.media_id,
        c.start_ms,
        c.end_ms,
        c.profile_hash,
        c.created_at,
        ROW_NUMBER() OVER (
            PARTITION BY c.media_id, c.start_ms, c.end_ms, c.profile_hash
            ORDER BY c.created_at NULLS LAST, c.id
        ) AS rn,
        FIRST_VALUE(c.id) OVER (
            PARTITION BY c.media_id, c.start_ms, c.end_ms, c.profile_hash
            ORDER BY c.created_at NULLS LAST, c.id
        ) AS keep_id
    FROM clip c
),
dups AS (
    SELECT id AS old_id
    FROM grp
    WHERE rn > 1 AND id <> keep_id
)
DELETE FROM clip c
USING dups d
WHERE c.id = d.old_id;

-- 3) Unieke index afdwingen
CREATE UNIQUE INDEX IF NOT EXISTS ux_clip_media_range_profile
    ON clip(media_id, start_ms, end_ms, profile_hash);

-- 4) Render stats tabel
CREATE TABLE IF NOT EXISTS render_stats (
    id UUID PRIMARY KEY,
    kind TEXT NOT NULL,
    avg_ms BIGINT NOT NULL DEFAULT 0,
    count BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_render_stats_kind ON render_stats(kind);

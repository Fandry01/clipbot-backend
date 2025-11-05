-- =========================
-- CLIP
-- =========================
-- default op status
ALTER TABLE clip
    ALTER COLUMN status SET DEFAULT 'QUEUED';

-- (optioneel) eventueele nulls rechtzetten vóór NOT NULL aanscherping
UPDATE clip SET status = 'QUEUED' WHERE status IS NULL;

-- =========================
-- JOB
-- =========================
-- default op status
ALTER TABLE job
    ALTER COLUMN status SET DEFAULT 'QUEUED';

UPDATE job SET status = 'QUEUED' WHERE status IS NULL;

-- dedup_key toevoegen (als nog niet bestaat)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='job' AND column_name='dedup_key'
    ) THEN
        ALTER TABLE job ADD COLUMN dedup_key varchar(255);
    END IF;
END$$;

-- result → jsonb
ALTER TABLE job
    ALTER COLUMN result TYPE jsonb USING
        CASE
            WHEN result IS NULL THEN '{}'::jsonb
            WHEN pg_typeof(result)::text = 'jsonb' THEN result::jsonb
            ELSE result::jsonb
        END;

-- payload (als die nog text/json is) ook naar jsonb voor consistentie
ALTER TABLE job
    ALTER COLUMN payload TYPE jsonb USING
        CASE
            WHEN payload IS NULL THEN '{}'::jsonb
            WHEN pg_typeof(payload)::text = 'jsonb' THEN payload::jsonb
            ELSE payload::jsonb
        END;

-- updated_at zeker stellen (NOT NULL + default now()).
-- NB: @UpdateTimestamp zet hem bij writes; default helpt bij inserts.
ALTER TABLE job
    ALTER COLUMN updated_at SET DEFAULT now();

UPDATE job SET updated_at = now() WHERE updated_at IS NULL;

ALTER TABLE job
    ALTER COLUMN updated_at SET NOT NULL;

-- =========================
-- MEDIA
-- =========================
-- created_at van DATE → TIMESTAMPTZ
ALTER TABLE media
    ALTER COLUMN created_at TYPE timestamptz USING
        CASE
            WHEN pg_typeof(created_at)::text = 'timestamp with time zone' THEN created_at
            ELSE created_at::timestamptz
        END;

-- =========================
-- PROJECT
-- =========================
-- template_id nullable maken
ALTER TABLE project
    ALTER COLUMN template_id DROP NOT NULL;

-- version kolom toevoegen met default 0
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='project' AND column_name='version'
    ) THEN
        ALTER TABLE project ADD COLUMN version bigint NOT NULL DEFAULT 0;
    END IF;
END$$;

-- =========================
-- PROJECT_MEDIA (koppeltabel)
-- =========================
-- Maak de tabel als hij nog niet bestaat (composiete PK)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='project_media') THEN
        CREATE TABLE project_media (
            project_id uuid NOT NULL,
            media_id   uuid NOT NULL,
            created_at timestamptz NOT NULL DEFAULT now(),
            CONSTRAINT pk_project_media PRIMARY KEY (project_id, media_id),
            CONSTRAINT fk_project_media_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
            CONSTRAINT fk_project_media_media   FOREIGN KEY (media_id)   REFERENCES media(id)   ON DELETE CASCADE
        );
        CREATE INDEX idx_pm_project ON project_media(project_id);
        CREATE INDEX idx_pm_media   ON project_media(media_id);
    END IF;
END$$;

-- =========================
-- SEGMENT
-- =========================
-- nulls opvullen vóór NOT NULL aanscherpen
UPDATE segment SET start_ms = 0 WHERE start_ms IS NULL;
UPDATE segment SET end_ms   = GREATEST(COALESCE(end_ms,0), start_ms + 1) WHERE end_ms IS NULL OR end_ms <= start_ms;

ALTER TABLE segment
    ALTER COLUMN start_ms SET NOT NULL,
    ALTER COLUMN end_ms   SET NOT NULL;

-- (optioneel) type expliciet naar BIGINT als dat nog niet zo is
ALTER TABLE segment
    ALTER COLUMN start_ms TYPE bigint,
    ALTER COLUMN end_ms   TYPE bigint;

-- =========================
-- TEMPLATE
-- =========================
-- owner relation: owner_id moet bestaan en FK krijgen
-- Als de kolom nog niet bestaat: toevoegen
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='template' AND column_name='owner_id'
    ) THEN
        ALTER TABLE template ADD COLUMN owner_id uuid;
    END IF;
END$$;

-- zet not null (vul evt. eerst met een bestaande account-id in losse migratie indien nodig)
ALTER TABLE template
    ALTER COLUMN owner_id SET NOT NULL;

-- FK toevoegen (als nog niet bestaat)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name='template' AND constraint_name='fk_template_owner'
    ) THEN
        ALTER TABLE template
            ADD CONSTRAINT fk_template_owner FOREIGN KEY (owner_id) REFERENCES account(id) ON DELETE RESTRICT;
    END IF;
END$$;

-- version kolom toevoegen met default 0
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='template' AND column_name='version'
    ) THEN
        ALTER TABLE template ADD COLUMN version bigint NOT NULL DEFAULT 0;
    END IF;
END$$;

-- =========================
-- TRANSCRIPT
-- =========================
-- words → jsonb
ALTER TABLE transcript
    ALTER COLUMN words TYPE jsonb USING
        CASE
            WHEN words IS NULL THEN jsonb_build_object('schema','v1','items','[]'::jsonb)
            WHEN pg_typeof(words)::text = 'jsonb' THEN words::jsonb
            ELSE words::jsonb
        END;

-- (optioneel) index op media + created_at desc voor “latest”
CREATE INDEX IF NOT EXISTS idx_transcript_media_created ON transcript(media_id, created_at DESC);

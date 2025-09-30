-- V3: migrate enum columns from ORDINAL (smallint) -> STRING (varchar)

-- JOB.status
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='job' AND column_name='status'
      AND data_type IN ('smallint','integer')
  ) THEN
    ALTER TABLE job ADD COLUMN status_txt TEXT;
    UPDATE job SET status_txt = CASE (status::int)
      WHEN 0 THEN 'QUEUED'
      WHEN 1 THEN 'RUNNING'
      WHEN 2 THEN 'DONE'
      WHEN 3 THEN 'ERROR'
      ELSE 'QUEUED' END;
    ALTER TABLE job DROP COLUMN status;
    ALTER TABLE job RENAME COLUMN status_txt TO status;
    ALTER TABLE job ALTER COLUMN status TYPE VARCHAR(32);
  END IF;
END$$;

-- JOB.type
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='job' AND column_name='type'
      AND data_type IN ('smallint','integer')
  ) THEN
    ALTER TABLE job ADD COLUMN type_txt TEXT;
    UPDATE job SET type_txt = CASE (type::int)
      WHEN 0 THEN 'TRANSCRIBE'
      WHEN 1 THEN 'DETECT'
      WHEN 2 THEN 'CLIP'
      ELSE 'TRANSCRIBE' END;
    ALTER TABLE job DROP COLUMN type;
    ALTER TABLE job RENAME COLUMN type_txt TO type;
    ALTER TABLE job ALTER COLUMN type TYPE VARCHAR(32);
  END IF;
END$$;

-- MEDIA.status
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='media' AND column_name='status'
      AND data_type IN ('smallint','integer')
  ) THEN
    ALTER TABLE media ADD COLUMN status_txt TEXT;
    UPDATE media SET status_txt = CASE (status::int)
      WHEN 0 THEN 'UPLOADED'
      WHEN 1 THEN 'PROCESSING'
      WHEN 2 THEN 'READY'
      ELSE 'UPLOADED' END;
    ALTER TABLE media DROP COLUMN status;
    ALTER TABLE media RENAME COLUMN status_txt TO status;
    ALTER TABLE media ALTER COLUMN status TYPE VARCHAR(32);
  END IF;
END$$;

-- CLIP.status
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='clip' AND column_name='status'
      AND data_type IN ('smallint','integer')
  ) THEN
    ALTER TABLE clip ADD COLUMN status_txt TEXT;
    UPDATE clip SET status_txt = CASE (status::int)
      WHEN 0 THEN 'QUEUED'
      WHEN 1 THEN 'RENDERING'
      WHEN 2 THEN 'READY'
      ELSE 'QUEUED' END;
    ALTER TABLE clip DROP COLUMN status;
    ALTER TABLE clip RENAME COLUMN status_txt TO status;
    ALTER TABLE clip ALTER COLUMN status TYPE VARCHAR(32);
  END IF;
END$$;

-- ASSET.kind  (sla dit blok over als 'kind' geen enum is)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='asset' AND column_name='kind'
      AND data_type IN ('smallint','integer')
  ) THEN
    ALTER TABLE asset ADD COLUMN kind_txt TEXT;
    UPDATE asset SET kind_txt = CASE (kind::int)
      WHEN 0 THEN 'MEDIA_RAW'
      WHEN 1 THEN 'CLIP_MP4'
      WHEN 2 THEN 'THUMBNAIL'
      WHEN 3 THEN 'SUB_SRT'
      WHEN 4 THEN 'SUB_VTT'
      ELSE 'MEDIA_RAW' END;
    ALTER TABLE asset DROP COLUMN kind;
    ALTER TABLE asset RENAME COLUMN kind_txt TO kind;
    ALTER TABLE asset ALTER COLUMN kind TYPE VARCHAR(32);
  END IF;
END$$;
-- V3: migrate enum columns from ORDINAL (smallint) -> STRING (varchar)

-- JOB.status
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='job' AND column_name='status'
      AND data_type IN ('smallint','integer')
  ) THEN
    ALTER TABLE job ADD COLUMN status_txt TEXT;
    UPDATE job SET status_txt = CASE (status::int)
      WHEN 0 THEN 'QUEUED'
      WHEN 1 THEN 'RUNNING'
      WHEN 2 THEN 'DONE'
      WHEN 3 THEN 'ERROR'
      ELSE 'QUEUED' END;
    ALTER TABLE job DROP COLUMN status;
    ALTER TABLE job RENAME COLUMN status_txt TO status;
    ALTER TABLE job ALTER COLUMN status TYPE VARCHAR(32);
  END IF;
END$$;

-- JOB.type
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='job' AND column_name='type'
      AND data_type IN ('smallint','integer')
  ) THEN
    ALTER TABLE job ADD COLUMN type_txt TEXT;
    UPDATE job SET type_txt = CASE (type::int)
      WHEN 0 THEN 'TRANSCRIBE'
      WHEN 1 THEN 'DETECT'
      WHEN 2 THEN 'CLIP'
      ELSE 'TRANSCRIBE' END;
    ALTER TABLE job DROP COLUMN type;
    ALTER TABLE job RENAME COLUMN type_txt TO type;
    ALTER TABLE job ALTER COLUMN type TYPE VARCHAR(32);
  END IF;
END$$;

-- MEDIA.status
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='media' AND column_name='status'
      AND data_type IN ('smallint','integer')
  ) THEN
    ALTER TABLE media ADD COLUMN status_txt TEXT;
    UPDATE media SET status_txt = CASE (status::int)
      WHEN 0 THEN 'UPLOADED'
      WHEN 1 THEN 'PROCESSING'
      WHEN 2 THEN 'READY'
      ELSE 'UPLOADED' END;
    ALTER TABLE media DROP COLUMN status;
    ALTER TABLE media RENAME COLUMN status_txt TO status;
    ALTER TABLE media ALTER COLUMN status TYPE VARCHAR(32);
  END IF;
END$$;

-- CLIP.status
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='clip' AND column_name='status'
      AND data_type IN ('smallint','integer')
  ) THEN
    ALTER TABLE clip ADD COLUMN status_txt TEXT;
    UPDATE clip SET status_txt = CASE (status::int)
      WHEN 0 THEN 'QUEUED'
      WHEN 1 THEN 'RENDERING'
      WHEN 2 THEN 'READY'
      ELSE 'QUEUED' END;
    ALTER TABLE clip DROP COLUMN status;
    ALTER TABLE clip RENAME COLUMN status_txt TO status;
    ALTER TABLE clip ALTER COLUMN status TYPE VARCHAR(32);
  END IF;
END$$;

-- ASSET.kind
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='asset' AND column_name='kind'
      AND data_type IN ('smallint','integer')
  ) THEN
    ALTER TABLE asset ADD COLUMN kind_txt TEXT;
    UPDATE asset SET kind_txt = CASE (kind::int)
      WHEN 0 THEN 'MEDIA_RAW'
      WHEN 1 THEN 'CLIP_MP4'
      WHEN 2 THEN 'THUMBNAIL'
      WHEN 3 THEN 'SUB_SRT'
      WHEN 4 THEN 'SUB_VTT'
      ELSE 'MEDIA_RAW' END;
    ALTER TABLE asset DROP COLUMN kind;
    ALTER TABLE asset RENAME COLUMN kind_txt TO kind;
    ALTER TABLE asset ALTER COLUMN kind TYPE VARCHAR(32);
  END IF;
END$$;

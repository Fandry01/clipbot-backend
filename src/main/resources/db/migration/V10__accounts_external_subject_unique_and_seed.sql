-- V10__accounts_external_subject_unique_and_seed.sql


-- 1) dedupe (safe-no-op als er geen dups zijn)
WITH dups AS (
  SELECT ctid
  FROM (
    SELECT ctid,
           row_number() OVER (PARTITION BY external_subject ORDER BY created_at NULLS LAST, id) AS rn
    FROM account
    WHERE external_subject IS NOT NULL
  ) t
  WHERE rn > 1
)
DELETE FROM account a
USING dups d
WHERE a.ctid = d.ctid;

-- 2) unieke constraint
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM   pg_constraint
    WHERE  conname = 'uk_accounts_external_subject'
  ) THEN
    ALTER TABLE account
      ADD CONSTRAINT uk_accounts_external_subject UNIQUE (external_subject);
  END IF;
END$$;

-- 3) seed
INSERT INTO account (id, external_subject, display_name, created_at, version)
VALUES (gen_random_uuid(), 'demo-user-1', 'Demo User', NOW(), 0)
ON CONFLICT (external_subject) DO NOTHING;

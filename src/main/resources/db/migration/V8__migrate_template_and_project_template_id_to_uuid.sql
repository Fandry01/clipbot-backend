-- V9__create_template_and_migrate_project_template_id.sql
-- 1) Maak 'template' (uuid PK) als hij nog niet bestaat
-- 2) Migreer 'project.template_id' naar uuid en leg FK vast

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1) TEMPLATE aanmaken/aanvullen
CREATE TABLE IF NOT EXISTS template (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id    uuid         NOT NULL,
  name        varchar(200) NOT NULL,
  json_config text         NOT NULL,
  created_at  timestamptz  NOT NULL,
  updated_at  timestamptz  NOT NULL
);

-- Zorg dat alle kolommen bestaan (idempotent)
ALTER TABLE template
  ADD COLUMN IF NOT EXISTS owner_id    uuid         NOT NULL,
  ADD COLUMN IF NOT EXISTS name        varchar(200) NOT NULL,
  ADD COLUMN IF NOT EXISTS json_config text         NOT NULL,
  ADD COLUMN IF NOT EXISTS created_at  timestamptz  NOT NULL,
  ADD COLUMN IF NOT EXISTS updated_at  timestamptz  NOT NULL;

-- 2) PROJECT: zorg dat template_id bestaat
ALTER TABLE project
  ADD COLUMN IF NOT EXISTS template_id uuid;

-- Als template_id GEEN uuid is, cast 'm met lege strings -> NULL
DO $body$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_name = 'project'
      AND column_name = 'template_id'
      AND data_type <> 'uuid'
  ) THEN
    -- Lege strings naar NULL (gebruik andere dollar-tag om quotes gedoe te vermijden)
    EXECUTE $upd$UPDATE project SET template_id = NULL WHERE template_id = ''$upd$;

    -- Typecast naar uuid met USING
    EXECUTE $alt$
      ALTER TABLE project
      ALTER COLUMN template_id TYPE uuid
      USING NULLIF(template_id::text, '')::uuid
    $alt$;
  END IF;
END
$body$;

-- FK opnieuw leggen (nullable toegestaan) + index
ALTER TABLE project
  DROP CONSTRAINT IF EXISTS fk_project_template,
  ADD CONSTRAINT fk_project_template
    FOREIGN KEY (template_id) REFERENCES template(id);

CREATE INDEX IF NOT EXISTS idx_project_template_id ON project(template_id);

-- V9__template_json_config_to_jsonb.sql
ALTER TABLE template
  ALTER COLUMN json_config TYPE jsonb
  USING json_config::jsonb;

ALTER TABLE one_click_orchestration
    ADD COLUMN IF NOT EXISTS request_fingerprint VARCHAR(512);

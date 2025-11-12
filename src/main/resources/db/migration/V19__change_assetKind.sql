ALTER TABLE asset DROP CONSTRAINT IF EXISTS asset_kind_check;

ALTER TABLE asset
    ADD CONSTRAINT asset_kind_check
        CHECK (kind IN ('MEDIA_RAW','MP4','WEBM','THUMBNAIL','SUB_SRT','SUB_VTT'))
    NOT VALID;

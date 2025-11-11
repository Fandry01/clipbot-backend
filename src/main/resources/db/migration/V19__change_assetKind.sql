ALTER TABLE asset DROP CONSTRAINT asset_kind_check;

ALTER TABLE asset
ADD CONSTRAINT asset_kind_check
CHECK (kind IN (
  'MEDIA_RAW',      -- rauwe input (als je die gebruikt)
  'MP4',            -- gerenderde clip
  'THUMBNAIL',      -- jpg/png
  'SUB_SRT',        -- srt
  'SUB_VTT'         -- vtt
));
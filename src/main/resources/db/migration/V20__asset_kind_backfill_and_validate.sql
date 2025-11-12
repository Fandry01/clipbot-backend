UPDATE asset SET kind = 'MP4'  WHERE kind = 'CLIP_MP4';
UPDATE asset SET kind = 'WEBM' WHERE kind = 'CLIP_WEBM';

-- als alles nu in de set valt:
ALTER TABLE asset VALIDATE CONSTRAINT asset_kind_check;

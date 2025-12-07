INSERT INTO plan_limits (plan, max_renders_day, max_renders_month, allow_1080p, allow_4k, watermark)
VALUES ('TRIAL', 50, 60, false, false, true)
ON CONFLICT (plan) DO UPDATE SET max_renders_day = EXCLUDED.max_renders_day;

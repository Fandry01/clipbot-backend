-- 1. Account: plan, trial, created_at fallback
ALTER TABLE account
  ADD COLUMN IF NOT EXISTS plan_tier TEXT NOT NULL DEFAULT 'TRIAL',
  ADD COLUMN IF NOT EXISTS trial_ends_at TIMESTAMPTZ;

-- 2. Usage counters (dag/maand) per account
CREATE TABLE IF NOT EXISTS usage_counters (
  id UUID PRIMARY KEY,
  account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  date_key DATE NOT NULL,
  month_key DATE NOT NULL,
  renders_today INT NOT NULL DEFAULT 0,
  renders_month INT NOT NULL DEFAULT 0,
  CONSTRAINT ux_usage_counters UNIQUE (account_id, date_key)
);

-- 3. Plan config (optioneel seed via app-code)
CREATE TABLE IF NOT EXISTS plan_limits (
  plan TEXT PRIMARY KEY,
  max_renders_day INT NOT NULL,
  max_renders_month INT NOT NULL,
  allow_1080p BOOLEAN NOT NULL,
  allow_4k BOOLEAN NOT NULL,
  watermark BOOLEAN NOT NULL
);


-- Seed defaults (kan ook via data-init in code)
INSERT INTO plan_limits(plan, max_renders_day, max_renders_month, allow_1080p, allow_4k, watermark) VALUES
('TRIAL',   10,   60, false, false, true)
ON CONFLICT (plan) DO NOTHING;

INSERT INTO plan_limits(plan, max_renders_day, max_renders_month, allow_1080p, allow_4k, watermark) VALUES
('STARTER', 40,  400, true,  false, false)
ON CONFLICT (plan) DO NOTHING;

INSERT INTO plan_limits(plan, max_renders_day, max_renders_month, allow_1080p, allow_4k, watermark) VALUES
('PRO',    120, 1200, true,  false, false)
ON CONFLICT (plan) DO NOTHING;

-- Snellere maand-queries
CREATE INDEX IF NOT EXISTS ix_usage_month ON usage_counters(account_id, month_key);
-- Zorg dat plan_tier nooit NULL is (backfill safety)
UPDATE account SET plan_tier = 'TRIAL' WHERE plan_tier IS NULL;

-- Account: admin-flag
ALTER TABLE account
  ADD COLUMN IF NOT EXISTS is_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- Usage: maand-query sneller (als nog niet gedaan)
CREATE INDEX IF NOT EXISTS ix_usage_month ON usage_counters(account_id, month_key);

-- Safety: plan_tier nooit NULL
UPDATE account SET plan_tier = 'TRIAL' WHERE plan_tier IS NULL;

CREATE TABLE IF NOT EXISTS one_click_orchestration (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_external_subject VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    response_payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT uk_orchestration_owner_key UNIQUE (owner_external_subject, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_orchestration_owner ON one_click_orchestration(owner_external_subject, created_at);

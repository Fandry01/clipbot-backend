CREATE TABLE IF NOT EXISTS project (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    template_id VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_project_owner_created ON project(owner_id, created_at);

CREATE TABLE IF NOT EXISTS project_media (
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    media_id UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (project_id, media_id)
);

CREATE INDEX IF NOT EXISTS idx_project_media_project ON project_media(project_id, created_at);
CREATE INDEX IF NOT EXISTS idx_project_media_media ON project_media(media_id);

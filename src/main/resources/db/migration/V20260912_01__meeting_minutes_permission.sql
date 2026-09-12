CREATE TABLE IF NOT EXISTS meeting_minutes (
    id BIGSERIAL PRIMARY KEY,
    meeting_id BIGINT NOT NULL UNIQUE REFERENCES meetings(id),
    content TEXT NOT NULL,
    approved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NULL
);

INSERT INTO permissions (name, description, created_at, updated_at) VALUES
    ('MEETING_MINUTES_MANAGE', 'Create and update meeting minutes', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'MEETING_MINUTES_MANAGE'
WHERE r.name IN ('GROUP_ADMIN', 'GROUP_CHAIRMAN', 'CHAIRPERSON', 'SECRETARY')
ON CONFLICT DO NOTHING;

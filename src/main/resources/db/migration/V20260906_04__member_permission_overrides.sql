-- Explicit grants are group-member scoped. They supplement role permissions and
-- let a chairman/admin delegate one capability without creating a new role.
CREATE TABLE IF NOT EXISTS member_permissions (
    id BIGSERIAL PRIMARY KEY,
    group_member_id BIGINT NOT NULL REFERENCES group_members(id),
    permission_id BIGINT NOT NULL REFERENCES permissions(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_member_permission UNIQUE (group_member_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_member_permissions_group_member
    ON member_permissions (group_member_id);

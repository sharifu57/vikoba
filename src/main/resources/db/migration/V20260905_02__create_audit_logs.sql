CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    user_id BIGINT,
    group_id BIGINT,
    action VARCHAR(20) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id BIGINT,
    ip_address VARCHAR(50),
    description TEXT,
    old_values TEXT,
    new_values TEXT,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_audit_group FOREIGN KEY (group_id) REFERENCES vikoba_groups(id)
);

CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_date ON audit_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_group_date ON audit_logs(group_id, created_at DESC);

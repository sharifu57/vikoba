CREATE TABLE IF NOT EXISTS workflow_nodes (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES vikoba_groups(id),
    action_key VARCHAR(80) NOT NULL,
    label VARCHAR(150) NOT NULL,
    required_role VARCHAR(50),
    required_permission VARCHAR(100),
    step_order INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_workflow_group_action_step UNIQUE (group_id, action_key, step_order)
);

CREATE INDEX IF NOT EXISTS idx_workflow_nodes_group_action
    ON workflow_nodes (group_id, action_key, active, step_order);

-- Default configurable nodes for existing groups. Administrators can add or replace
-- nodes through /api/workflows/group/{groupId}/nodes.
INSERT INTO workflow_nodes (group_id, action_key, label, required_role, required_permission, step_order)
SELECT g.id, 'SHARE_PURCHASE_PROOF', 'Review manual share purchase proof', 'ACCOUNTANT', 'SHARE_PURCHASE_APPROVE', 1
FROM vikoba_groups g
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_nodes w WHERE w.group_id = g.id AND w.action_key = 'SHARE_PURCHASE_PROOF'
);

-- Group roles are group-scoped through member_roles. GROUP_ADMIN is the break-glass
-- full-access role; the chairman and accountant receive their access via permissions.
INSERT INTO roles (name, description, created_at, updated_at) VALUES
 ('GROUP_ADMIN', 'Full group administration access', NOW(), NOW()),
 ('GROUP_CHAIRMAN', 'Mwenyekiti / group governance leadership', NOW(), NOW()),
 ('ACCOUNTANT', 'Financial records, payments and reconciliation', NOW(), NOW()),
 ('CHAIRPERSON', 'Legacy chairperson role', NOW(), NOW()),
 ('VICE_CHAIRPERSON', 'Legacy vice-chairperson role', NOW(), NOW()),
 ('SECRETARY', 'Records and meetings administration', NOW(), NOW()),
 ('TREASURER', 'Cash collection and contribution administration', NOW(), NOW()),
 ('LOAN_OFFICER', 'Loan application administration', NOW(), NOW()),
 ('AUDITOR', 'Read-only financial and audit access', NOW(), NOW()),
 ('MEMBER', 'Limited member self-service access', NOW(), NOW())
ON CONFLICT (name) DO UPDATE SET description = EXCLUDED.description, updated_at = NOW();

INSERT INTO permissions (name, description, created_at, updated_at) VALUES
 ('MEMBER_VIEW', 'View group members', NOW(), NOW()),
 ('MEMBER_MANAGE', 'Create and manage members', NOW(), NOW()),
 ('MEETING_MANAGE', 'Manage meetings and attendance', NOW(), NOW()),
 ('CONTRIBUTION_MANAGE', 'Record contributions and payments', NOW(), NOW()),
 ('SHARE_MANAGE', 'Manage shares', NOW(), NOW()),
 ('LOAN_MANAGE', 'Manage loans', NOW(), NOW()),
 ('FINE_MANAGE', 'Issue, waive and collect fines', NOW(), NOW()),
 ('DIVIDEND_MANAGE', 'Generate and approve dividends', NOW(), NOW()),
 ('REPORT_VIEW', 'View reports and dashboards', NOW(), NOW()),
 ('USER_ROLE_MANAGE', 'Assign users, roles and permissions', NOW(), NOW()),
 ('GROUP_MANAGE', 'Manage group settings and governance', NOW(), NOW()),
 ('PAYMENT_VIEW', 'View received payments and proof submissions', NOW(), NOW()),
 ('PAYMENT_APPROVE', 'Approve or reject payment proofs', NOW(), NOW()),
 ('SHARE_PURCHASE_APPROVE', 'Approve manual share purchase proofs', NOW(), NOW()),
 ('WORKFLOW_MANAGE', 'Configure group workflow nodes', NOW(), NOW()),
 ('AUDIT_VIEW', 'View group audit records', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'GROUP_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN
 ('GROUP_MANAGE', 'MEMBER_VIEW', 'MEMBER_MANAGE', 'MEETING_MANAGE', 'REPORT_VIEW', 'AUDIT_VIEW', 'USER_ROLE_MANAGE', 'WORKFLOW_MANAGE')
WHERE r.name = 'GROUP_CHAIRMAN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN
 ('MEMBER_VIEW', 'CONTRIBUTION_MANAGE', 'SHARE_MANAGE', 'DIVIDEND_MANAGE', 'REPORT_VIEW', 'AUDIT_VIEW', 'PAYMENT_VIEW', 'PAYMENT_APPROVE', 'SHARE_PURCHASE_APPROVE')
WHERE r.name = 'ACCOUNTANT'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN ('MEMBER_VIEW', 'CONTRIBUTION_MANAGE', 'SHARE_MANAGE', 'DIVIDEND_MANAGE', 'REPORT_VIEW', 'PAYMENT_VIEW', 'PAYMENT_APPROVE', 'SHARE_PURCHASE_APPROVE')
WHERE r.name = 'TREASURER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN ('MEMBER_VIEW', 'LOAN_MANAGE', 'REPORT_VIEW')
WHERE r.name = 'LOAN_OFFICER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN ('MEMBER_VIEW', 'MEETING_MANAGE', 'REPORT_VIEW')
WHERE r.name IN ('SECRETARY', 'CHAIRPERSON', 'VICE_CHAIRPERSON')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN ('MEMBER_VIEW', 'REPORT_VIEW', 'AUDIT_VIEW')
WHERE r.name = 'AUDITOR'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN ('MEMBER_VIEW', 'REPORT_VIEW')
WHERE r.name = 'MEMBER'
ON CONFLICT DO NOTHING;

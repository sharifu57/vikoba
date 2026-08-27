-- Idempotent system roles and permissions. Run in DBeaver after the role_permissions
-- table has been created by Hibernate (ddl-auto=update).
INSERT INTO roles (name, description, created_at, updated_at) VALUES
 ('GROUP_ADMIN','Full administration of a VIKOBA group',NOW(),NOW()),
 ('TREASURER','Payments, contributions and dividend administration',NOW(),NOW()),
 ('LOAN_OFFICER','Loan applications and repayments',NOW(),NOW()),
 ('SECRETARY','Members, meetings and attendance',NOW(),NOW()),
 ('AUDITOR','Read-only financial and audit access',NOW(),NOW()),
 ('MEMBER','Standard member access',NOW(),NOW())
ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions (
    name,
    description,
    created_at,
    updated_at
)
VALUES
    ('MEMBER_VIEW', 'View group members', NOW(), NOW()),
    ('MEMBER_MANAGE', 'Create and manage members', NOW(), NOW()),
    ('MEETING_MANAGE', 'Manage meetings and attendance', NOW(), NOW()),
    ('CONTRIBUTION_MANAGE', 'Record contributions and payments', NOW(), NOW()),
    ('SHARE_MANAGE', 'Manage shares', NOW(), NOW()),
    ('LOAN_MANAGE', 'Manage loans', NOW(), NOW()),
    ('FINE_MANAGE', 'Issue, waive and collect fines', NOW(), NOW()),
    ('DIVIDEND_MANAGE', 'Generate and approve dividends', NOW(), NOW()),
    ('REPORT_VIEW', 'View reports and dashboards', NOW(), NOW()),
    ('USER_ROLE_MANAGE', 'Assign users, roles and permissions', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;


INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name='GROUP_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN ('MEMBER_VIEW','CONTRIBUTION_MANAGE','SHARE_MANAGE','DIVIDEND_MANAGE','REPORT_VIEW')
WHERE r.name='TREASURER' ON CONFLICT DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN ('MEMBER_VIEW','LOAN_MANAGE','REPORT_VIEW')
WHERE r.name='LOAN_OFFICER' ON CONFLICT DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN ('MEMBER_VIEW','MEETING_MANAGE','REPORT_VIEW')
WHERE r.name='SECRETARY' ON CONFLICT DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN ('MEMBER_VIEW','REPORT_VIEW')
WHERE r.name='AUDITOR' ON CONFLICT DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN ('MEMBER_VIEW','REPORT_VIEW')
WHERE r.name='MEMBER' ON CONFLICT DO NOTHING;

-- Make the user linked to the earliest member in every group a global GROUP_ADMIN.
INSERT INTO user_roles (
    user_id,
    role_id,
    created_at,
    updated_at
)
SELECT DISTINCT
    u.id,
    r.id,
    NOW(),
    NOW()
FROM users u
JOIN members m
    ON m.id = u.member_id
JOIN group_members gm
    ON gm.member_id = m.id
JOIN roles r
    ON r.name = 'GROUP_ADMIN'
WHERE gm.id = (
    SELECT MIN(gm2.id)
    FROM group_members gm2
    WHERE gm2.group_id = gm.group_id
)
ON CONFLICT (user_id, role_id) DO NOTHING;

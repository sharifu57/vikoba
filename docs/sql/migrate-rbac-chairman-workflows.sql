-- Add the group leadership roles. GROUP_ADMIN remains the full-access role;
-- GROUP_CHAIRMAN (Mwenyekiti) is a separate, permission-based governance role.
INSERT INTO roles (name, description, created_at, updated_at)
VALUES
 ('GROUP_ADMIN', 'Full group administration access', NOW(), NOW()),
 ('GROUP_CHAIRMAN', 'Group chairman / Mwenyekiti with broad group leadership access', NOW(), NOW()),
 ('ACCOUNTANT', 'Financial records, payments and reconciliation', NOW(), NOW()),
 ('CHAIRPERSON', 'Legacy chairperson role', NOW(), NOW()),
 ('VICE_CHAIRPERSON', 'Legacy vice-chairperson role', NOW(), NOW()),
 ('SECRETARY', 'Records and meetings administration', NOW(), NOW()),
 ('TREASURER', 'Cash collection and contribution administration', NOW(), NOW()),
 ('LOAN_OFFICER', 'Loan application administration', NOW(), NOW()),
 ('AUDITOR', 'Read-only financial and audit access', NOW(), NOW()),
 ('MEMBER', 'Limited member self-service access', NOW(), NOW())
ON CONFLICT (name) DO UPDATE SET description = EXCLUDED.description, updated_at = NOW();

INSERT INTO permissions (name, description, created_at, updated_at)
VALUES
 ('GROUP_MANAGE', 'Manage group settings and governance', NOW(), NOW()),
 ('PAYMENT_VIEW', 'View received payments and proof submissions', NOW(), NOW()),
 ('PAYMENT_APPROVE', 'Approve or reject payment proofs', NOW(), NOW()),
 ('SHARE_PURCHASE_APPROVE', 'Approve manual share purchase proofs', NOW(), NOW()),
 ('DASHBOARD_GROUP_VIEW', 'View the group-wide dashboard', NOW(), NOW()),
 ('WORKFLOW_MANAGE', 'Configure group workflow nodes', NOW(), NOW()),
 ('AUDIT_VIEW', 'View group audit records', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

-- Group admin: all currently defined permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'GROUP_ADMIN'
ON CONFLICT DO NOTHING;

-- Chairman: governance permissions. Additional action authority is assigned
-- explicitly through a workflow node.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN
 ('GROUP_MANAGE', 'MEMBER_VIEW', 'MEMBER_MANAGE', 'MEETING_MANAGE', 'REPORT_VIEW', 'AUDIT_VIEW', 'USER_ROLE_MANAGE', 'WORKFLOW_MANAGE')
WHERE r.name = 'GROUP_CHAIRMAN'
ON CONFLICT DO NOTHING;

-- Accountant: finance and payment-proof permissions only.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN
 ('MEMBER_VIEW','PAYMENT_VIEW','PAYMENT_APPROVE','SHARE_PURCHASE_APPROVE','CONTRIBUTION_MANAGE','REPORT_VIEW','AUDIT_VIEW')
WHERE r.name = 'ACCOUNTANT'
ON CONFLICT DO NOTHING;

-- Members retain read access and may submit proofs, but cannot approve them.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN
 ('MEMBER_VIEW','REPORT_VIEW')
WHERE r.name = 'MEMBER'
ON CONFLICT DO NOTHING;

-- Existing GROUP_ADMIN assignments are intentionally preserved. New group creation
-- assigns GROUP_CHAIRMAN in Java; assign GROUP_ADMIN deliberately where full access
-- is required.

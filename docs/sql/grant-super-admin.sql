-- Run with psql after deploying the backend:
-- psql ... -v admin_phone='2557XXXXXXXX' -f grant-super-admin.sql
-- This grants an EXISTING, ACTIVE account a system role, never a group role.
BEGIN;
INSERT INTO roles (name, description, created_at)
VALUES ('SUPER_ADMIN', 'System administrator', CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;
INSERT INTO user_roles (user_id, role_id, created_at)
SELECT u.id, r.id, CURRENT_TIMESTAMP
FROM users u CROSS JOIN roles r
WHERE u.phone = :'admin_phone' AND u.status = 'ACTIVE' AND r.name = 'SUPER_ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;
SELECT u.phone, r.name FROM user_roles ur JOIN users u ON u.id = ur.user_id
JOIN roles r ON r.id = ur.role_id WHERE u.phone = :'admin_phone' AND r.name = 'SUPER_ADMIN';
COMMIT;

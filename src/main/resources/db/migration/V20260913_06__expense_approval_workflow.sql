ALTER TABLE public.expenses ADD COLUMN IF NOT EXISTS approval_steps_json text;
UPDATE public.expenses
SET approval_steps_json = '[{"role":"ACCOUNTANT","label":"Accountant review","approvedAt":null,"approvedBy":null}]'
WHERE approval_steps_json IS NULL AND status = 'PENDING';

INSERT INTO permissions (name, description, created_at, updated_at)
VALUES ('EXPENSE_APPROVE', 'Approve or reject group expenses', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('ACCOUNTANT', 'GROUP_ADMIN', 'GROUP_CHAIRMAN', 'CHAIRPERSON', 'VICE_CHAIRPERSON', 'SECRETARY', 'TREASURER', 'LOAN_OFFICER', 'AUDITOR')
  AND p.name = 'EXPENSE_APPROVE'
ON CONFLICT DO NOTHING;

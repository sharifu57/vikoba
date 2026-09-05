-- Seed the default chart of accounts for each existing group without creating duplicates.
-- This keeps the unique constraint uk_account_group_code safe during repeated bootstraps or app restarts.

INSERT INTO public.accounts (active, code, created_at, group_id, name, parent_id, type, updated_at)
SELECT
    true,
    v.code,
    NOW(),
    g.id,
    v.name,
    NULL,
    v.type,
    NOW()
FROM public.vikoba_groups g
CROSS JOIN (
    VALUES
        ('1000', 'Cash in Hand', 'ASSET'),
        ('1010', 'Bank Account', 'ASSET'),
        ('1020', 'Mobile Money', 'ASSET'),
        ('1100', 'Loan Receivables', 'ASSET'),
        ('2000', 'Member Savings', 'LIABILITY'),
        ('3000', 'Share Capital', 'EQUITY'),
        ('4000', 'Interest Income', 'INCOME'),
        ('5000', 'Operating Expenses', 'EXPENSE')
) AS v(code, name, type)
ON CONFLICT (group_id, code) DO NOTHING;

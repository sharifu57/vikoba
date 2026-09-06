-- Run in DBeaver to make the standard Jamii support types available to every group.
-- Safe to run again: existing (group_id, code) rows are retained.
INSERT INTO social_fund_types
    (group_id, code, name, description, default_contribution, mandatory, active, created_at, updated_at)
SELECT
    g.id, t.code, t.name, t.description, t.default_contribution, t.mandatory,
    TRUE, NOW(), NOW()
FROM vikoba_groups AS g
CROSS JOIN (
    VALUES
        ('MEDICAL', 'Medical emergency', 'Support for urgent medical treatment or hospital expenses.', 0::numeric, FALSE),
        ('BEREAVEMENT', 'Bereavement support', 'Support following the death of a member or eligible dependant.', 0::numeric, FALSE),
        ('ACCIDENT', 'Accident support', 'Support for an accident or sudden emergency.', 0::numeric, FALSE),
        ('DISASTER', 'Disaster relief', 'Support after fire, flood, or another household disaster.', 0::numeric, FALSE),
        ('MATERNITY', 'Maternity support', 'Support for childbirth-related needs.', 0::numeric, FALSE)
) AS t(code, name, description, default_contribution, mandatory)
ON CONFLICT (group_id, code) DO NOTHING;




INSERT INTO expense_categories (group_id, name, description, active, created_at, updated_at)
SELECT g.id, x.name, x.description, true, NOW(), NOW()
FROM vikoba_groups g
CROSS JOIN (
    SELECT 'Food' AS name, 'Food and meals' AS description
    UNION ALL
    SELECT 'Transport', 'Travel and transport costs'
    UNION ALL
    SELECT 'Office', 'Office supplies and admin'
    UNION ALL
    SELECT 'Utilities', 'Electricity, water and communication'
) x
WHERE NOT EXISTS (
    SELECT 1
    FROM expense_categories ec
    WHERE ec.group_id = g.id
      AND ec.name = x.name
);

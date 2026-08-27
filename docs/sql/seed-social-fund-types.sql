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

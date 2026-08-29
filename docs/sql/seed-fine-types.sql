-- Standard VIKOBA fines for every group. Safe to run repeatedly.
INSERT INTO fine_types
    (group_id, code, name, description, default_amount, active, created_at, updated_at)
SELECT g.id, t.code, t.name, t.description, t.default_amount, TRUE, NOW(), NOW()
FROM vikoba_groups g
CROSS JOIN (VALUES
    ('MEETING_ABSENCE', 'Meeting absence', 'Failure to attend a scheduled group meeting without an approved excuse.', 10000::numeric),
    ('MEETING_LATE', 'Late arrival', 'Arriving after the agreed meeting start time.', 5000::numeric),
    ('LATE_CONTRIBUTION', 'Late contribution', 'Contribution paid after the group deadline.', 5000::numeric),
    ('LATE_LOAN_PAYMENT', 'Late loan repayment', 'Loan instalment paid after its due date.', 10000::numeric),
    ('MISCONDUCT', 'Misconduct', 'Breach of approved group rules or member conduct.', 10000::numeric),
    ('OTHER', 'Other penalty', 'A penalty approved by the group for an exceptional case.', 0::numeric)
) AS t(code, name, description, default_amount)
ON CONFLICT (group_id, code) DO NOTHING;

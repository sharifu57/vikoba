-- Run migrate-fine-types-global.sql first.
-- Shared standard VIKOBA fines. NULL group_id makes these available to every
-- current and future group; group-specific custom types can still use group_id.
INSERT INTO public.fine_types
    (group_id, code, name, description, default_amount, active, created_at, updated_at)
VALUES
    (NULL, 'MEETING_ABSENCE', 'Meeting absence', 'Failure to attend a scheduled group meeting without an approved excuse.', 10000, TRUE, NOW(), NOW()),
    (NULL, 'MEETING_LATE', 'Late arrival', 'Arriving after the agreed meeting start time.', 5000, TRUE, NOW(), NOW()),
    (NULL, 'LATE_CONTRIBUTION', 'Late contribution', 'Contribution paid after the group deadline.', 5000, TRUE, NOW(), NOW()),
    (NULL, 'LATE_LOAN_PAYMENT', 'Late loan repayment', 'Loan instalment paid after its due date.', 10000, TRUE, NOW(), NOW()),
    (NULL, 'MISCONDUCT', 'Misconduct', 'Breach of approved group rules or member conduct.', 10000, TRUE, NOW(), NOW()),
    (NULL, 'OTHER', 'Other penalty', 'A penalty approved by the group for an exceptional case.', 0, TRUE, NOW(), NOW())
ON CONFLICT (code) WHERE group_id IS NULL DO NOTHING;

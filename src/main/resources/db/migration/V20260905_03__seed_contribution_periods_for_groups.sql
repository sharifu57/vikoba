INSERT INTO contribution_types (
    created_at,
    updated_at,
    group_id,
    code,
    name,
    description,
    default_amount,
    frequency,
    mandatory,
    active
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    g.id,
    'GENERAL_CONTRIBUTION',
    'General Contribution',
    'Default monthly group contribution',
    50000,
    'MONTHLY',
    TRUE,
    TRUE
FROM vikoba_groups g
WHERE NOT EXISTS (
    SELECT 1
    FROM contribution_types ct
    WHERE ct.group_id = g.id
      AND ct.code = 'GENERAL_CONTRIBUTION'
);

INSERT INTO contribution_periods (
    created_at,
    updated_at,
    contribution_type_id,
    period_start,
    period_end,
    expected_amount,
    status
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    ct.id,
    month_start::date,
    (month_start + INTERVAL '1 month - 1 day')::date,
    ct.default_amount,
    'OPEN'
FROM contribution_types ct
CROSS JOIN generate_series(
    date_trunc('month', CURRENT_DATE),
    date_trunc('month', CURRENT_DATE) + INTERVAL '11 months',
    INTERVAL '1 month'
) AS months(month_start)
WHERE ct.code = 'GENERAL_CONTRIBUTION'
  AND NOT EXISTS (
      SELECT 1
      FROM contribution_periods cp
      WHERE cp.contribution_type_id = ct.id
        AND cp.period_start = month_start::date
        AND cp.period_end = (month_start + INTERVAL '1 month - 1 day')::date
  );

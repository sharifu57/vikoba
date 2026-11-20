-- Older group-creation paths assigned the founder GROUP_CHAIRMAN but omitted
-- GROUP_ADMIN. Promote the earliest active chairman in groups with no active
-- administrator so the founder receives the full access intended by setup.
WITH founder_candidates AS (
    SELECT
        gm.id AS group_member_id,
        gm.group_id,
        gm.joined_date,
        ROW_NUMBER() OVER (
            PARTITION BY gm.group_id
            ORDER BY gm.joined_date ASC, gm.id ASC
        ) AS founder_rank
    FROM group_members gm
    JOIN member_roles mr
        ON mr.group_member_id = gm.id
       AND mr.active = TRUE
       AND mr.role IN ('GROUP_CHAIRMAN', 'CHAIRPERSON')
    WHERE gm.status = 'ACTIVE'
      AND NOT EXISTS (
          SELECT 1
          FROM group_members admin_member
          JOIN member_roles admin_role
            ON admin_role.group_member_id = admin_member.id
           AND admin_role.active = TRUE
           AND admin_role.role = 'GROUP_ADMIN'
          WHERE admin_member.group_id = gm.group_id
            AND admin_member.status = 'ACTIVE'
      )
)
INSERT INTO member_roles (
    group_member_id,
    role,
    start_date,
    active,
    created_at,
    updated_at
)
SELECT
    founder.group_member_id,
    'GROUP_ADMIN',
    COALESCE(founder.joined_date, CURRENT_DATE),
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM founder_candidates founder
WHERE founder.founder_rank = 1;

ALTER TABLE share_purchase_requests
    ADD COLUMN IF NOT EXISTS jamii_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS accountant_approved_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS chair_approved_at TIMESTAMP NULL;

INSERT INTO workflow_nodes (group_id, action_key, label, required_role, required_permission, step_order)
SELECT g.id, 'SHARE_PURCHASE_CHAIR_APPROVAL', 'Chair approval for share purchase', 'GROUP_CHAIRMAN', 'SHARE_PURCHASE_APPROVE', 2
FROM vikoba_groups g
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_nodes w WHERE w.group_id = g.id AND w.action_key = 'SHARE_PURCHASE_CHAIR_APPROVAL'
);

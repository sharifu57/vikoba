ALTER TABLE group_settings RENAME COLUMN minimum_contribution TO minimum_share_purchase_amount;
ALTER TABLE group_settings DROP COLUMN IF EXISTS maximum_contribution;
ALTER TABLE group_settings DROP COLUMN IF EXISTS maximum_shares_per_member;
ALTER TABLE group_settings ADD COLUMN IF NOT EXISTS required_loan_guarantors INTEGER NOT NULL DEFAULT 2;
ALTER TABLE group_settings ADD COLUMN IF NOT EXISTS jamii_contribution_per_share_payment NUMERIC(19,2) NOT NULL DEFAULT 0;
ALTER TABLE meetings ADD COLUMN IF NOT EXISTS meeting_mode VARCHAR(20) NOT NULL DEFAULT 'PHYSICAL';
ALTER TABLE meetings ADD COLUMN IF NOT EXISTS meeting_link VARCHAR(500);

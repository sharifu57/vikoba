ALTER TABLE share_purchase_requests
    ADD COLUMN IF NOT EXISTS jamii_amount NUMERIC(19, 2) DEFAULT 0;

ALTER TABLE share_purchase_requests
    ALTER COLUMN jamii_amount SET DEFAULT 0;

UPDATE share_purchase_requests
SET jamii_amount = 0
WHERE jamii_amount IS NULL;

ALTER TABLE share_purchase_requests
    ALTER COLUMN jamii_amount SET NOT NULL;

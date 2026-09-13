ALTER TABLE share_transactions
    ALTER COLUMN quantity TYPE NUMERIC(19, 8) USING quantity::NUMERIC(19, 8);

ALTER TABLE share_purchase_requests
    ALTER COLUMN quantity TYPE NUMERIC(19, 8) USING quantity::NUMERIC(19, 8);

ALTER TABLE dividends
    ALTER COLUMN shares_owned TYPE NUMERIC(19, 8) USING shares_owned::NUMERIC(19, 8);

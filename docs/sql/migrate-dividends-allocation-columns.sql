-- Run once if the dividends table was created before allocation details were added.
ALTER TABLE public.dividends
    ADD COLUMN IF NOT EXISTS contributions numeric(19,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fines_assessed numeric(19,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fines_paid numeric(19,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fine_deduction numeric(19,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS share_value numeric(19,2) NOT NULL DEFAULT 0;

ALTER TABLE public.loans
    ADD COLUMN IF NOT EXISTS required_guarantors_at_application INTEGER;

ALTER TABLE public.loans
    ADD COLUMN IF NOT EXISTS late_fine_at_application NUMERIC(19, 2);

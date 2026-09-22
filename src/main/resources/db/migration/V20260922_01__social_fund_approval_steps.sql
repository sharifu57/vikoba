ALTER TABLE IF EXISTS public.social_fund_requests
    ADD COLUMN IF NOT EXISTS approval_steps_json TEXT;

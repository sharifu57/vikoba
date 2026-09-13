ALTER TABLE public.share_purchase_requests
    ADD COLUMN IF NOT EXISTS approval_steps_json text;

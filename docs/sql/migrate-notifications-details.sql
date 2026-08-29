-- Notification delivery/audit metadata. Safe to run repeatedly.
ALTER TABLE public.notifications
    ADD COLUMN IF NOT EXISTS channel varchar(20) NOT NULL DEFAULT 'IN_APP',
    ADD COLUMN IF NOT EXISTS delivery_status varchar(20) NOT NULL DEFAULT 'SENT',
    ADD COLUMN IF NOT EXISTS recipient_phone varchar(30),
    ADD COLUMN IF NOT EXISTS provider_response text,
    ADD COLUMN IF NOT EXISTS sent_at timestamp;

CREATE INDEX IF NOT EXISTS idx_notification_delivery_status
    ON public.notifications (delivery_status);
CREATE INDEX IF NOT EXISTS idx_notification_channel
    ON public.notifications (channel);

-- Non-secret, administrator-managed application settings.
-- Never store JWT signing keys, database passwords, or provider API keys here.
CREATE TABLE IF NOT EXISTS public.system_settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(150) NOT NULL UNIQUE,
    setting_value TEXT,
    value_type VARCHAR(30) NOT NULL DEFAULT 'STRING',
    description VARCHAR(500),
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_system_setting_key
    ON public.system_settings (setting_key);

INSERT INTO public.system_settings
    (setting_key, setting_value, value_type, description, is_public)
VALUES
    ('app.default.language', 'sw', 'STRING', 'Default application language', TRUE),
    ('app.currency', 'TZS', 'STRING', 'Default currency', TRUE),
    ('app.maintenance.mode', 'false', 'BOOLEAN', 'Enable maintenance mode', FALSE),
    ('app.max.upload.size.mb', '20', 'NUMBER', 'Maximum upload size in megabytes', FALSE),
    ('sms.sender.id', 'Pago', 'STRING', 'SMS sender name', FALSE)
ON CONFLICT (setting_key) DO NOTHING;

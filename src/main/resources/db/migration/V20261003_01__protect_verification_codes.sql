-- Apply before deploying the hashed-OTP backend to a database with schema validation.
ALTER TABLE otp ALTER COLUMN code TYPE VARCHAR(100);

-- Legacy codes are intentionally invalidated instead of accepting plaintext.
UPDATE otp SET code = '[redacted]', is_expired = TRUE
WHERE code ~ '^[0-9]{6}$';

UPDATE notifications
SET message = 'Verification code requested; code not retained.', provider_response = NULL
WHERE message LIKE 'VIKOBA360 verification code: %';

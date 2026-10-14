-- Keep SMS delivery history even when a recipient does not have a login account.
ALTER TABLE notifications ALTER COLUMN user_id DROP NOT NULL;

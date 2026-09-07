-- Shared categories used by every Kikoba group. group_id is intentionally NULL.
INSERT INTO expense_categories (group_id, name, description, active, created_at, updated_at)
SELECT NULL, 'Meeting expenses', 'Venue, refreshments, stationery and meeting logistics', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM expense_categories WHERE group_id IS NULL AND LOWER(name) = LOWER('Meeting expenses'));

INSERT INTO expense_categories (group_id, name, description, active, created_at, updated_at)
SELECT NULL, 'Administration', 'Registration, office supplies and administration costs', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM expense_categories WHERE group_id IS NULL AND LOWER(name) = LOWER('Administration'));

INSERT INTO expense_categories (group_id, name, description, active, created_at, updated_at)
SELECT NULL, 'Communication', 'SMS, phone, internet and communication costs', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM expense_categories WHERE group_id IS NULL AND LOWER(name) = LOWER('Communication'));

INSERT INTO expense_categories (group_id, name, description, active, created_at, updated_at)
SELECT NULL, 'Bank charges', 'Bank, mobile-money and payment processing charges', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM expense_categories WHERE group_id IS NULL AND LOWER(name) = LOWER('Bank charges'));

INSERT INTO expense_categories (group_id, name, description, active, created_at, updated_at)
SELECT NULL, 'Transport', 'Transport and travel costs for group activities', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM expense_categories WHERE group_id IS NULL AND LOWER(name) = LOWER('Transport'));

INSERT INTO expense_categories (group_id, name, description, active, created_at, updated_at)
SELECT NULL, 'Training and education', 'Training, workshops and learning materials', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM expense_categories WHERE group_id IS NULL AND LOWER(name) = LOWER('Training and education'));

INSERT INTO expense_categories (group_id, name, description, active, created_at, updated_at)
SELECT NULL, 'Social welfare', 'Approved welfare and community support costs', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM expense_categories WHERE group_id IS NULL AND LOWER(name) = LOWER('Social welfare'));

INSERT INTO expense_categories (group_id, name, description, active, created_at, updated_at)
SELECT NULL, 'Other', 'Other approved group expense', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM expense_categories WHERE group_id IS NULL AND LOWER(name) = LOWER('Other'));

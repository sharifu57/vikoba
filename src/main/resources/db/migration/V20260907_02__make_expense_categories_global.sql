ALTER TABLE expense_categories ALTER COLUMN group_id DROP NOT NULL;
UPDATE expense_categories SET group_id = NULL;

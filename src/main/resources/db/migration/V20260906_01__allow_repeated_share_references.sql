DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT tc.constraint_name
      INTO constraint_name
      FROM information_schema.table_constraints tc
      JOIN information_schema.constraint_column_usage ccu
        ON ccu.constraint_name = tc.constraint_name
       AND ccu.table_schema = tc.table_schema
     WHERE tc.table_schema = current_schema()
       AND tc.table_name = 'share_transactions'
       AND tc.constraint_type = 'UNIQUE'
       AND ccu.column_name = 'reference'
     LIMIT 1;

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE share_transactions DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

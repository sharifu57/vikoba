-- Return the fine type catalog to group-scoped rows.
-- Refuse to delete shared types if any fine records still reference them.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.fines f
        JOIN public.fine_types ft ON ft.id = f.fine_type_id
        WHERE ft.group_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot remove global fine types while fines reference them.';
    END IF;
END $$;

DELETE FROM public.fine_types WHERE group_id IS NULL;

DROP INDEX IF EXISTS public.uk_fine_type_global_code;

ALTER TABLE public.fine_types
    ALTER COLUMN group_id SET NOT NULL;

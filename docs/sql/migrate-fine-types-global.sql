-- Standard fine types are shared by every group, including groups created later.
-- Keep group_id for optional group-specific custom types, but allow NULL for globals.
ALTER TABLE public.fine_types
    ALTER COLUMN group_id DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_fine_type_global_code
    ON public.fine_types (code)
    WHERE group_id IS NULL;

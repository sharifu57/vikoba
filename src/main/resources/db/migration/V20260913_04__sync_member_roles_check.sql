-- Older Hibernate-generated enum checks did not include newer governance roles.
-- Keep all currently supported GroupRole values assignable without changing rows.
ALTER TABLE public.member_roles
    DROP CONSTRAINT IF EXISTS member_roles_role_check;

ALTER TABLE public.member_roles
    ADD CONSTRAINT member_roles_role_check CHECK (role IN (
        'GROUP_ADMIN', 'GROUP_CHAIRMAN', 'CHAIRPERSON', 'VICE_CHAIRPERSON',
        'SECRETARY', 'TREASURER', 'ACCOUNTANT', 'LOAN_OFFICER', 'AUDITOR', 'MEMBER'
    ));

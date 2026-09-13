-- Hibernate's earlier enum check predates Jamii allocations. Approval writes
-- SHARE_PURCHASE and JAMII_SHARE_PAYMENT within the same transaction.
ALTER TABLE public.payment_allocations
    DROP CONSTRAINT IF EXISTS payment_allocations_type_check;

ALTER TABLE public.payment_allocations
    ADD CONSTRAINT payment_allocations_type_check CHECK (type IN (
        'CONTRIBUTION', 'SHARE_PURCHASE', 'JAMII_SHARE_PAYMENT',
        'LOAN_REPAYMENT', 'FINE', 'SOCIAL_FUND', 'OTHER'
    ));

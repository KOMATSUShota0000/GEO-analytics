ALTER TABLE public.wallet_transactions
    ADD COLUMN IF NOT EXISTS note VARCHAR(2048);

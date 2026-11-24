-- Persist direct donation checkout references and deduplicate signed Stripe events.
BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='2min';

DO $preflight$
BEGIN
    IF to_regclass('public.doacoes') IS NULL THEN
        RAISE EXCEPTION 'Preflight: public.doacoes is missing.';
    END IF;
END
$preflight$;

ALTER TABLE public.doacoes
    ADD COLUMN IF NOT EXISTS stripe_session_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS stripe_payment_intent_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS stripe_account_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS stripe_livemode BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_doacoes_stripe_session ON public.doacoes(stripe_session_id) WHERE stripe_session_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_doacoes_stripe_payment_intent ON public.doacoes(stripe_payment_intent_id) WHERE stripe_payment_intent_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.doacao_stripe_events (
    event_id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMIT;
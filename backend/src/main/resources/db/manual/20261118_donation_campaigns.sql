-- Scoped additive fundraising campaign tables; existing direct donations remain untouched.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

DO $preflight$
BEGIN
    IF to_regclass('public.oscs') IS NULL OR to_regclass('public.usuarios') IS NULL THEN
        RAISE EXCEPTION 'Preflight: required public.oscs and public.usuarios tables are missing.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='oscs' AND column_name='id' AND data_type='integer')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='usuarios' AND column_name='id' AND data_type='integer') THEN
        RAISE EXCEPTION 'Preflight: NGO and user identifiers must remain integer-compatible.';
    END IF;
    IF to_regclass('public.donation_campaigns') IS NOT NULL
       OR to_regclass('public.donation_campaign_contributions') IS NOT NULL
       OR to_regclass('public.donation_campaign_quotes') IS NOT NULL
       OR to_regclass('public.donation_campaign_stripe_events') IS NOT NULL
       OR to_regclass('public.donation_campaign_ledger_entries') IS NOT NULL
       OR to_regclass('public.donation_campaign_moderation_history') IS NOT NULL
       OR to_regclass('public.donation_fee_rules') IS NOT NULL THEN
        RAISE EXCEPTION 'Preflight: campaign objects already exist; inspect the installed version before applying this one-shot migration.';
    END IF;
END
$preflight$;

CREATE TABLE IF NOT EXISTS donation_campaigns (
    id BIGSERIAL PRIMARY KEY,
    osc_id INTEGER NOT NULL REFERENCES oscs(id),
    title VARCHAR(140) NOT NULL,
    description TEXT NOT NULL,
    image_url VARCHAR(1000),
    goal_net_cents BIGINT NOT NULL CHECK (goal_net_cents > 0),
    deadline TIMESTAMPTZ,
    state VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (state IN ('DRAFT','ACTIVE','PAUSED','ENDED')),
    moderation_suspended BOOLEAN NOT NULL DEFAULT FALSE,
    suspension_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_donation_campaigns_public ON donation_campaigns(state, deadline);

CREATE TABLE IF NOT EXISTS donation_campaign_contributions (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES donation_campaigns(id),
    donor_id INTEGER NOT NULL REFERENCES usuarios(id),
    net_cents BIGINT NOT NULL CHECK (net_cents > 0),
    total_cents BIGINT NOT NULL CHECK (total_cents >= net_cents),
    payment_method VARCHAR(20) NOT NULL,
    fee_rule_version VARCHAR(40) NOT NULL,
    fee_breakdown JSONB NOT NULL,
    connected_account_id VARCHAR(255),
    currency VARCHAR(3) NOT NULL DEFAULT 'brl' CHECK (currency='brl'),
    stripe_session_id VARCHAR(255) UNIQUE,
    stripe_payment_intent_id VARCHAR(255) UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SUCCEEDED','FAILED','CANCELED','REFUNDED','DISPUTED')),
    actual_stripe_fee_cents BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_campaign_contributions_progress ON donation_campaign_contributions(campaign_id, status);

CREATE TABLE donation_campaign_quotes (
    id VARCHAR(80) PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES donation_campaigns(id),
    donor_id INTEGER NOT NULL REFERENCES usuarios(id),
    net_cents BIGINT NOT NULL CHECK (net_cents > 0),
    total_cents BIGINT NOT NULL CHECK (total_cents >= net_cents),
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('PIX','CARD','BOLETO')),
    card_origin VARCHAR(20),
    currency VARCHAR(3) NOT NULL DEFAULT 'brl' CHECK (currency='brl'),
    fee_rule_version VARCHAR(40) NOT NULL,
    breakdown JSONB NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    stripe_session_id VARCHAR(255) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_campaign_quotes_expiry ON donation_campaign_quotes(expires_at) WHERE consumed_at IS NULL;

-- Stripe event IDs provide webhook delivery idempotency. Payloads retain the source record for audit.
CREATE TABLE donation_campaign_stripe_events (
    event_id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    livemode BOOLEAN NOT NULL,
    connected_account_id VARCHAR(255),
    contribution_id BIGINT REFERENCES donation_campaign_contributions(id),
    payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Signed net deltas make refunds/disputes auditable and prevent them from silently counting as available progress.
-- Payouts intentionally have no entry type: withdrawals never reduce cumulative fundraising progress.
CREATE TABLE donation_campaign_ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    contribution_id BIGINT NOT NULL REFERENCES donation_campaign_contributions(id),
    source_event_id VARCHAR(255) REFERENCES donation_campaign_stripe_events(event_id),
    entry_type VARCHAR(32) NOT NULL CHECK (entry_type IN ('CONFIRMED','REFUNDED','DISPUTED','DISPUTE_WON','DISPUTE_LOST')),
    net_delta_cents BIGINT NOT NULL,
    gross_delta_cents BIGINT NOT NULL,
    platform_fee_delta_cents BIGINT NOT NULL DEFAULT 0,
    stripe_fee_cents BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(source_event_id, contribution_id, entry_type)
);
CREATE INDEX idx_campaign_ledger_contribution ON donation_campaign_ledger_entries(contribution_id, created_at);

CREATE TABLE IF NOT EXISTS donation_campaign_moderation_history (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES donation_campaigns(id),
    action VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    actor_id INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS donation_fee_rules (
    version VARCHAR(40) PRIMARY KEY,
    payment_method VARCHAR(20) NOT NULL,
    processing_rate NUMERIC(9,8) NOT NULL CHECK (processing_rate >= 0 AND processing_rate < 1),
    processing_fixed_cents BIGINT NOT NULL CHECK (processing_fixed_cents >= 0),
    platform_rate NUMERIC(9,8) NOT NULL CHECK (platform_rate >= 0 AND platform_rate < 1),
    additional_fixed_cents BIGINT NOT NULL DEFAULT 0 CHECK (additional_fixed_cents >= 0),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Supabase/PostgREST must not expose finance or moderation records directly. Backend connects as DB owner.
ALTER TABLE donation_campaigns ENABLE ROW LEVEL SECURITY;
ALTER TABLE donation_campaign_contributions ENABLE ROW LEVEL SECURITY;
ALTER TABLE donation_campaign_quotes ENABLE ROW LEVEL SECURITY;
ALTER TABLE donation_campaign_stripe_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE donation_campaign_ledger_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE donation_campaign_moderation_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE donation_fee_rules ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON donation_campaigns, donation_campaign_contributions, donation_campaign_quotes,
    donation_campaign_stripe_events, donation_campaign_ledger_entries,
    donation_campaign_moderation_history, donation_fee_rules FROM PUBLIC;
DO $grants$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
        REVOKE ALL ON donation_campaigns, donation_campaign_contributions, donation_campaign_quotes,
            donation_campaign_stripe_events, donation_campaign_ledger_entries,
            donation_campaign_moderation_history, donation_fee_rules FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
        REVOKE ALL ON donation_campaigns, donation_campaign_contributions, donation_campaign_quotes,
            donation_campaign_stripe_events, donation_campaign_ledger_entries,
            donation_campaign_moderation_history, donation_fee_rules FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN
        REVOKE ALL ON donation_campaigns, donation_campaign_contributions,
            donation_campaign_quotes, donation_campaign_stripe_events, donation_campaign_ledger_entries,
            donation_campaign_moderation_history, donation_fee_rules FROM service_role;
        GRANT SELECT, INSERT, UPDATE, DELETE ON donation_campaigns, donation_campaign_contributions,
            donation_campaign_quotes, donation_campaign_stripe_events, donation_campaign_ledger_entries,
            donation_campaign_moderation_history, donation_fee_rules TO service_role;
        GRANT USAGE, SELECT ON SEQUENCE donation_campaigns_id_seq,
            donation_campaign_contributions_id_seq, donation_campaign_ledger_entries_id_seq,
            donation_campaign_moderation_history_id_seq TO service_role;
    END IF;
END
$grants$;
-- No fee rule is enabled or seeded until live account pricing is verified and configured.
COMMIT;

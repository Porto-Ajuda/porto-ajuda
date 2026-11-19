-- Additive payment runtime fields and campaign reporting support.
BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='5min';
DO $preflight$
BEGIN
    IF to_regclass('public.donation_campaigns') IS NULL
       OR to_regclass('public.donation_campaign_quotes') IS NULL
       OR to_regclass('public.donation_campaign_contributions') IS NULL
       OR to_regclass('public.donation_campaign_ledger_entries') IS NULL
       OR to_regclass('public.content_reports') IS NULL
       OR to_regclass('public.content_moderation_blocks') IS NULL
       OR to_regprocedure('public.validate_content_report_target()') IS NULL THEN
        RAISE EXCEPTION 'Preflight: campaign and content reporting migrations must already be applied.';
    END IF;
END
$preflight$;

ALTER TABLE public.donation_fee_rules
    ADD COLUMN additional_rate NUMERIC(9,8) NOT NULL DEFAULT 0
    CHECK (additional_rate >= 0 AND additional_rate < 1),
    ADD COLUMN test_only BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN pricing_source VARCHAR(120) NOT NULL DEFAULT 'UNVERIFIED';
ALTER TABLE public.donation_campaign_contributions DROP CONSTRAINT donation_campaign_contributions_status_check;
ALTER TABLE public.donation_campaign_contributions ADD CONSTRAINT donation_campaign_contributions_status_check
    CHECK (status IN ('PENDING','SUCCEEDED','FAILED','CANCELED','PARTIALLY_REFUNDED','REFUNDED','DISPUTED','DISPUTE_LOST'));
ALTER TABLE public.donation_campaign_contributions
    ADD COLUMN quote_id VARCHAR(80) REFERENCES public.donation_campaign_quotes(id),
    ADD COLUMN stripe_charge_id VARCHAR(255),
    ADD COLUMN stripe_balance_transaction_id VARCHAR(255),
    ADD COLUMN fee_shortfall_cents BIGINT NOT NULL DEFAULT 0 CHECK (fee_shortfall_cents >= 0),
    ADD COLUMN stripe_refunded_gross_cents BIGINT NOT NULL DEFAULT 0 CHECK (stripe_refunded_gross_cents >= 0);
ALTER TABLE public.donation_campaign_ledger_entries ADD COLUMN stripe_dispute_id VARCHAR(255);
ALTER TABLE public.donation_campaign_quotes ADD COLUMN checkout_url TEXT;
ALTER TABLE public.donation_campaign_ledger_entries DROP CONSTRAINT donation_campaign_ledger_entries_entry_type_check;
ALTER TABLE public.donation_campaign_ledger_entries ADD CONSTRAINT donation_campaign_ledger_entries_entry_type_check
    CHECK (entry_type IN ('CONFIRMED','REFUNDED','DISPUTED','DISPUTE_WON','DISPUTE_LOST','PLATFORM_FEE_REFUNDED'));
CREATE UNIQUE INDEX uq_campaign_contributions_quote ON public.donation_campaign_contributions(quote_id) WHERE quote_id IS NOT NULL;

-- Explicit public-price test assumption: worst-case domestic/international card baseline.
-- Not account-specific pricing; live payments remain disabled and no margin guarantee is implied.
INSERT INTO public.donation_fee_rules
    (version,payment_method,processing_rate,processing_fixed_cents,platform_rate,additional_rate,additional_fixed_cents,test_only,pricing_source,enabled)
VALUES ('test-br-card-worst-case-v1','CARD',0.0599,39,0.034,0,0,TRUE,
        'Stripe Brazil public card pricing baseline; test assumption only; excludes account/payout costs',TRUE);

ALTER TABLE public.content_reports DROP CONSTRAINT ck_content_reports_target;
ALTER TABLE public.content_reports ADD CONSTRAINT ck_content_reports_target CHECK (target_type IN ('OSC','POST','CAMPAIGN'));
ALTER TABLE public.content_reports DROP CONSTRAINT ck_content_reports_kind;
ALTER TABLE public.content_reports ADD CONSTRAINT ck_content_reports_kind CHECK
    ((target_type='OSC' AND content_kind='OSC') OR
     (target_type='POST' AND content_kind IN ('PUBLICATION','VOLUNTEERING')) OR
     (target_type='CAMPAIGN' AND content_kind='CAMPAIGN'));

ALTER TABLE public.content_moderation_blocks DROP CONSTRAINT ck_content_blocks_target;
ALTER TABLE public.content_moderation_blocks ADD CONSTRAINT ck_content_blocks_target CHECK (target_type IN ('OSC','POST','CAMPAIGN'));
ALTER TABLE public.content_moderation_blocks DROP CONSTRAINT ck_content_blocks_kind;
ALTER TABLE public.content_moderation_blocks ADD CONSTRAINT ck_content_blocks_kind CHECK
    ((target_type='OSC' AND content_kind='OSC') OR
     (target_type='POST' AND content_kind IN ('PUBLICATION','VOLUNTEERING')) OR
     (target_type='CAMPAIGN' AND content_kind='CAMPAIGN'));
ALTER TABLE public.content_report_review_history DROP CONSTRAINT ck_content_review_target;
ALTER TABLE public.content_report_review_history ADD CONSTRAINT ck_content_review_target CHECK (target_type IN ('OSC','POST','CAMPAIGN'));

CREATE OR REPLACE FUNCTION public.validate_content_report_target()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE post_type TEXT;
BEGIN
    IF NEW.target_type='OSC' THEN
        IF NEW.content_kind<>'OSC' THEN RAISE EXCEPTION 'Report target kind does not match.'; END IF;
        PERFORM 1 FROM public.oscs WHERE id=NEW.target_id FOR KEY SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Report target does not exist.'; END IF;
    ELSIF NEW.target_type='POST' THEN
        SELECT tipo INTO post_type FROM public.post WHERE id=NEW.target_id FOR NO KEY UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Report target does not exist.'; END IF;
        IF (NEW.content_kind='VOLUNTEERING' AND post_type<>'VOLUNTARIADO')
           OR (NEW.content_kind='PUBLICATION' AND post_type='VOLUNTARIADO') THEN
            RAISE EXCEPTION 'Report target kind does not match.';
        END IF;
        IF NEW.content_kind='VOLUNTEERING' THEN
            PERFORM 1 FROM public.vagas_voluntariado WHERE id_post=NEW.target_id FOR UPDATE;
            IF NOT FOUND THEN RAISE EXCEPTION 'Canonical Vaga target does not exist.'; END IF;
        ELSE
            PERFORM 1 FROM public.vagas_voluntariado WHERE id_post=NEW.target_id FOR KEY SHARE;
            IF FOUND THEN RAISE EXCEPTION 'Publication target conflicts with a linked Vaga.'; END IF;
        END IF;
    ELSIF NEW.target_type='CAMPAIGN' THEN
        IF NEW.content_kind<>'CAMPAIGN' THEN RAISE EXCEPTION 'Report target kind does not match.'; END IF;
        IF NEW.target_id<1 OR NOT EXISTS (
            SELECT 1 FROM public.donation_campaigns c JOIN public.oscs o ON o.id=c.osc_id
             WHERE c.id=NEW.target_id AND c.state='ACTIVE' AND NOT c.moderation_suspended
               AND o.status='ATIVA' AND o.data_arquivamento IS NULL
               AND (c.deadline IS NULL OR c.deadline>CURRENT_TIMESTAMP)
        ) THEN RAISE EXCEPTION 'Report target does not exist or is unavailable.'; END IF;
        PERFORM 1 FROM public.donation_campaigns WHERE id=NEW.target_id FOR NO KEY UPDATE;
    ELSE
        RAISE EXCEPTION 'Unsupported report target type.';
    END IF;
    RETURN NEW;
END;
$$;

COMMIT;

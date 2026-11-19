-- Bind a quote to its original Connect recipient and tighten only campaign-table ACLs.
BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='2min';
DO $preflight$
BEGIN
    IF to_regclass('public.donation_campaign_quotes') IS NULL
       OR to_regclass('public.donation_campaigns') IS NULL
       OR to_regclass('public.donation_campaign_contributions') IS NULL THEN
        RAISE EXCEPTION 'Preflight: campaign payment tables are missing.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN
        RAISE EXCEPTION 'Preflight: Supabase service_role is missing.';
    END IF;
END
$preflight$;

ALTER TABLE public.donation_campaign_quotes ADD COLUMN connected_account_id VARCHAR(255);
REVOKE ALL ON public.donation_campaigns,public.donation_campaign_contributions,public.donation_campaign_quotes,
    public.donation_campaign_stripe_events,public.donation_campaign_ledger_entries,
    public.donation_campaign_moderation_history,public.donation_fee_rules FROM service_role;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.donation_campaigns,public.donation_campaign_contributions,
    public.donation_campaign_quotes,public.donation_campaign_stripe_events,public.donation_campaign_ledger_entries,
    public.donation_campaign_moderation_history,public.donation_fee_rules TO service_role;
REVOKE ALL ON SEQUENCE public.donation_campaigns_id_seq,public.donation_campaign_contributions_id_seq,
    public.donation_campaign_ledger_entries_id_seq,public.donation_campaign_moderation_history_id_seq FROM service_role;
GRANT USAGE,SELECT ON SEQUENCE public.donation_campaigns_id_seq,public.donation_campaign_contributions_id_seq,
    public.donation_campaign_ledger_entries_id_seq,public.donation_campaign_moderation_history_id_seq TO service_role;
COMMIT;

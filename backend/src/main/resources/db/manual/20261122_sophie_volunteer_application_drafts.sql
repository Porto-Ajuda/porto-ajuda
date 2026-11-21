-- Additive persistence for explicit Sophie volunteer-application previews.
-- Do not execute against a shared database without the project's normal migration approval.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '2min';

DO $preflight$
BEGIN
    IF to_regclass('public.usuarios') IS NULL
       OR to_regclass('public.support_conversations') IS NULL
       OR to_regclass('public.support_messages') IS NULL
       OR to_regclass('public.support_retention_authorizations') IS NULL THEN
        RAISE EXCEPTION 'Preflight: M15 users, conversations, messages and retention authorization are required.';
    END IF;
    IF to_regclass('public.support_volunteer_application_drafts') IS NOT NULL THEN
        RAISE EXCEPTION 'Preflight: Sophie application draft table already exists; inspect it instead of rerunning.';
    END IF;
END
$preflight$;

CREATE TABLE public.support_volunteer_application_drafts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Immutable audit snapshots are deliberately not FK blockers for account/conversation cleanup.
    actor_user_id INTEGER NOT NULL,
    conversation_id UUID NOT NULL,
    action_type VARCHAR(40) NOT NULL DEFAULT 'VOLUNTEER_APPLICATION',
    target_opportunity_id INTEGER NOT NULL CHECK (target_opportunity_id > 0),
    request_parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    request_hash CHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    opportunity_title VARCHAR(255) NOT NULL,
    ngo_name VARCHAR(255) NOT NULL,
    public_details VARCHAR(1200) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (state IN ('DRAFT','CANCELLED','EXPIRED','COMPLETED')),
    result_application_id INTEGER NULL,
    result_status VARCHAR(24) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NULL,
    CONSTRAINT ck_support_volunteer_draft_type CHECK (action_type='VOLUNTEER_APPLICATION'),
    CONSTRAINT ck_support_volunteer_draft_expiry CHECK (expires_at > created_at AND expires_at <= created_at + INTERVAL '10 minutes'),
    CONSTRAINT ck_support_volunteer_draft_result CHECK (
        (state='COMPLETED' AND result_application_id IS NOT NULL AND result_status IS NOT NULL AND completed_at IS NOT NULL)
        OR (state<>'COMPLETED' AND result_application_id IS NULL AND result_status IS NULL AND completed_at IS NULL)
    )
);

-- Preview text and request parameters exist only for the five-minute confirmation window.
-- A completed action gets a minimal 30-day receipt so a lost HTTP response can be retried
-- without executing the application twice. M15 conversation retention/account deletion
-- removes receipts earlier when their owning data is purged.
CREATE TABLE public.support_volunteer_application_receipts (
    draft_id UUID PRIMARY KEY,
    actor_user_id INTEGER NOT NULL,
    conversation_id UUID NOT NULL,
    target_opportunity_id INTEGER NOT NULL CHECK (target_opportunity_id > 0),
    request_hash CHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    result_application_id INTEGER NOT NULL,
    result_status VARCHAR(24) NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_support_volunteer_receipt_expiry CHECK (expires_at > completed_at AND expires_at <= completed_at + INTERVAL '30 days')
);
CREATE UNIQUE INDEX uq_support_volunteer_receipt_actor_target
    ON public.support_volunteer_application_receipts(actor_user_id,target_opportunity_id);
CREATE INDEX idx_support_volunteer_receipt_expiry ON public.support_volunteer_application_receipts(expires_at);
CREATE UNIQUE INDEX uq_support_volunteer_draft_open_target
    ON public.support_volunteer_application_drafts(actor_user_id, conversation_id, target_opportunity_id)
    WHERE state='DRAFT';
CREATE INDEX idx_support_volunteer_drafts_conversation
    ON public.support_volunteer_application_drafts(conversation_id, actor_user_id, created_at DESC);

CREATE FUNCTION public.guard_support_volunteer_application_draft() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        -- A completed draft is deleted only after its minimal receipt has been written.
        IF OLD.state='COMPLETED' AND NOT EXISTS (
            SELECT 1 FROM public.support_volunteer_application_receipts r WHERE r.draft_id=OLD.id
        ) THEN RAISE EXCEPTION 'A completed draft requires a receipt before preview cleanup.'; END IF;
        RETURN OLD;
    END IF;
    IF (NEW.id,NEW.actor_user_id,NEW.conversation_id,NEW.action_type,NEW.target_opportunity_id,
        NEW.request_parameters,NEW.request_hash,NEW.opportunity_title,NEW.ngo_name,NEW.public_details,
        NEW.created_at,NEW.expires_at)
       IS DISTINCT FROM
       (OLD.id,OLD.actor_user_id,OLD.conversation_id,OLD.action_type,OLD.target_opportunity_id,
        OLD.request_parameters,OLD.request_hash,OLD.opportunity_title,OLD.ngo_name,OLD.public_details,
        OLD.created_at,OLD.expires_at) THEN
        RAISE EXCEPTION 'Sophie action draft binding is immutable.';
    END IF;
    IF NOT ((OLD.state='DRAFT' AND NEW.state IN ('CANCELLED','EXPIRED','COMPLETED'))
            OR (OLD.state=NEW.state AND NEW.state IN ('CANCELLED','EXPIRED','COMPLETED'))) THEN
        RAISE EXCEPTION 'Invalid Sophie action draft state transition.';
    END IF;
    IF OLD.state='COMPLETED' AND (NEW.result_application_id,NEW.result_status,NEW.completed_at)
       IS DISTINCT FROM (OLD.result_application_id,OLD.result_status,OLD.completed_at) THEN
        RAISE EXCEPTION 'Completed Sophie action outcome is immutable.';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_support_volunteer_draft_guard
    BEFORE UPDATE OR DELETE ON public.support_volunteer_application_drafts
    FOR EACH ROW EXECUTE FUNCTION public.guard_support_volunteer_application_draft();

-- Run after M15 marks content purged, inside the same transaction and while its
-- transaction-scoped authorization still exists. This works even with zero messages.
CREATE FUNCTION public.cleanup_support_volunteer_drafts_after_retention() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.support_retention_authorizations a
                WHERE a.transaction_id=txid_current() AND a.backend_pid=pg_backend_pid()
                  AND a.conversation_id=NEW.id)
       AND OLD.content_purged_at IS NULL AND NEW.content_purged_at IS NOT NULL THEN
        DELETE FROM public.support_volunteer_application_drafts
         WHERE conversation_id=NEW.id;
        DELETE FROM public.support_volunteer_application_receipts
         WHERE conversation_id=NEW.id;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_support_volunteer_drafts_retention_cleanup
    AFTER UPDATE OF content_purged_at ON public.support_conversations
    FOR EACH ROW EXECUTE FUNCTION public.cleanup_support_volunteer_drafts_after_retention();

-- Account deletion removes all remaining previews and retry receipts transactionally.
CREATE FUNCTION public.cleanup_support_volunteer_drafts_after_account_deletion() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
BEGIN
    DELETE FROM public.support_volunteer_application_drafts
     WHERE actor_user_id=OLD.id;
    DELETE FROM public.support_volunteer_application_receipts WHERE actor_user_id=OLD.id;
    RETURN OLD;
END $$;
CREATE TRIGGER trg_support_volunteer_drafts_account_cleanup
    AFTER DELETE ON public.usuarios
    FOR EACH ROW EXECUTE FUNCTION public.cleanup_support_volunteer_drafts_after_account_deletion();

ALTER TABLE public.support_volunteer_application_drafts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.support_volunteer_application_receipts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.support_volunteer_application_drafts FROM PUBLIC;
REVOKE ALL ON public.support_volunteer_application_receipts FROM PUBLIC;
REVOKE ALL ON FUNCTION public.guard_support_volunteer_application_draft() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.cleanup_support_volunteer_drafts_after_retention() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.cleanup_support_volunteer_drafts_after_account_deletion() FROM PUBLIC;
DO $privileges$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
        EXECUTE 'REVOKE ALL ON public.support_volunteer_application_drafts FROM anon';
        EXECUTE 'REVOKE ALL ON public.support_volunteer_application_receipts FROM anon';
        EXECUTE 'REVOKE ALL ON FUNCTION public.guard_support_volunteer_application_draft() FROM anon';
        EXECUTE 'REVOKE ALL ON FUNCTION public.cleanup_support_volunteer_drafts_after_retention() FROM anon';
        EXECUTE 'REVOKE ALL ON FUNCTION public.cleanup_support_volunteer_drafts_after_account_deletion() FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
        EXECUTE 'REVOKE ALL ON public.support_volunteer_application_drafts FROM authenticated';
        EXECUTE 'REVOKE ALL ON public.support_volunteer_application_receipts FROM authenticated';
        EXECUTE 'REVOKE ALL ON FUNCTION public.guard_support_volunteer_application_draft() FROM authenticated';
        EXECUTE 'REVOKE ALL ON FUNCTION public.cleanup_support_volunteer_drafts_after_retention() FROM authenticated';
        EXECUTE 'REVOKE ALL ON FUNCTION public.cleanup_support_volunteer_drafts_after_account_deletion() FROM authenticated';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='postgres') THEN
        EXECUTE 'CREATE POLICY support_volunteer_drafts_postgres_backend ON public.support_volunteer_application_drafts TO postgres USING (true) WITH CHECK (true)';
        EXECUTE 'CREATE POLICY support_volunteer_receipts_postgres_backend ON public.support_volunteer_application_receipts TO postgres USING (true) WITH CHECK (true)';
        GRANT SELECT, INSERT, UPDATE, DELETE ON public.support_volunteer_application_drafts TO postgres;
        GRANT SELECT, INSERT, UPDATE, DELETE ON public.support_volunteer_application_receipts TO postgres;
    END IF;
END
$privileges$;

DO $postflight$
BEGIN
    IF NOT (SELECT relrowsecurity FROM pg_class WHERE oid='public.support_volunteer_application_drafts'::regclass)
       OR NOT (SELECT relrowsecurity FROM pg_class WHERE oid='public.support_volunteer_application_receipts'::regclass)
       OR to_regclass('public.uq_support_volunteer_draft_open_target') IS NULL
       OR to_regclass('public.uq_support_volunteer_receipt_actor_target') IS NULL
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='public.support_volunteer_application_drafts'::regclass
           AND tgname='trg_support_volunteer_draft_guard' AND tgenabled='O')
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='public.support_conversations'::regclass
           AND tgname='trg_support_volunteer_drafts_retention_cleanup' AND tgenabled='O')
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='public.usuarios'::regclass
           AND tgname='trg_support_volunteer_drafts_account_cleanup' AND tgenabled='O') THEN
        RAISE EXCEPTION 'Postflight: draft RLS, uniqueness or immutable binding guard is missing.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='anon') AND (
       has_table_privilege('anon','public.support_volunteer_application_drafts','SELECT') OR
       has_table_privilege('anon','public.support_volunteer_application_drafts','INSERT') OR
       has_table_privilege('anon','public.support_volunteer_application_drafts','UPDATE') OR
       has_table_privilege('anon','public.support_volunteer_application_drafts','DELETE') OR
       has_table_privilege('anon','public.support_volunteer_application_receipts','SELECT') OR
       has_table_privilege('anon','public.support_volunteer_application_receipts','INSERT') OR
       has_table_privilege('anon','public.support_volunteer_application_receipts','UPDATE') OR
       has_table_privilege('anon','public.support_volunteer_application_receipts','DELETE')) THEN
        RAISE EXCEPTION 'Postflight: anon has unexpected draft/receipt table privileges.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='authenticated') AND (
       has_table_privilege('authenticated','public.support_volunteer_application_drafts','SELECT') OR
       has_table_privilege('authenticated','public.support_volunteer_application_drafts','INSERT') OR
       has_table_privilege('authenticated','public.support_volunteer_application_drafts','UPDATE') OR
       has_table_privilege('authenticated','public.support_volunteer_application_drafts','DELETE') OR
       has_table_privilege('authenticated','public.support_volunteer_application_receipts','SELECT') OR
       has_table_privilege('authenticated','public.support_volunteer_application_receipts','INSERT') OR
       has_table_privilege('authenticated','public.support_volunteer_application_receipts','UPDATE') OR
       has_table_privilege('authenticated','public.support_volunteer_application_receipts','DELETE')) THEN
        RAISE EXCEPTION 'Postflight: authenticated has unexpected draft/receipt table privileges.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='postgres') AND (
       NOT has_table_privilege('postgres','public.support_volunteer_application_drafts','SELECT') OR
       NOT has_table_privilege('postgres','public.support_volunteer_application_drafts','INSERT') OR
       NOT has_table_privilege('postgres','public.support_volunteer_application_drafts','UPDATE') OR
       NOT has_table_privilege('postgres','public.support_volunteer_application_drafts','DELETE') OR
       NOT has_table_privilege('postgres','public.support_volunteer_application_receipts','SELECT') OR
       NOT has_table_privilege('postgres','public.support_volunteer_application_receipts','INSERT') OR
       NOT has_table_privilege('postgres','public.support_volunteer_application_receipts','UPDATE') OR
       NOT has_table_privilege('postgres','public.support_volunteer_application_receipts','DELETE')) THEN
        RAISE EXCEPTION 'Postflight: configured application role postgres lacks a required individual privilege.';
    END IF;
END
$postflight$;
COMMIT;

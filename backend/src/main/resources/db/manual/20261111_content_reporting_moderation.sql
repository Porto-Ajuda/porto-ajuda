-- Porto Ajuda content reporting and preventive moderation.
-- One-shot migration: a repeated run fails at preflight and the transaction rolls back.
-- Apply only after reviewing the preflight queries and project-specific Supabase role model.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

DO $preflight$
BEGIN
    IF current_user <> 'postgres'
       OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname=current_user AND rolbypassrls AND NOT rolsuper)
       OR NOT has_schema_privilege(current_user, 'public', 'USAGE')
       OR NOT has_schema_privilege(current_user, 'public', 'CREATE') THEN
        RAISE EXCEPTION 'Preflight: execute as the existing postgres BYPASSRLS non-superuser database owner.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='anon')
       OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='authenticated')
       OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN
        RAISE EXCEPTION 'Preflight: expected Supabase REST/service roles are missing.';
    END IF;
    IF to_regclass('public.usuarios') IS NULL OR to_regclass('public.oscs') IS NULL
       OR to_regclass('public.post') IS NULL OR to_regclass('public.vagas_voluntariado') IS NULL THEN
        RAISE EXCEPTION 'Preflight: required Porto Ajuda base tables are missing.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM unnest(ARRAY['usuarios','oscs','post','vagas_voluntariado']::text[]) AS t(name)
        JOIN pg_class c ON c.oid=to_regclass('public.'||t.name)
        WHERE pg_get_userbyid(c.relowner) <> current_user
    ) OR NOT has_table_privilege(current_user, 'public.usuarios', 'REFERENCES') THEN
        RAISE EXCEPTION 'Preflight: current backend/migration role does not own the FK target/base tables.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='usuarios' AND column_name='id' AND data_type='integer')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='oscs' AND column_name='id' AND data_type='integer')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='post' AND column_name='id' AND data_type='integer')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='post' AND column_name='id_osc' AND data_type='integer')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='post' AND column_name='tipo' AND data_type='character varying')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='vagas_voluntariado' AND column_name='id_post' AND data_type='integer') THEN
        RAISE EXCEPTION 'Preflight: target key/column types diverge from the current JPA schema.';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c WHERE c.conrelid='public.vagas_voluntariado'::regclass
          AND c.confrelid='public.post'::regclass AND c.contype='f'
          AND pg_get_constraintdef(c.oid) ILIKE 'FOREIGN KEY (id_post) REFERENCES post(id)%'
    ) OR NOT EXISTS (
        SELECT 1 FROM pg_constraint c WHERE c.conrelid='public.post'::regclass
          AND c.confrelid='public.oscs'::regclass AND c.contype='f'
          AND pg_get_constraintdef(c.oid) ILIKE 'FOREIGN KEY (id_osc) REFERENCES oscs(id)%'
    ) OR NOT EXISTS (
        SELECT 1 FROM pg_index i WHERE i.indrelid='public.vagas_voluntariado'::regclass
          AND i.indisunique AND i.indisvalid AND i.indisready AND i.indnkeyatts=1
          AND i.indkey[0]=(SELECT attnum FROM pg_attribute WHERE attrelid='public.vagas_voluntariado'::regclass AND attname='id_post' AND NOT attisdropped)
    ) THEN
        RAISE EXCEPTION 'Preflight: Vaga must keep its unique FK to canonical Post.id.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.oscs'::regclass AND conname='ck_oscs_status' AND contype='c'
          AND pg_get_constraintdef(oid) LIKE '%ATIVA%' AND pg_get_constraintdef(oid) LIKE '%INATIVA%')
       OR NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.oscs'::regclass AND conname='ck_oscs_origem_ultima_desativacao' AND contype='c'
          AND pg_get_constraintdef(oid) LIKE '%ONG%' AND pg_get_constraintdef(oid) LIKE '%PORTO_AJUDA%' AND pg_get_constraintdef(oid) LIKE '%INDETERMINADA%')
       OR NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.post'::regclass AND conname='post_tipo_check' AND contype='c'
          AND pg_get_constraintdef(oid) LIKE '%NOTICIA%' AND pg_get_constraintdef(oid) LIKE '%DOACAO%' AND pg_get_constraintdef(oid) LIKE '%VOLUNTARIADO%')
       OR NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.vagas_voluntariado'::regclass AND conname='vagas_voluntariado_status_check' AND contype='c'
          AND pg_get_constraintdef(oid) LIKE '%ABERTA%' AND pg_get_constraintdef(oid) LIKE '%ENCERRADA%' AND pg_get_constraintdef(oid) LIKE '%CANCELADA%') THEN
        RAISE EXCEPTION 'Preflight: existing organization/post/vacancy lifecycle constraints are missing.';
    END IF;
    IF to_regprocedure('public.prevent_admin_operation_audit_mutation()') IS NULL
       OR pg_get_userbyid((SELECT proowner FROM pg_proc WHERE oid='public.prevent_admin_operation_audit_mutation()'::regprocedure)) <> current_user THEN
        RAISE EXCEPTION 'Preflight: the applied M5 append-only audit guard is missing or has unexpected ownership.';
    END IF;
    IF to_regclass('public.content_reports') IS NOT NULL
       OR to_regclass('public.content_moderation_blocks') IS NOT NULL
       OR to_regclass('public.content_report_review_history') IS NOT NULL
       OR to_regprocedure('public.validate_content_report_target()') IS NOT NULL
       OR to_regprocedure('public.prevent_reported_content_delete()') IS NOT NULL
       OR to_regprocedure('public.prevent_reported_vaga_change()') IS NOT NULL
       OR to_regprocedure('public.guard_content_report_identity()') IS NOT NULL
       OR to_regprocedure('public.guard_content_moderation_block_identity()') IS NOT NULL
       OR to_regprocedure('public.prevent_reported_post_type_change()') IS NOT NULL
       OR to_regprocedure('public.reject_content_report_review_history_mutation()') IS NOT NULL
       OR to_regclass('public.uq_content_report_pending_reporter_target') IS NOT NULL
       OR to_regclass('public.idx_content_report_latest_reviewed_by_reporter_target') IS NOT NULL
       OR to_regclass('public.uq_content_report_reporter_target') IS NOT NULL
       OR to_regclass('public.idx_content_reports_pending_target_date') IS NOT NULL
       OR to_regclass('public.idx_content_reports_review_queue') IS NOT NULL
       OR to_regclass('public.idx_content_reports_reporter_date') IS NOT NULL
       OR to_regclass('public.uq_content_blocks_active_source') IS NOT NULL
       OR to_regclass('public.idx_content_blocks_active_target') IS NOT NULL
       OR to_regclass('public.idx_content_report_review_target_date') IS NOT NULL
       OR to_regclass('public.content_reports_id_seq') IS NOT NULL
       OR to_regclass('public.content_moderation_blocks_id_seq') IS NOT NULL
       OR to_regclass('public.content_report_review_history_id_seq') IS NOT NULL
       OR EXISTS (SELECT 1 FROM pg_trigger WHERE NOT tgisinternal AND tgname IN (
          'trg_content_reports_target_exists','trg_content_moderation_blocks_target_exists',
          'trg_content_reports_identity_immutable','trg_content_moderation_blocks_identity_immutable',
          'trg_post_preserve_content_report_type',
          'trg_oscs_preserve_content_reports','trg_post_preserve_content_reports',
          'trg_vaga_preserve_content_reports','trg_vaga_preserve_content_report_link',
          'trg_content_report_review_history_immutable','trg_content_report_review_history_no_truncate')) THEN
        RAISE EXCEPTION 'Preflight: reporting objects already exist; inspect them instead of rerunning this one-shot migration.';
    END IF;
END
$preflight$;

CREATE TABLE public.content_reports (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    target_type VARCHAR(16) NOT NULL,
    target_id INTEGER NOT NULL,
    content_kind VARCHAR(16) NOT NULL,
    reporter_key VARCHAR(64) NOT NULL,
    reason VARCHAR(40) NOT NULL,
    details VARCHAR(1000),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ,
    CONSTRAINT ck_content_reports_target_id CHECK (target_id > 0),
    CONSTRAINT ck_content_reports_reporter_key CHECK (reporter_key ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_content_reports_target CHECK (target_type IN ('OSC', 'POST')),
    CONSTRAINT ck_content_reports_kind CHECK
        ((target_type='OSC' AND content_kind='OSC') OR
         (target_type='POST' AND content_kind IN ('PUBLICATION', 'VOLUNTEERING'))),
    CONSTRAINT ck_content_reports_reason CHECK (reason IN
        ('MISLEADING', 'INAPPROPRIATE', 'FRAUD', 'PRIVACY', 'OTHER')),
    CONSTRAINT ck_content_reports_status CHECK (status IN ('PENDING', 'DISMISSED', 'UPHELD')),
    CONSTRAINT ck_content_reports_review CHECK
        ((status = 'PENDING' AND reviewed_at IS NULL)
         OR (status <> 'PENDING' AND reviewed_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_content_report_pending_reporter_target
    ON public.content_reports (target_type, target_id, reporter_key)
    WHERE status='PENDING';
CREATE INDEX idx_content_report_latest_reviewed_by_reporter_target
    ON public.content_reports (target_type, target_id, reporter_key, reviewed_at DESC, id DESC)
    WHERE status IN ('DISMISSED','UPHELD');

CREATE INDEX idx_content_reports_pending_target_date
    ON public.content_reports (target_type, target_id, created_at DESC)
    WHERE status = 'PENDING';
CREATE INDEX idx_content_reports_review_queue
    ON public.content_reports (created_at, target_type, target_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_content_reports_reporter_date
    ON public.content_reports (reporter_key, created_at DESC);

CREATE TABLE public.content_moderation_blocks (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    target_type VARCHAR(16) NOT NULL,
    target_id INTEGER NOT NULL,
    content_kind VARCHAR(16) NOT NULL,
    source VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    reason VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id INTEGER REFERENCES public.usuarios(id) ON DELETE SET NULL,
    ended_at TIMESTAMPTZ,
    ended_by_user_id INTEGER REFERENCES public.usuarios(id) ON DELETE SET NULL,
    CONSTRAINT ck_content_blocks_target_id CHECK (target_id > 0),
    CONSTRAINT ck_content_blocks_target CHECK (target_type IN ('OSC', 'POST')),
    CONSTRAINT ck_content_blocks_kind CHECK
        ((target_type='OSC' AND content_kind='OSC') OR
         (target_type='POST' AND content_kind IN ('PUBLICATION', 'VOLUNTEERING'))),
    CONSTRAINT ck_content_blocks_source CHECK (source IN ('AUTOMATIC_PROTECTION', 'ADMIN_RESTRICTION')),
    CONSTRAINT ck_content_blocks_lifecycle CHECK
        ((active AND ended_at IS NULL) OR (NOT active AND ended_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_content_blocks_active_source
    ON public.content_moderation_blocks (target_type, target_id, source) WHERE active;
CREATE INDEX idx_content_blocks_active_target
    ON public.content_moderation_blocks (target_type, target_id) WHERE active;

CREATE TABLE public.content_report_review_history (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    target_type VARCHAR(16) NOT NULL,
    target_id INTEGER NOT NULL,
    decision VARCHAR(16) NOT NULL,
    justification VARCHAR(2000) NOT NULL,
    actor_user_id_snapshot INTEGER NOT NULL,
    actor_profile VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_content_review_target_id CHECK (target_id > 0),
    CONSTRAINT ck_content_review_target CHECK (target_type IN ('OSC', 'POST')),
    CONSTRAINT ck_content_review_decision CHECK (decision IN ('DISMISS', 'UPHOLD', 'RESTORE')),
    CONSTRAINT ck_content_review_profile CHECK (actor_profile IN ('SUPER_ADMIN', 'PLATFORM_ADMIN', 'MODERATOR'))
);
CREATE INDEX idx_content_report_review_target_date
    ON public.content_report_review_history (target_type, target_id, occurred_at DESC);

CREATE FUNCTION public.validate_content_report_target()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE post_type TEXT;
BEGIN
    IF NEW.target_type = 'OSC' THEN
        IF NEW.content_kind <> 'OSC' THEN RAISE EXCEPTION 'Report target kind does not match.'; END IF;
        PERFORM 1 FROM public.oscs WHERE id = NEW.target_id FOR KEY SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Report target does not exist.'; END IF;
    ELSE
        -- NO KEY UPDATE conflicts with a concurrent type update but remains
        -- compatible with FK KEY SHARE locks used by the Vaga relation.
        SELECT tipo INTO post_type FROM public.post WHERE id = NEW.target_id FOR NO KEY UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Report target does not exist.'; END IF;
        IF (NEW.content_kind='VOLUNTEERING' AND post_type <> 'VOLUNTARIADO')
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
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_content_reports_target_exists BEFORE INSERT ON public.content_reports
    FOR EACH ROW EXECUTE FUNCTION public.validate_content_report_target();
CREATE TRIGGER trg_content_moderation_blocks_target_exists BEFORE INSERT ON public.content_moderation_blocks
    FOR EACH ROW EXECUTE FUNCTION public.validate_content_report_target();

CREATE FUNCTION public.guard_content_report_identity()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.target_type IS DISTINCT FROM NEW.target_type
       OR OLD.target_id IS DISTINCT FROM NEW.target_id
       OR OLD.content_kind IS DISTINCT FROM NEW.content_kind
       OR OLD.reporter_key IS DISTINCT FROM NEW.reporter_key
       OR OLD.created_at IS DISTINCT FROM NEW.created_at THEN
        RAISE EXCEPTION 'Persisted content-report identity is immutable.';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_content_reports_identity_immutable
    BEFORE UPDATE ON public.content_reports
    FOR EACH ROW EXECUTE FUNCTION public.guard_content_report_identity();

CREATE FUNCTION public.guard_content_moderation_block_identity()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.target_type IS DISTINCT FROM NEW.target_type
       OR OLD.target_id IS DISTINCT FROM NEW.target_id
       OR OLD.content_kind IS DISTINCT FROM NEW.content_kind
       OR OLD.source IS DISTINCT FROM NEW.source
       OR OLD.reason IS DISTINCT FROM NEW.reason
       OR OLD.created_at IS DISTINCT FROM NEW.created_at THEN
        RAISE EXCEPTION 'Persisted moderation-block identity is immutable.';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_content_moderation_blocks_identity_immutable
    BEFORE UPDATE ON public.content_moderation_blocks
    FOR EACH ROW EXECUTE FUNCTION public.guard_content_moderation_block_identity();

CREATE FUNCTION public.prevent_reported_post_type_change()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.tipo IS DISTINCT FROM NEW.tipo AND (
        EXISTS (
            SELECT 1 FROM public.content_reports r
             WHERE r.target_type='POST' AND r.target_id=OLD.id
               AND ((r.content_kind='VOLUNTEERING' AND NEW.tipo <> 'VOLUNTARIADO')
                 OR (r.content_kind='PUBLICATION' AND NEW.tipo = 'VOLUNTARIADO'))
        ) OR EXISTS (
            SELECT 1 FROM public.content_moderation_blocks b
             WHERE b.target_type='POST' AND b.target_id=OLD.id
               AND ((b.content_kind='VOLUNTEERING' AND NEW.tipo <> 'VOLUNTARIADO')
                 OR (b.content_kind='PUBLICATION' AND NEW.tipo = 'VOLUNTARIADO'))
        ) OR EXISTS (
            -- Review history has no content_kind column, so any post type change
            -- after a review is conservatively rejected to preserve its meaning.
            SELECT 1 FROM public.content_report_review_history h
             WHERE h.target_type='POST' AND h.target_id=OLD.id
        )
    ) THEN
        RAISE EXCEPTION 'Post type conflicts with its content-moderation history.';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_post_preserve_content_report_type
    BEFORE UPDATE OF tipo ON public.post
    FOR EACH ROW EXECUTE FUNCTION public.prevent_reported_post_type_change();

CREATE FUNCTION public.prevent_reported_content_delete()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE canonical_type TEXT;
BEGIN
    canonical_type := CASE WHEN TG_TABLE_NAME='oscs' THEN 'OSC' ELSE 'POST' END;
    IF EXISTS (
        SELECT 1 FROM public.content_reports
         WHERE target_type=canonical_type AND target_id=OLD.id
        UNION ALL
        SELECT 1 FROM public.content_moderation_blocks
         WHERE target_type=canonical_type AND target_id=OLD.id
        UNION ALL
        SELECT 1 FROM public.content_report_review_history
         WHERE target_type=canonical_type AND target_id=OLD.id
    ) THEN
        RAISE EXCEPTION 'Content with moderation history cannot be deleted.';
    END IF;
    RETURN OLD;
END;
$$;
CREATE TRIGGER trg_oscs_preserve_content_reports BEFORE DELETE ON public.oscs
    FOR EACH ROW EXECUTE FUNCTION public.prevent_reported_content_delete();
CREATE TRIGGER trg_post_preserve_content_reports BEFORE DELETE ON public.post
    FOR EACH ROW EXECUTE FUNCTION public.prevent_reported_content_delete();

CREATE FUNCTION public.prevent_reported_vaga_change()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    old_target INTEGER;
    new_target INTEGER;
BEGIN
    IF TG_OP <> 'INSERT' THEN old_target := OLD.id_post; END IF;
    IF TG_OP <> 'DELETE' THEN new_target := NEW.id_post; END IF;
    IF EXISTS (
        SELECT 1 FROM public.content_reports r
         WHERE r.target_type='POST' AND r.target_id IN (old_target,new_target)
        UNION ALL
        SELECT 1 FROM public.content_moderation_blocks b
         WHERE b.target_type='POST' AND b.target_id IN (old_target,new_target)
        UNION ALL
        SELECT 1 FROM public.content_report_review_history h
         WHERE h.target_type='POST' AND h.target_id IN (old_target,new_target)
    ) THEN
        RAISE EXCEPTION 'Vaga with content-report history cannot be added, deleted, or detached.';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_vaga_preserve_content_reports BEFORE INSERT OR DELETE ON public.vagas_voluntariado
    FOR EACH ROW EXECUTE FUNCTION public.prevent_reported_vaga_change();
CREATE TRIGGER trg_vaga_preserve_content_report_link BEFORE UPDATE OF id_post ON public.vagas_voluntariado
    FOR EACH ROW WHEN (OLD.id_post IS DISTINCT FROM NEW.id_post)
    EXECUTE FUNCTION public.prevent_reported_vaga_change();

CREATE FUNCTION public.reject_content_report_review_history_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Content report review history is append-only.';
END;
$$;
CREATE TRIGGER trg_content_report_review_history_immutable
    BEFORE UPDATE OR DELETE ON public.content_report_review_history
    FOR EACH ROW EXECUTE FUNCTION public.reject_content_report_review_history_mutation();
CREATE TRIGGER trg_content_report_review_history_no_truncate
    BEFORE TRUNCATE ON public.content_report_review_history
    FOR EACH STATEMENT EXECUTE FUNCTION public.reject_content_report_review_history_mutation();

REVOKE ALL ON FUNCTION public.validate_content_report_target() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.prevent_reported_content_delete() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.prevent_reported_vaga_change() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.guard_content_report_identity() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.guard_content_moderation_block_identity() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.prevent_reported_post_type_change() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.reject_content_report_review_history_mutation() FROM PUBLIC;
-- Remove inherited M5 trigger-function EXECUTE grants from REST roles; the trigger remains attached.
REVOKE ALL ON FUNCTION public.prevent_admin_operation_audit_mutation() FROM PUBLIC;

ALTER TABLE public.content_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.content_moderation_blocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.content_report_review_history ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.content_reports, public.content_moderation_blocks,
    public.content_report_review_history FROM PUBLIC;
REVOKE ALL ON SEQUENCE public.content_reports_id_seq,
    public.content_moderation_blocks_id_seq, public.content_report_review_history_id_seq FROM PUBLIC;
DO $acl$
DECLARE role_name TEXT;
BEGIN
    FOREACH role_name IN ARRAY ARRAY['anon', 'authenticated', 'service_role'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
            EXECUTE format('REVOKE ALL ON TABLE public.content_reports, public.content_moderation_blocks, public.content_report_review_history FROM %I', role_name);
            EXECUTE format('REVOKE ALL ON SEQUENCE public.content_reports_id_seq, public.content_moderation_blocks_id_seq, public.content_report_review_history_id_seq FROM %I', role_name);
            EXECUTE format('REVOKE ALL ON FUNCTION public.validate_content_report_target(), public.prevent_reported_content_delete(), public.prevent_reported_vaga_change(), public.guard_content_report_identity(), public.guard_content_moderation_block_identity(), public.prevent_reported_post_type_change(), public.reject_content_report_review_history_mutation(), public.prevent_admin_operation_audit_mutation() FROM %I', role_name);
        END IF;
    END LOOP;
END
$acl$;

DO $postflight$
DECLARE table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['content_reports', 'content_moderation_blocks', 'content_report_review_history'] LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relname = table_name AND c.relrowsecurity
              AND pg_get_userbyid(c.relowner) = current_user
        ) THEN RAISE EXCEPTION 'Postflight RLS/owner failure for public.%', table_name; END IF;
        IF EXISTS (
            SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
              CROSS JOIN LATERAL aclexplode(coalesce(c.relacl, acldefault('r', c.relowner))) a
            WHERE n.nspname = 'public' AND c.relname = table_name AND a.grantee = 0
        ) THEN RAISE EXCEPTION 'Public ACL remains on public.%', table_name; END IF;
        IF EXISTS (
            SELECT 1 FROM pg_roles r
            WHERE r.rolname IN ('anon', 'authenticated', 'service_role')
              AND has_table_privilege(r.rolname, format('public.%I', table_name),
                  'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN')
        ) THEN RAISE EXCEPTION 'REST role has table privileges on public.%', table_name; END IF;
    END LOOP;
    IF to_regclass('public.uq_content_report_pending_reporter_target') IS NULL
       OR to_regclass('public.idx_content_report_latest_reviewed_by_reporter_target') IS NULL
       OR to_regclass('public.uq_content_report_reporter_target') IS NOT NULL
       OR NOT EXISTS (
            SELECT 1 FROM pg_index i
             WHERE i.indexrelid='public.uq_content_report_pending_reporter_target'::regclass
               AND i.indisunique AND i.indisvalid AND i.indisready
               AND pg_get_indexdef(i.indexrelid) ILIKE '%(target_type, target_id, reporter_key)%'
               AND pg_get_expr(i.indpred,i.indrelid) ILIKE '%status%PENDING%'
       )
       OR NOT EXISTS (
            SELECT 1 FROM pg_index i
             WHERE i.indexrelid='public.idx_content_report_latest_reviewed_by_reporter_target'::regclass
               AND i.indisvalid AND i.indisready
               AND pg_get_indexdef(i.indexrelid) ILIKE '%(target_type, target_id, reporter_key, reviewed_at DESC, id DESC)%'
               AND pg_get_expr(i.indpred,i.indrelid) ILIKE '%DISMISSED%UPHELD%'
       )
       OR to_regclass('public.uq_content_blocks_active_source') IS NULL
       OR to_regclass('public.idx_content_reports_review_queue') IS NULL
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='public.content_report_review_history'::regclass AND tgname='trg_content_report_review_history_immutable' AND NOT tgisinternal AND tgenabled='O')
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_content_report_review_history_no_truncate' AND NOT tgisinternal)
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_content_reports_target_exists' AND NOT tgisinternal AND tgenabled='O')
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_content_moderation_blocks_target_exists' AND NOT tgisinternal AND tgenabled='O')
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='public.content_reports'::regclass AND tgname='trg_content_reports_identity_immutable' AND NOT tgisinternal AND tgenabled='O')
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='public.content_reports'::regclass AND tgname='trg_content_reports_identity_immutable' AND tgfoid='public.guard_content_report_identity()'::regprocedure)
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='public.content_moderation_blocks'::regclass AND tgname='trg_content_moderation_blocks_identity_immutable' AND NOT tgisinternal AND tgenabled='O')
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='public.content_moderation_blocks'::regclass AND tgname='trg_content_moderation_blocks_identity_immutable' AND tgfoid='public.guard_content_moderation_block_identity()'::regprocedure)
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='public.post'::regclass AND tgname='trg_post_preserve_content_report_type' AND NOT tgisinternal AND tgenabled='O')
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid='public.post'::regclass AND tgname='trg_post_preserve_content_report_type' AND tgfoid='public.prevent_reported_post_type_change()'::regprocedure)
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_oscs_preserve_content_reports' AND NOT tgisinternal AND tgenabled='O')
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_post_preserve_content_reports' AND NOT tgisinternal AND tgenabled='O')
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_vaga_preserve_content_reports' AND NOT tgisinternal AND tgenabled='O')
       OR NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='trg_vaga_preserve_content_report_link' AND NOT tgisinternal AND tgenabled='O') THEN
        RAISE EXCEPTION 'Postflight required index/trigger missing.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_policy WHERE polrelid IN (
          'public.content_reports'::regclass,'public.content_moderation_blocks'::regclass,
          'public.content_report_review_history'::regclass)) THEN
        RAISE EXCEPTION 'Postflight unexpected RLS policies exist on reporting tables.';
    END IF;
    IF (SELECT count(*) FROM pg_constraint WHERE conrelid='public.content_moderation_blocks'::regclass
          AND confrelid='public.usuarios'::regclass AND contype='f') <> 2 THEN
        RAISE EXCEPTION 'Postflight actor foreign keys are missing.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relname IN
          ('content_reports_id_seq','content_moderation_blocks_id_seq','content_report_review_history_id_seq')
          AND pg_get_userbyid(c.relowner) <> current_user
    ) THEN RAISE EXCEPTION 'Reporting identity sequence ownership diverges from the backend role.'; END IF;
    IF EXISTS (
        SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
          CROSS JOIN LATERAL aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a
        WHERE n.nspname='public' AND p.proname IN
          ('validate_content_report_target','prevent_reported_content_delete','prevent_reported_vaga_change','guard_content_report_identity','guard_content_moderation_block_identity','prevent_reported_post_type_change','reject_content_report_review_history_mutation','prevent_admin_operation_audit_mutation')
          AND a.grantee=0 AND a.privilege_type='EXECUTE'
    ) OR EXISTS (
        SELECT 1 FROM pg_roles r CROSS JOIN pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
        WHERE r.rolname IN ('anon','authenticated','service_role') AND n.nspname='public'
          AND p.proname IN ('validate_content_report_target','prevent_reported_content_delete','prevent_reported_vaga_change','guard_content_report_identity','guard_content_moderation_block_identity','prevent_reported_post_type_change','reject_content_report_review_history_mutation','prevent_admin_operation_audit_mutation')
          AND has_function_privilege(r.rolname,p.oid,'EXECUTE')
    ) OR EXISTS (
        SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
        WHERE n.nspname='public' AND p.proname IN
          ('validate_content_report_target','prevent_reported_content_delete','prevent_reported_vaga_change','guard_content_report_identity','guard_content_moderation_block_identity','prevent_reported_post_type_change','reject_content_report_review_history_mutation','prevent_admin_operation_audit_mutation')
          AND (pg_get_userbyid(p.proowner)<>current_user OR NOT has_function_privilege(current_user,p.oid,'EXECUTE'))
    ) THEN RAISE EXCEPTION 'Reporting function ownership/execute ACL diverges from the backend role.'; END IF;
    IF EXISTS (
        SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
          CROSS JOIN LATERAL aclexplode(coalesce(c.relacl, acldefault('S', c.relowner))) a
        WHERE n.nspname='public' AND c.relname IN
          ('content_reports_id_seq','content_moderation_blocks_id_seq','content_report_review_history_id_seq')
          AND a.grantee=0
    ) THEN RAISE EXCEPTION 'Public sequence ACL remains.'; END IF;
    IF EXISTS (
        SELECT 1 FROM pg_roles r
        WHERE r.rolname IN ('anon','authenticated','service_role') AND (
          has_sequence_privilege(r.rolname, 'public.content_reports_id_seq', 'USAGE') OR
          has_sequence_privilege(r.rolname, 'public.content_reports_id_seq', 'SELECT') OR
          has_sequence_privilege(r.rolname, 'public.content_reports_id_seq', 'UPDATE') OR
          has_sequence_privilege(r.rolname, 'public.content_moderation_blocks_id_seq', 'USAGE') OR
          has_sequence_privilege(r.rolname, 'public.content_moderation_blocks_id_seq', 'SELECT') OR
          has_sequence_privilege(r.rolname, 'public.content_moderation_blocks_id_seq', 'UPDATE') OR
          has_sequence_privilege(r.rolname, 'public.content_report_review_history_id_seq', 'USAGE') OR
          has_sequence_privilege(r.rolname, 'public.content_report_review_history_id_seq', 'SELECT') OR
          has_sequence_privilege(r.rolname, 'public.content_report_review_history_id_seq', 'UPDATE'))
    ) THEN RAISE EXCEPTION 'REST role has sequence privileges.'; END IF;
END
$postflight$;

COMMIT;

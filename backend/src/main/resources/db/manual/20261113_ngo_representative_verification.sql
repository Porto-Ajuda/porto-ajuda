-- Verificação documental de representação de ONGs (fase humana, sem IA).
-- One-shot: confirma a role de execução/Storage, cria um bucket dedicado privado e
-- não altera policies existentes. Uma segunda execução falha no preflight.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

DO $preflight$
DECLARE id_type TEXT;
BEGIN
    IF to_regclass('public.oscs') IS NULL OR to_regclass('public.usuarios') IS NULL THEN
        RAISE EXCEPTION 'Preflight: public.oscs e public.usuarios precisam existir.';
    END IF;
    SELECT data_type INTO id_type FROM information_schema.columns
     WHERE table_schema='public' AND table_name='oscs' AND column_name='id';
    IF id_type IS DISTINCT FROM 'integer' THEN RAISE EXCEPTION 'Preflight: tipo de public.oscs.id incompatível.'; END IF;
    SELECT data_type INTO id_type FROM information_schema.columns
     WHERE table_schema='public' AND table_name='usuarios' AND column_name='id';
    IF id_type IS DISTINCT FROM 'integer' THEN RAISE EXCEPTION 'Preflight: tipo de public.usuarios.id incompatível.'; END IF;
    IF NOT has_table_privilege(current_user, 'public.oscs', 'SELECT')
       OR NOT has_table_privilege(current_user, 'public.oscs', 'REFERENCES')
       OR NOT has_table_privilege(current_user, 'public.oscs', 'UPDATE')
       OR NOT has_table_privilege(current_user, 'public.usuarios', 'SELECT')
       OR NOT has_table_privilege(current_user, 'public.usuarios', 'REFERENCES') THEN
        RAISE EXCEPTION 'Preflight: a role executora precisa ler oscs/usuarios, referenciar seus IDs e bloquear oscs para capturar a população legada.';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_index i
        WHERE i.indrelid='public.oscs'::regclass AND i.indisunique AND i.indisvalid
          AND i.indnkeyatts=1
          AND EXISTS (SELECT 1 FROM pg_attribute a WHERE a.attrelid=i.indrelid
                      AND a.attnum=i.indkey[0] AND a.attname='cnpj')
    ) THEN
        RAISE EXCEPTION 'Preflight: public.oscs precisa de unicidade no CNPJ para concorrência de cadastros.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM unnest(ARRAY[
            'ngo_representative_verification_config',
            'ngo_representative_verification_retention_approval_events',
            'ngo_representative_verification_legacy_exemptions',
            'ngo_representative_verification_applications',
            'ngo_representative_verification_submissions',
            'ngo_representative_verification_evidence',
            'ngo_representative_verification_events',
            'ngo_representative_verification_evidence_disposal_events',
            'ngo_representative_verification_audit_metadata_redactions',
            'ngo_representative_verification_disposal_metadata_redactions'
        ]) AS t(name) WHERE to_regclass('public.' || t.name) IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'Preflight: objetos de verificação já existem; inspecione manualmente em vez de reaplicar.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
         WHERE n.nspname='public' AND (p.proname LIKE 'guard_ngo_representative_verification%'
             OR p.proname IN ('reject_ngo_representative_verification_config_mutation',
                              'guard_ngo_verification_submission_event_consistency',
                              'ngo_representative_is_publicly_eligible',
                              'request_ngo_verification_evidence_disposal',
                              'complete_ngo_verification_evidence_disposal',
                              'record_ngo_verification_evidence_disposal_failure',
                              'redact_ngo_verification_audit_event_metadata',
                              'redact_ngo_verification_disposal_event_metadata',
                              'approve_ngo_verification_retention_policy'))
    ) THEN
        RAISE EXCEPTION 'Preflight: funções de guarda de verificação já existem.';
    END IF;
    IF to_regclass('storage.buckets') IS NULL OR to_regclass('storage.objects') IS NULL THEN
        RAISE EXCEPTION 'Preflight: Supabase Storage não está instalado neste projeto.';
    END IF;
    IF NOT has_table_privilege(current_user, 'storage.buckets', 'SELECT,INSERT,UPDATE')
       OR NOT (SELECT rolbypassrls FROM pg_roles WHERE rolname=current_user) THEN
        RAISE EXCEPTION 'Preflight: a role executora precisa de acesso efetivo à tabela de buckets sob RLS do Supabase Storage.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE oid='storage.objects'::regclass AND relrowsecurity IS TRUE) THEN
        RAISE EXCEPTION 'Preflight: RLS precisa estar habilitado em storage.objects.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_policy p
        WHERE p.polrelid='storage.objects'::regclass
          AND (0=ANY(p.polroles) OR EXISTS (
              SELECT 1 FROM unnest(p.polroles) AS policy_role(role_oid)
               JOIN pg_roles r ON r.oid=policy_role.role_oid
               WHERE r.rolname IN ('anon','authenticated')))
    ) THEN
        RAISE EXCEPTION 'Preflight: storage.objects já tem policies para clientes; a migração não as altera. Revise uma policy restritiva do bucket com a role proprietária do Storage.';
    END IF;
    IF EXISTS (SELECT 1 FROM storage.buckets WHERE id='ngo-representative-evidence'
        AND (name IS DISTINCT FROM 'ngo-representative-evidence' OR public IS TRUE
             OR file_size_limit IS DISTINCT FROM 10485760
             OR allowed_mime_types IS DISTINCT FROM ARRAY['application/pdf','image/jpeg','image/png']::text[])) THEN
        RAISE EXCEPTION 'Preflight: o bucket de evidências já existe com configuração incompatível.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='anon')
       OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='authenticated')
       OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN
        RAISE EXCEPTION 'Preflight: roles Supabase anon/authenticated/service_role ausentes.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname IN ('anon','authenticated') AND rolbypassrls) THEN
        RAISE EXCEPTION 'Preflight: anon/authenticated não podem ignorar RLS em storage.objects.';
    END IF;
END
$preflight$;

-- The cutoff snapshots the legacy population. Rows without a verification application
-- remain grandfathered only when the ONG existed before this point.
CREATE TABLE public.ngo_representative_verification_config (
    id SMALLINT PRIMARY KEY CHECK (id=1),
    legacy_cutoff_at TIMESTAMPTZ NOT NULL,
    proposed_evidence_retention_months SMALLINT NOT NULL DEFAULT 12,
    proposed_audit_metadata_retention_years SMALLINT NOT NULL DEFAULT 5,
    retention_policy_status VARCHAR(20) NOT NULL DEFAULT 'PROPOSED',
    retention_policy_approved_at TIMESTAMPTZ NULL,
    retention_policy_approved_by_user_id INTEGER NULL,
    retention_policy_approval_reference VARCHAR(80) NULL,
    retention_policy_approval_event_id BIGINT NULL,
    CONSTRAINT ck_ngo_verification_retention_proposal CHECK
        (proposed_evidence_retention_months BETWEEN 1 AND 120
         AND proposed_audit_metadata_retention_years BETWEEN 1 AND 50),
    CONSTRAINT ck_ngo_verification_retention_status CHECK
        (retention_policy_status IN ('PROPOSED','APPROVED')),
    CONSTRAINT ck_ngo_verification_retention_approval CHECK
        ((retention_policy_status='PROPOSED' AND retention_policy_approved_at IS NULL
          AND retention_policy_approved_by_user_id IS NULL AND retention_policy_approval_reference IS NULL
          AND retention_policy_approval_event_id IS NULL)
         OR (retention_policy_status='APPROVED' AND retention_policy_approved_at IS NOT NULL
             AND retention_policy_approval_reference IS NOT NULL
             AND retention_policy_approval_event_id IS NOT NULL
             AND length(btrim(retention_policy_approval_reference)) BETWEEN 1 AND 80)),
    CONSTRAINT fk_ngo_verification_retention_approver FOREIGN KEY (retention_policy_approved_by_user_id)
        REFERENCES public.usuarios(id) ON DELETE SET NULL
);
INSERT INTO public.ngo_representative_verification_config (id, legacy_cutoff_at)
VALUES (1, clock_timestamp());

CREATE TABLE public.ngo_representative_verification_retention_approval_events (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    actor_user_id INTEGER NULL,
    approval_reference VARCHAR(80) NOT NULL,
    evidence_retention_months SMALLINT NOT NULL,
    audit_metadata_retention_years SMALLINT NOT NULL,
    justification TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT fk_ngo_verification_retention_approval_actor FOREIGN KEY (actor_user_id)
        REFERENCES public.usuarios(id) ON DELETE SET NULL,
    CONSTRAINT ck_ngo_verification_retention_event_reference CHECK
        (length(btrim(approval_reference)) BETWEEN 1 AND 80),
    CONSTRAINT ck_ngo_verification_retention_event_periods CHECK
        (evidence_retention_months BETWEEN 1 AND 120 AND audit_metadata_retention_years BETWEEN 1 AND 50),
    CONSTRAINT ck_ngo_verification_retention_event_justification CHECK
        (length(btrim(justification)) BETWEEN 1 AND 2000)
);
ALTER TABLE public.ngo_representative_verification_config
    ADD CONSTRAINT fk_ngo_verification_retention_approval_event
        FOREIGN KEY (retention_policy_approval_event_id)
        REFERENCES public.ngo_representative_verification_retention_approval_events(id) ON DELETE RESTRICT;

CREATE TABLE public.ngo_representative_verification_legacy_exemptions (
    osc_id INTEGER PRIMARY KEY,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT fk_ngo_verification_legacy_osc FOREIGN KEY (osc_id)
        REFERENCES public.oscs(id) ON DELETE RESTRICT
);
-- Snapshot the actual pre-existing population. No NGO status or visibility column is changed.
LOCK TABLE public.oscs IN SHARE ROW EXCLUSIVE MODE;
INSERT INTO public.ngo_representative_verification_legacy_exemptions (osc_id)
SELECT id FROM public.oscs;

CREATE TABLE public.ngo_representative_verification_applications (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    osc_id INTEGER NOT NULL,
    application_number SMALLINT NOT NULL DEFAULT 1,
    parent_application_id BIGINT NULL,
    applicant_user_id INTEGER NULL,
    representation_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    current_submission_version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_ngo_verification_application_cycle UNIQUE (osc_id, application_number),
    CONSTRAINT fk_ngo_verification_application_osc FOREIGN KEY (osc_id)
        REFERENCES public.oscs(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ngo_verification_application_parent FOREIGN KEY (parent_application_id)
        REFERENCES public.ngo_representative_verification_applications(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ngo_verification_application_applicant FOREIGN KEY (applicant_user_id)
        REFERENCES public.usuarios(id) ON DELETE SET NULL,
    CONSTRAINT ck_ngo_verification_representation CHECK
        (representation_type IN ('LEGAL_REPRESENTATIVE','AUTHORIZED_DELEGATE')),
    CONSTRAINT ck_ngo_verification_status CHECK
        (status IN ('PENDING','UNDER_REVIEW','INFORMATION_REQUESTED','APPROVED','REJECTED')),
    -- Ordinary requests are limited to four versions by the service. An explicit
    -- ADMIN_REOPENED audit event may authorize one additional correction version.
    CONSTRAINT ck_ngo_verification_version CHECK (current_submission_version BETWEEN 0 AND 32767),
    CONSTRAINT ck_ngo_verification_application_cycle CHECK
        (application_number BETWEEN 1 AND 3
         AND ((application_number=1 AND parent_application_id IS NULL)
              OR (application_number>1 AND parent_application_id IS NOT NULL)))
);
CREATE UNIQUE INDEX uq_ngo_verification_one_active_application
    ON public.ngo_representative_verification_applications(osc_id)
    WHERE status IN ('PENDING','UNDER_REVIEW','INFORMATION_REQUESTED');

CREATE TABLE public.ngo_representative_verification_submissions (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    application_id BIGINT NOT NULL,
    version_number INTEGER NOT NULL,
    submitted_by_user_id INTEGER NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    submitted_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_ngo_verification_submission_version UNIQUE (application_id, version_number),
    CONSTRAINT fk_ngo_verification_submission_application FOREIGN KEY (application_id)
        REFERENCES public.ngo_representative_verification_applications(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ngo_verification_submission_user FOREIGN KEY (submitted_by_user_id)
        REFERENCES public.usuarios(id) ON DELETE SET NULL,
    CONSTRAINT ck_ngo_verification_submission_version CHECK (version_number > 0)
);
CREATE UNIQUE INDEX uq_ngo_verification_single_draft
    ON public.ngo_representative_verification_submissions(application_id)
    WHERE submitted_at IS NULL;

CREATE TABLE public.ngo_representative_verification_evidence (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    storage_key TEXT NULL,
    media_type VARCHAR(40) NULL,
    size_bytes BIGINT NULL,
    uploaded_by_user_id INTEGER NULL,
    uploaded_at TIMESTAMPTZ NULL DEFAULT clock_timestamp(),
    disposal_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    disposal_requested_at TIMESTAMPTZ NULL,
    disposed_at TIMESTAMPTZ NULL,
    disposal_request_event_id BIGINT NULL,
    disposal_completed_event_id BIGINT NULL,
    CONSTRAINT uq_ngo_verification_evidence_key UNIQUE (storage_key),
    CONSTRAINT fk_ngo_verification_evidence_submission FOREIGN KEY (submission_id)
        REFERENCES public.ngo_representative_verification_submissions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ngo_verification_evidence_uploader FOREIGN KEY (uploaded_by_user_id)
        REFERENCES public.usuarios(id) ON DELETE SET NULL,
    CONSTRAINT ck_ngo_verification_document_type CHECK
        (document_type IN ('REPRESENTATION_PROOF','DELEGATION_AUTHORIZATION')),
    CONSTRAINT ck_ngo_verification_media_type CHECK
        (media_type IS NULL OR media_type IN ('application/pdf','image/jpeg','image/png')),
    CONSTRAINT ck_ngo_verification_evidence_size CHECK
        (size_bytes IS NULL OR size_bytes BETWEEN 1 AND 10485760),
    CONSTRAINT ck_ngo_verification_evidence_disposal CHECK
        ((disposal_status='ACTIVE' AND storage_key IS NOT NULL AND media_type IS NOT NULL
          AND size_bytes IS NOT NULL AND uploaded_at IS NOT NULL
          AND disposal_requested_at IS NULL AND disposed_at IS NULL
          AND disposal_request_event_id IS NULL AND disposal_completed_event_id IS NULL)
         OR (disposal_status='DISPOSAL_REQUESTED' AND storage_key IS NOT NULL AND media_type IS NOT NULL
             AND size_bytes IS NOT NULL AND uploaded_at IS NOT NULL
             AND disposal_requested_at IS NOT NULL AND disposed_at IS NULL
             AND disposal_request_event_id IS NOT NULL AND disposal_completed_event_id IS NULL)
         OR (disposal_status='DISPOSED' AND storage_key IS NULL AND media_type IS NULL AND size_bytes IS NULL
             AND uploaded_by_user_id IS NULL AND uploaded_at IS NULL
             AND disposal_requested_at IS NOT NULL AND disposed_at IS NOT NULL
             AND disposal_request_event_id IS NOT NULL AND disposal_completed_event_id IS NOT NULL)),
    CONSTRAINT ck_ngo_verification_private_storage_key CHECK
        (storage_key IS NULL OR (storage_key !~* '^https?://' AND storage_key LIKE 'applications/%'))
);

CREATE TABLE public.ngo_representative_verification_events (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    application_id BIGINT NOT NULL,
    actor_user_id INTEGER NULL,
    actor_type_snapshot VARCHAR(24) NOT NULL,
    action VARCHAR(40) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    justification TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    metadata_redacted_at TIMESTAMPTZ NULL,
    metadata_redaction_id BIGINT NULL,
    CONSTRAINT fk_ngo_verification_event_application FOREIGN KEY (application_id)
        REFERENCES public.ngo_representative_verification_applications(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ngo_verification_event_actor FOREIGN KEY (actor_user_id)
        REFERENCES public.usuarios(id) ON DELETE SET NULL,
    CONSTRAINT ck_ngo_verification_event_action CHECK
        (action IN ('APPLICATION_CREATED','REAPPLICATION_CREATED','SUBMITTED','REVIEW_STARTED','INFORMATION_REQUESTED','APPROVED','REJECTED','RESUBMITTED','ADMIN_REOPENED')),
    CONSTRAINT ck_ngo_verification_event_actor_type CHECK
        (actor_type_snapshot IN ('APPLICANT','ADMINISTRATOR','SYSTEM')),
    CONSTRAINT ck_ngo_verification_event_from_status CHECK
        (from_status IS NULL OR from_status IN ('PENDING','UNDER_REVIEW','INFORMATION_REQUESTED','APPROVED','REJECTED')),
    CONSTRAINT ck_ngo_verification_event_to_status CHECK
        (to_status IN ('PENDING','UNDER_REVIEW','INFORMATION_REQUESTED','APPROVED','REJECTED')),
    CONSTRAINT ck_ngo_verification_decision_reason CHECK
        (action NOT IN ('INFORMATION_REQUESTED','APPROVED','REJECTED','ADMIN_REOPENED')
         OR (justification IS NOT NULL AND length(btrim(justification)) BETWEEN 1 AND 2000)
         OR (metadata_redacted_at IS NOT NULL AND actor_user_id IS NULL AND justification IS NULL)),
    CONSTRAINT ck_ngo_verification_event_redaction_state CHECK
        ((metadata_redacted_at IS NULL AND metadata_redaction_id IS NULL)
         OR (metadata_redacted_at IS NOT NULL AND metadata_redaction_id IS NOT NULL
             AND actor_user_id IS NULL AND justification IS NULL))
);

CREATE TABLE public.ngo_representative_verification_audit_metadata_redactions (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    event_id BIGINT NOT NULL UNIQUE,
    redacted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    policy_reference VARCHAR(80) NOT NULL,
    reason_code VARCHAR(32) NOT NULL DEFAULT 'RETENTION_EXPIRY',
    CONSTRAINT fk_ngo_verification_audit_redaction_event FOREIGN KEY (event_id)
        REFERENCES public.ngo_representative_verification_events(id) ON DELETE RESTRICT,
    CONSTRAINT ck_ngo_verification_audit_redaction_reference CHECK
        (length(btrim(policy_reference)) BETWEEN 1 AND 80),
    CONSTRAINT ck_ngo_verification_audit_redaction_reason CHECK (reason_code='RETENTION_EXPIRY')
);
ALTER TABLE public.ngo_representative_verification_events
    ADD CONSTRAINT fk_ngo_verification_event_redaction FOREIGN KEY (metadata_redaction_id)
        REFERENCES public.ngo_representative_verification_audit_metadata_redactions(id) ON DELETE RESTRICT;

-- Disposal events are separate immutable records. The proposed policy is not approved
-- in this migration, so disposal functions remain guarded until a reviewed approval path exists.
CREATE TABLE public.ngo_representative_verification_evidence_disposal_events (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    evidence_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    request_event_id BIGINT NULL,
    event_type VARCHAR(24) NOT NULL,
    actor_user_id INTEGER NULL,
    actor_type_snapshot VARCHAR(24) NOT NULL DEFAULT 'ADMINISTRATOR',
    reason TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    metadata_redacted_at TIMESTAMPTZ NULL,
    metadata_redaction_id BIGINT NULL,
    CONSTRAINT fk_ngo_verification_disposal_evidence FOREIGN KEY (evidence_id)
        REFERENCES public.ngo_representative_verification_evidence(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ngo_verification_disposal_application FOREIGN KEY (application_id)
        REFERENCES public.ngo_representative_verification_applications(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ngo_verification_disposal_actor FOREIGN KEY (actor_user_id)
        REFERENCES public.usuarios(id) ON DELETE SET NULL,
    CONSTRAINT fk_ngo_verification_disposal_request FOREIGN KEY (request_event_id)
        REFERENCES public.ngo_representative_verification_evidence_disposal_events(id) ON DELETE RESTRICT,
    CONSTRAINT ck_ngo_verification_disposal_type CHECK
        (event_type IN ('REQUESTED','FAILED','COMPLETED')),
    CONSTRAINT ck_ngo_verification_disposal_actor_type CHECK
        (actor_type_snapshot IN ('ADMINISTRATOR','SYSTEM')),
    CONSTRAINT ck_ngo_verification_disposal_request_reference CHECK
        ((event_type='REQUESTED' AND request_event_id IS NULL)
         OR (event_type IN ('FAILED','COMPLETED') AND request_event_id IS NOT NULL)),
    CONSTRAINT ck_ngo_verification_disposal_reason CHECK
        ((reason IS NOT NULL AND length(btrim(reason)) BETWEEN 1 AND 2000
          AND metadata_redacted_at IS NULL AND metadata_redaction_id IS NULL)
         OR (reason IS NULL AND actor_user_id IS NULL
             AND metadata_redacted_at IS NOT NULL AND metadata_redaction_id IS NOT NULL))
);
CREATE UNIQUE INDEX uq_ngo_verification_evidence_disposal_request
    ON public.ngo_representative_verification_evidence_disposal_events(evidence_id)
    WHERE event_type='REQUESTED';
CREATE UNIQUE INDEX uq_ngo_verification_evidence_disposal_completed
    ON public.ngo_representative_verification_evidence_disposal_events(evidence_id)
    WHERE event_type='COMPLETED';
ALTER TABLE public.ngo_representative_verification_evidence
    ADD CONSTRAINT fk_ngo_verification_evidence_disposal_request
        FOREIGN KEY (disposal_request_event_id)
        REFERENCES public.ngo_representative_verification_evidence_disposal_events(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_ngo_verification_evidence_disposal_completed
        FOREIGN KEY (disposal_completed_event_id)
        REFERENCES public.ngo_representative_verification_evidence_disposal_events(id) ON DELETE RESTRICT;

CREATE TABLE public.ngo_representative_verification_disposal_metadata_redactions (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    disposal_event_id BIGINT NOT NULL UNIQUE,
    redacted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    policy_reference VARCHAR(80) NOT NULL,
    reason_code VARCHAR(32) NOT NULL DEFAULT 'RETENTION_EXPIRY',
    CONSTRAINT fk_ngo_verification_disposal_redaction_event FOREIGN KEY (disposal_event_id)
        REFERENCES public.ngo_representative_verification_evidence_disposal_events(id) ON DELETE RESTRICT,
    CONSTRAINT ck_ngo_verification_disposal_redaction_reference CHECK
        (length(btrim(policy_reference)) BETWEEN 1 AND 80),
    CONSTRAINT ck_ngo_verification_disposal_redaction_reason CHECK (reason_code='RETENTION_EXPIRY')
);
ALTER TABLE public.ngo_representative_verification_evidence_disposal_events
    ADD CONSTRAINT fk_ngo_verification_disposal_event_redaction FOREIGN KEY (metadata_redaction_id)
        REFERENCES public.ngo_representative_verification_disposal_metadata_redactions(id) ON DELETE RESTRICT;

CREATE OR REPLACE FUNCTION public.ngo_representative_is_publicly_eligible(target_osc_id INTEGER)
RETURNS BOOLEAN LANGUAGE SQL STABLE SECURITY INVOKER
SET search_path = pg_catalog, public
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM public.oscs o
          CROSS JOIN public.ngo_representative_verification_config c
         WHERE o.id=target_osc_id AND c.id=1
           AND (EXISTS (SELECT 1 FROM public.ngo_representative_verification_legacy_exemptions l WHERE l.osc_id=o.id)
                OR EXISTS (SELECT 1 FROM public.ngo_representative_verification_applications a
                           WHERE a.osc_id=o.id AND a.status='APPROVED'))
    )
$$;

CREATE INDEX idx_ngo_verification_queue ON public.ngo_representative_verification_applications(status, updated_at, id);
CREATE INDEX idx_ngo_verification_applicant ON public.ngo_representative_verification_applications(applicant_user_id, updated_at DESC);
CREATE INDEX idx_ngo_verification_submitted_queue ON public.ngo_representative_verification_submissions(submitted_at, application_id)
    WHERE submitted_at IS NOT NULL;
CREATE INDEX idx_ngo_verification_evidence_submission ON public.ngo_representative_verification_evidence(submission_id, uploaded_at, id);
CREATE INDEX idx_ngo_verification_events_latest ON public.ngo_representative_verification_events(application_id, created_at DESC, id DESC);
CREATE INDEX idx_ngo_verification_retention_approvals_latest
    ON public.ngo_representative_verification_retention_approval_events(created_at DESC, id DESC);
CREATE INDEX idx_ngo_verification_disposal_events_evidence
    ON public.ngo_representative_verification_evidence_disposal_events(evidence_id, created_at, id);
CREATE INDEX idx_ngo_verification_disposal_events_application
    ON public.ngo_representative_verification_evidence_disposal_events(application_id, created_at, id);

CREATE OR REPLACE FUNCTION public.guard_ngo_representative_verification_application()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' OR TG_OP='TRUNCATE' THEN
        RAISE EXCEPTION 'Verificação e histórico de representação não podem ser removidos.';
    END IF;
    IF TG_OP='UPDATE' THEN
        IF NEW.id IS DISTINCT FROM OLD.id OR NEW.osc_id IS DISTINCT FROM OLD.osc_id
           OR NEW.application_number IS DISTINCT FROM OLD.application_number
           OR NEW.parent_application_id IS DISTINCT FROM OLD.parent_application_id
           OR NEW.representation_type IS DISTINCT FROM OLD.representation_type
           OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
            RAISE EXCEPTION 'A identidade da solicitação de verificação é imutável.';
        END IF;
        IF NEW.applicant_user_id IS DISTINCT FROM OLD.applicant_user_id AND NOT (
            OLD.applicant_user_id IS NOT NULL AND NEW.applicant_user_id IS NULL
            AND pg_trigger_depth()>1 AND NEW.status IS NOT DISTINCT FROM OLD.status
            AND NEW.current_submission_version IS NOT DISTINCT FROM OLD.current_submission_version
            AND NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at) THEN
            RAISE EXCEPTION 'A conta solicitante só pode ser desvinculada pela exclusão autorizada da conta após o prazo de retenção.';
        END IF;
        IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
              (OLD.status='PENDING' AND NEW.status='UNDER_REVIEW')
           OR (OLD.status='UNDER_REVIEW' AND NEW.status IN ('INFORMATION_REQUESTED','APPROVED','REJECTED'))
           OR (OLD.status='INFORMATION_REQUESTED' AND NEW.status='PENDING')
           OR (OLD.status='REJECTED' AND NEW.status='INFORMATION_REQUESTED')) THEN
            RAISE EXCEPTION 'Transição de verificação inválida.';
        END IF;
        IF NEW.current_submission_version < OLD.current_submission_version
           OR NEW.current_submission_version > OLD.current_submission_version + 1 THEN
            RAISE EXCEPTION 'Versão documental inválida.';
        END IF;
        IF NEW.current_submission_version=OLD.current_submission_version+1 THEN
            IF NOT ((OLD.status='PENDING' AND OLD.current_submission_version=0)
                    OR OLD.status='INFORMATION_REQUESTED')
               OR NOT EXISTS (SELECT 1 FROM public.ngo_representative_verification_submissions s
                   WHERE s.application_id=OLD.id AND s.version_number=NEW.current_submission_version
                     AND s.submitted_at IS NULL) THEN
                RAISE EXCEPTION 'Nova versão documental não permitida neste estado.';
            END IF;
            IF NEW.current_submission_version > 4 AND
               (SELECT count(*) FROM public.ngo_representative_verification_events e
                 WHERE e.application_id=OLD.id AND e.action='ADMIN_REOPENED') < NEW.current_submission_version - 4 THEN
                RAISE EXCEPTION 'Versões adicionais exigem reabertura administrativa auditada.';
            END IF;
        END IF;
        NEW.updated_at := clock_timestamp();
        RETURN NEW;
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION public.guard_ngo_representative_verification_event_consistency()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' THEN
        IF (NEW.application_number=1 AND NEW.parent_application_id IS NOT NULL)
           OR (NEW.application_number>1 AND NOT EXISTS (
                SELECT 1 FROM public.ngo_representative_verification_applications parent
                 WHERE parent.id=NEW.parent_application_id AND parent.osc_id=NEW.osc_id
                   AND parent.application_number=NEW.application_number-1 AND parent.status='REJECTED')) THEN
            RAISE EXCEPTION 'A nova solicitação precisa suceder a recusa anterior da mesma ONG.';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM public.ngo_representative_verification_events e
            WHERE e.application_id=NEW.id
              AND (e.actor_user_id=NEW.applicant_user_id OR
                   (e.actor_user_id IS NULL AND NEW.applicant_user_id IS NOT NULL
                    AND NOT EXISTS (SELECT 1 FROM public.usuarios u WHERE u.id=NEW.applicant_user_id)
                    AND EXISTS (SELECT 1 FROM public.ngo_representative_verification_applications current_app
                                 WHERE current_app.id=NEW.id AND current_app.applicant_user_id IS NULL)))
              AND e.action=CASE WHEN NEW.application_number=1 THEN 'APPLICATION_CREATED' ELSE 'REAPPLICATION_CREATED' END
              AND e.from_status IS NULL AND e.to_status='PENDING'
              AND e.created_at >= transaction_timestamp()) THEN
            RAISE EXCEPTION 'A solicitação deve possuir evento de criação no mesmo commit.';
        END IF;
    ELSIF NEW.status IS DISTINCT FROM OLD.status THEN
        IF OLD.status='REJECTED' AND NEW.status='INFORMATION_REQUESTED'
           AND NOT EXISTS (SELECT 1 FROM public.ngo_representative_verification_events e
                JOIN public.ngo_representative_verification_applications a ON a.id=e.application_id
               WHERE e.application_id=NEW.id AND e.action='ADMIN_REOPENED'
                 AND e.from_status='REJECTED' AND e.to_status='INFORMATION_REQUESTED'
                 AND e.actor_type_snapshot='ADMINISTRATOR' AND e.actor_user_id IS NOT NULL
                 AND e.actor_user_id<>a.applicant_user_id
                 AND e.justification IS NOT NULL AND length(btrim(e.justification)) BETWEEN 1 AND 2000
                 AND e.created_at>=transaction_timestamp()) THEN
            RAISE EXCEPTION 'A reabertura deve possuir justificativa e revisor independente no mesmo commit.';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM public.ngo_representative_verification_events e
            WHERE e.application_id=NEW.id AND e.from_status=OLD.status AND e.to_status=NEW.status
              AND e.action IN ('REVIEW_STARTED','INFORMATION_REQUESTED','APPROVED','REJECTED','RESUBMITTED','ADMIN_REOPENED')
              AND e.created_at >= transaction_timestamp()) THEN
            RAISE EXCEPTION 'A mudança de status deve possuir evento de revisão no mesmo commit.';
        END IF;
    END IF;
    RETURN NULL;
END
$$;

CREATE OR REPLACE FUNCTION public.guard_ngo_representative_verification_submission()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE','TRUNCATE') THEN
        RAISE EXCEPTION 'Versões documentais não podem ser removidas.';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.submitted_by_user_id IS NULL THEN
            RAISE EXCEPTION 'A conta que enviou a versão documental é obrigatória.';
        END IF;
        RETURN NEW;
    END IF;
    IF TG_OP='UPDATE' THEN
        IF NEW.submitted_by_user_id IS DISTINCT FROM OLD.submitted_by_user_id THEN
            IF OLD.submitted_by_user_id IS NOT NULL AND NEW.submitted_by_user_id IS NULL
               AND pg_trigger_depth()>1
               AND NEW.id IS NOT DISTINCT FROM OLD.id
               AND NEW.application_id IS NOT DISTINCT FROM OLD.application_id
               AND NEW.version_number IS NOT DISTINCT FROM OLD.version_number
               AND NEW.created_at IS NOT DISTINCT FROM OLD.created_at
               AND NEW.submitted_at IS NOT DISTINCT FROM OLD.submitted_at THEN
                RETURN NEW;
            END IF;
            RAISE EXCEPTION 'A autoria da versão só pode ser desvinculada pela exclusão autorizada da conta.';
        END IF;
        IF NEW.id IS DISTINCT FROM OLD.id OR NEW.application_id IS DISTINCT FROM OLD.application_id
           OR NEW.version_number IS DISTINCT FROM OLD.version_number
           OR NEW.created_at IS DISTINCT FROM OLD.created_at
           OR OLD.submitted_at IS NOT NULL
           OR NEW.submitted_at IS NULL THEN
            RAISE EXCEPTION 'A versão enviada é imutável; somente o primeiro envio pode registrar submitted_at.';
        END IF;
        RETURN NEW;
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION public.guard_ngo_verification_submission_event_consistency()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.submitted_at IS NULL AND NEW.submitted_at IS NOT NULL THEN
        IF NOT EXISTS (SELECT 1 FROM public.ngo_representative_verification_events e
            WHERE e.application_id=NEW.application_id AND e.actor_user_id=NEW.submitted_by_user_id
              AND e.action IN ('SUBMITTED','RESUBMITTED')
              AND e.created_at >= transaction_timestamp()) THEN
            RAISE EXCEPTION 'O envio de uma versão deve possuir evento de auditoria no mesmo commit.';
        END IF;
    END IF;
    RETURN NULL;
END
$$;

CREATE OR REPLACE FUNCTION public.guard_ngo_representative_verification_application_parent()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.applicant_user_id IS NULL THEN
        RAISE EXCEPTION 'A conta solicitante é obrigatória ao criar uma aplicação.';
    END IF;
    IF NEW.application_number=1 THEN
        IF NEW.parent_application_id IS NOT NULL THEN
            RAISE EXCEPTION 'A solicitação inicial não pode apontar para um ciclo anterior.';
        END IF;
    ELSIF NOT EXISTS (
        SELECT 1 FROM public.ngo_representative_verification_applications parent
         WHERE parent.id=NEW.parent_application_id AND parent.osc_id=NEW.osc_id
           AND parent.application_number=NEW.application_number-1 AND parent.status='REJECTED'
    ) THEN
        RAISE EXCEPTION 'Reaplicação inválida: é necessário haver recusa anterior da mesma ONG.';
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION public.reject_ngo_representative_verification_history_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Evidências e histórico de verificação são append-only.';
END
$$;

CREATE OR REPLACE FUNCTION public.reject_ngo_representative_verification_config_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'O marco de compatibilidade de ONGs legadas é imutável.';
END
$$;

CREATE OR REPLACE FUNCTION public.guard_ngo_representative_verification_retention_approval_event()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE','TRUNCATE') THEN
        RAISE EXCEPTION 'Eventos de aprovação de retenção não podem ser removidos.';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.actor_user_id IS NULL THEN RAISE EXCEPTION 'O ator da aprovação é obrigatório.'; END IF;
        RETURN NEW;
    END IF;
    IF TG_OP='UPDATE' AND OLD.actor_user_id IS NOT NULL AND NEW.actor_user_id IS NULL
       AND pg_trigger_depth()>1
       AND NEW.id IS NOT DISTINCT FROM OLD.id
       AND NEW.approval_reference IS NOT DISTINCT FROM OLD.approval_reference
       AND NEW.evidence_retention_months IS NOT DISTINCT FROM OLD.evidence_retention_months
       AND NEW.audit_metadata_retention_years IS NOT DISTINCT FROM OLD.audit_metadata_retention_years
       AND NEW.justification IS NOT DISTINCT FROM OLD.justification
       AND NEW.created_at IS NOT DISTINCT FROM OLD.created_at THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Evento de aprovação é append-only; somente o vínculo de conta pode ser removido pela exclusão autorizada.';
END
$$;

CREATE OR REPLACE FUNCTION public.guard_ngo_representative_verification_config_update()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'A configuração de retenção e o corte legado são imutáveis.'; END IF;
    IF TG_OP='UPDATE' AND OLD.retention_policy_approved_by_user_id IS NOT NULL
       AND NEW.retention_policy_approved_by_user_id IS NULL AND pg_trigger_depth()>1
       AND NEW.id IS NOT DISTINCT FROM OLD.id
       AND NEW.legacy_cutoff_at IS NOT DISTINCT FROM OLD.legacy_cutoff_at
       AND NEW.proposed_evidence_retention_months IS NOT DISTINCT FROM OLD.proposed_evidence_retention_months
       AND NEW.proposed_audit_metadata_retention_years IS NOT DISTINCT FROM OLD.proposed_audit_metadata_retention_years
       AND NEW.retention_policy_status IS NOT DISTINCT FROM OLD.retention_policy_status
       AND NEW.retention_policy_approved_at IS NOT DISTINCT FROM OLD.retention_policy_approved_at
       AND NEW.retention_policy_approval_reference IS NOT DISTINCT FROM OLD.retention_policy_approval_reference
       AND NEW.retention_policy_approval_event_id IS NOT DISTINCT FROM OLD.retention_policy_approval_event_id THEN
        RETURN NEW;
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.legacy_cutoff_at IS DISTINCT FROM OLD.legacy_cutoff_at
       OR NEW.proposed_evidence_retention_months IS DISTINCT FROM OLD.proposed_evidence_retention_months
       OR NEW.proposed_audit_metadata_retention_years IS DISTINCT FROM OLD.proposed_audit_metadata_retention_years
       OR OLD.retention_policy_status<>'PROPOSED' OR NEW.retention_policy_status<>'APPROVED'
       OR OLD.retention_policy_approved_at IS NOT NULL OR NEW.retention_policy_approved_at IS NULL
       OR NEW.retention_policy_approved_by_user_id IS NULL OR NEW.retention_policy_approval_reference IS NULL
       OR NEW.retention_policy_approval_event_id IS NULL
       OR NOT EXISTS (SELECT 1 FROM public.ngo_representative_verification_retention_approval_events e
           WHERE e.id=NEW.retention_policy_approval_event_id
             AND e.actor_user_id=NEW.retention_policy_approved_by_user_id
             AND e.approval_reference=NEW.retention_policy_approval_reference
             AND e.evidence_retention_months=OLD.proposed_evidence_retention_months
             AND e.audit_metadata_retention_years=OLD.proposed_audit_metadata_retention_years
             AND e.created_at>=transaction_timestamp()) THEN
        RAISE EXCEPTION 'A retenção só pode ser ativada por uma aprovação registrada e manter os prazos propostos.';
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION public.guard_ngo_representative_verification_evidence()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE','TRUNCATE') THEN
        RAISE EXCEPTION 'Registros de evidência não podem ser removidos.';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.uploaded_by_user_id IS NULL THEN RAISE EXCEPTION 'A conta que enviou a evidência é obrigatória.'; END IF;
        RETURN NEW;
    END IF;
    IF TG_OP='UPDATE' THEN
        IF NEW.id IS DISTINCT FROM OLD.id OR NEW.submission_id IS DISTINCT FROM OLD.submission_id
           OR NEW.document_type IS DISTINCT FROM OLD.document_type THEN
            RAISE EXCEPTION 'A identidade da evidência é imutável.';
        END IF;
        IF NEW.uploaded_by_user_id IS DISTINCT FROM OLD.uploaded_by_user_id THEN
            IF OLD.uploaded_by_user_id IS NOT NULL AND NEW.uploaded_by_user_id IS NULL
               AND pg_trigger_depth()>1
               AND NEW.storage_key IS NOT DISTINCT FROM OLD.storage_key
               AND NEW.media_type IS NOT DISTINCT FROM OLD.media_type
               AND NEW.size_bytes IS NOT DISTINCT FROM OLD.size_bytes
               AND NEW.uploaded_at IS NOT DISTINCT FROM OLD.uploaded_at
               AND NEW.disposal_status IS NOT DISTINCT FROM OLD.disposal_status
               AND NEW.disposal_requested_at IS NOT DISTINCT FROM OLD.disposal_requested_at
               AND NEW.disposed_at IS NOT DISTINCT FROM OLD.disposed_at
               AND NEW.disposal_request_event_id IS NOT DISTINCT FROM OLD.disposal_request_event_id
               AND NEW.disposal_completed_event_id IS NOT DISTINCT FROM OLD.disposal_completed_event_id THEN
                RETURN NEW;
            END IF;
            RAISE EXCEPTION 'O vínculo de upload só pode ser removido pela exclusão autorizada da conta.';
        END IF;
        IF OLD.disposal_status='ACTIVE' AND NEW.disposal_status='DISPOSAL_REQUESTED' THEN
            IF NEW.storage_key IS DISTINCT FROM OLD.storage_key
               OR NEW.media_type IS DISTINCT FROM OLD.media_type
               OR NEW.size_bytes IS DISTINCT FROM OLD.size_bytes
               OR NEW.uploaded_by_user_id IS DISTINCT FROM OLD.uploaded_by_user_id
               OR NEW.uploaded_at IS DISTINCT FROM OLD.uploaded_at
               OR NEW.disposal_requested_at IS NULL OR NEW.disposed_at IS NOT NULL
               OR NEW.disposal_request_event_id IS NULL OR NEW.disposal_completed_event_id IS NOT NULL
               OR NOT EXISTS (SELECT 1 FROM public.ngo_representative_verification_evidence_disposal_events d
                   WHERE d.id=NEW.disposal_request_event_id AND d.evidence_id=NEW.id
                     AND d.event_type='REQUESTED' AND d.created_at>=transaction_timestamp()) THEN
                RAISE EXCEPTION 'Pedido de descarte inconsistente; a evidência foi mantida.';
            END IF;
            RETURN NEW;
        ELSIF OLD.disposal_status='DISPOSAL_REQUESTED' AND NEW.disposal_status='DISPOSED' THEN
            IF NEW.storage_key IS NOT NULL OR NEW.media_type IS NOT NULL OR NEW.size_bytes IS NOT NULL
               OR NEW.uploaded_by_user_id IS NOT NULL OR NEW.uploaded_at IS NOT NULL
               OR NEW.disposal_requested_at IS DISTINCT FROM OLD.disposal_requested_at
               OR NEW.disposal_request_event_id IS DISTINCT FROM OLD.disposal_request_event_id
               OR NEW.disposed_at IS NULL OR NEW.disposal_completed_event_id IS NULL
               OR NOT EXISTS (SELECT 1 FROM public.ngo_representative_verification_evidence_disposal_events d
                   WHERE d.id=NEW.disposal_completed_event_id AND d.evidence_id=NEW.id
                     AND d.event_type='COMPLETED' AND d.request_event_id=OLD.disposal_request_event_id
                     AND d.created_at>=transaction_timestamp()) THEN
                RAISE EXCEPTION 'Conclusão de descarte inconsistente; o histórico permanece preservado.';
            END IF;
            RETURN NEW;
        END IF;
        RAISE EXCEPTION 'Somente o procedimento controlado de descarte pode alterar metadados de evidência.';
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION public.guard_ngo_representative_verification_audit_event()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE','TRUNCATE') THEN
        RAISE EXCEPTION 'Eventos de decisão não podem ser removidos.';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.actor_user_id IS NULL THEN RAISE EXCEPTION 'O ator do evento de verificação é obrigatório.'; END IF;
        RETURN NEW;
    END IF;
    IF TG_OP='UPDATE' THEN
        IF NEW.actor_user_id IS DISTINCT FROM OLD.actor_user_id THEN
            IF OLD.actor_user_id IS NOT NULL AND NEW.actor_user_id IS NULL
               AND pg_trigger_depth()>1
               AND NEW.id IS NOT DISTINCT FROM OLD.id
               AND NEW.application_id IS NOT DISTINCT FROM OLD.application_id
               AND NEW.actor_type_snapshot IS NOT DISTINCT FROM OLD.actor_type_snapshot
               AND NEW.action IS NOT DISTINCT FROM OLD.action
               AND NEW.from_status IS NOT DISTINCT FROM OLD.from_status
               AND NEW.to_status IS NOT DISTINCT FROM OLD.to_status
               AND NEW.justification IS NOT DISTINCT FROM OLD.justification
               AND NEW.created_at IS NOT DISTINCT FROM OLD.created_at
               AND NEW.metadata_redacted_at IS NOT DISTINCT FROM OLD.metadata_redacted_at
               AND NEW.metadata_redaction_id IS NOT DISTINCT FROM OLD.metadata_redaction_id THEN
                RETURN NEW;
            END IF;
            RAISE EXCEPTION 'O ator histórico só pode ser desvinculado pela exclusão autorizada da conta.';
        END IF;
        IF NEW.id IS DISTINCT FROM OLD.id OR NEW.application_id IS DISTINCT FROM OLD.application_id
           OR NEW.actor_type_snapshot IS DISTINCT FROM OLD.actor_type_snapshot
           OR NEW.action IS DISTINCT FROM OLD.action OR NEW.from_status IS DISTINCT FROM OLD.from_status
           OR NEW.to_status IS DISTINCT FROM OLD.to_status OR NEW.created_at IS DISTINCT FROM OLD.created_at
           OR OLD.metadata_redacted_at IS NOT NULL OR NEW.metadata_redacted_at IS NULL
           OR NEW.actor_user_id IS NOT NULL OR NEW.justification IS NOT NULL
           OR NEW.metadata_redaction_id IS NULL
           OR NOT EXISTS (SELECT 1 FROM public.ngo_representative_verification_audit_metadata_redactions r
               WHERE r.id=NEW.metadata_redaction_id AND r.event_id=OLD.id
                 AND r.redacted_at=NEW.metadata_redacted_at AND r.redacted_at>=transaction_timestamp()) THEN
            RAISE EXCEPTION 'Somente o procedimento controlado de retenção pode anonimizar metadados; o evento decisório permanece imutável.';
        END IF;
        RETURN NEW;
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION public.guard_ngo_representative_verification_disposal_event()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE','TRUNCATE') THEN
        RAISE EXCEPTION 'Eventos de descarte não podem ser removidos.';
    END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.actor_user_id IS NULL THEN RAISE EXCEPTION 'O ator do evento de descarte é obrigatório.'; END IF;
        RETURN NEW;
    END IF;
    IF TG_OP='UPDATE' THEN
        IF NEW.actor_user_id IS DISTINCT FROM OLD.actor_user_id THEN
            IF OLD.actor_user_id IS NOT NULL AND NEW.actor_user_id IS NULL
               AND pg_trigger_depth()>1
               AND NEW.id IS NOT DISTINCT FROM OLD.id
               AND NEW.evidence_id IS NOT DISTINCT FROM OLD.evidence_id
               AND NEW.application_id IS NOT DISTINCT FROM OLD.application_id
               AND NEW.request_event_id IS NOT DISTINCT FROM OLD.request_event_id
               AND NEW.event_type IS NOT DISTINCT FROM OLD.event_type
               AND NEW.actor_type_snapshot IS NOT DISTINCT FROM OLD.actor_type_snapshot
               AND NEW.reason IS NOT DISTINCT FROM OLD.reason
               AND NEW.created_at IS NOT DISTINCT FROM OLD.created_at
               AND NEW.metadata_redacted_at IS NOT DISTINCT FROM OLD.metadata_redacted_at
               AND NEW.metadata_redaction_id IS NOT DISTINCT FROM OLD.metadata_redaction_id THEN
                RETURN NEW;
            END IF;
            RAISE EXCEPTION 'O ator de descarte só pode ser desvinculado pela exclusão autorizada da conta.';
        END IF;
        IF NEW.id IS DISTINCT FROM OLD.id OR NEW.evidence_id IS DISTINCT FROM OLD.evidence_id
           OR NEW.application_id IS DISTINCT FROM OLD.application_id OR NEW.request_event_id IS DISTINCT FROM OLD.request_event_id
           OR NEW.event_type IS DISTINCT FROM OLD.event_type OR NEW.actor_type_snapshot IS DISTINCT FROM OLD.actor_type_snapshot
           OR NEW.created_at IS DISTINCT FROM OLD.created_at OR OLD.metadata_redacted_at IS NOT NULL
           OR NEW.metadata_redacted_at IS NULL OR NEW.actor_user_id IS NOT NULL OR NEW.reason IS NOT NULL
           OR NEW.metadata_redaction_id IS NULL
           OR NOT EXISTS (SELECT 1 FROM public.ngo_representative_verification_disposal_metadata_redactions r
               WHERE r.id=NEW.metadata_redaction_id AND r.disposal_event_id=OLD.id
                 AND r.redacted_at=NEW.metadata_redacted_at AND r.redacted_at>=transaction_timestamp()) THEN
            RAISE EXCEPTION 'Somente o procedimento controlado de retenção pode anonimizar metadados de descarte.';
        END IF;
        RETURN NEW;
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION public.request_ngo_verification_evidence_disposal(
    p_evidence_id BIGINT, p_actor_user_id INTEGER, p_justification TEXT)
RETURNS TABLE(disposal_event_id BIGINT, object_key TEXT)
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE
    v_application_id BIGINT;
    v_application_status TEXT;
    v_disposal_status TEXT;
    v_storage_key TEXT;
    v_request_event_id BIGINT;
    v_retention_status TEXT;
    v_retention_months SMALLINT;
    v_approved_at TIMESTAMPTZ;
    v_decision_at TIMESTAMPTZ;
BEGIN
    IF p_actor_user_id IS NULL OR p_justification IS NULL OR length(btrim(p_justification)) NOT BETWEEN 1 AND 2000 THEN
        RAISE EXCEPTION 'Justificativa e ator autorizado são obrigatórios para solicitar descarte.';
    END IF;
    SELECT s.application_id INTO v_application_id
      FROM public.ngo_representative_verification_evidence e
      JOIN public.ngo_representative_verification_submissions s ON s.id=e.submission_id
     WHERE e.id=p_evidence_id;
    IF v_application_id IS NULL THEN RAISE EXCEPTION 'Evidência inexistente.'; END IF;
    SELECT status INTO v_application_status FROM public.ngo_representative_verification_applications
     WHERE id=v_application_id FOR UPDATE;
    IF v_application_status NOT IN ('APPROVED','REJECTED') THEN
        RAISE EXCEPTION 'Evidências de solicitações abertas não podem ser descartadas.';
    END IF;
    SELECT disposal_status, storage_key, disposal_request_event_id
      INTO v_disposal_status, v_storage_key, v_request_event_id
      FROM public.ngo_representative_verification_evidence WHERE id=p_evidence_id FOR UPDATE;
    IF v_disposal_status='DISPOSED' THEN RAISE EXCEPTION 'A evidência já foi descartada.'; END IF;
    IF v_disposal_status='DISPOSAL_REQUESTED' THEN
        RETURN QUERY SELECT v_request_event_id, v_storage_key;
        RETURN;
    END IF;
    SELECT retention_policy_status, proposed_evidence_retention_months, retention_policy_approved_at
      INTO v_retention_status, v_retention_months, v_approved_at
      FROM public.ngo_representative_verification_config WHERE id=1;
    IF v_retention_status IS DISTINCT FROM 'APPROVED' OR v_approved_at IS NULL THEN
        RAISE EXCEPTION 'A política de retenção ainda é proposta e não autoriza descarte.';
    END IF;
    SELECT max(created_at) INTO v_decision_at FROM public.ngo_representative_verification_events
     WHERE application_id=v_application_id AND action IN ('APPROVED','REJECTED');
    IF v_decision_at IS NULL OR clock_timestamp() < v_decision_at + make_interval(months=>v_retention_months::INTEGER) THEN
        RAISE EXCEPTION 'O prazo de retenção aprovado ainda não terminou.';
    END IF;
    INSERT INTO public.ngo_representative_verification_evidence_disposal_events
        (evidence_id,application_id,event_type,actor_user_id,actor_type_snapshot,reason)
    VALUES (p_evidence_id,v_application_id,'REQUESTED',p_actor_user_id,'ADMINISTRATOR',btrim(p_justification))
    RETURNING id INTO v_request_event_id;
    UPDATE public.ngo_representative_verification_evidence
       SET disposal_status='DISPOSAL_REQUESTED', disposal_requested_at=clock_timestamp(),
           disposal_request_event_id=v_request_event_id
     WHERE id=p_evidence_id;
    RETURN QUERY SELECT v_request_event_id, v_storage_key;
END
$$;

CREATE OR REPLACE FUNCTION public.approve_ngo_verification_retention_policy(
    p_actor_user_id INTEGER, p_approval_reference VARCHAR, p_justification TEXT)
RETURNS BIGINT LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE
    v_evidence_months SMALLINT;
    v_audit_years SMALLINT;
    v_event_id BIGINT;
BEGIN
    IF p_actor_user_id IS NULL OR p_approval_reference IS NULL
       OR length(btrim(p_approval_reference)) NOT BETWEEN 1 AND 80
       OR p_justification IS NULL OR length(btrim(p_justification)) NOT BETWEEN 1 AND 2000 THEN
        RAISE EXCEPTION 'Ator, referência de aprovação e justificativa são obrigatórios.';
    END IF;
    SELECT proposed_evidence_retention_months, proposed_audit_metadata_retention_years
      INTO v_evidence_months, v_audit_years
      FROM public.ngo_representative_verification_config
     WHERE id=1 AND retention_policy_status='PROPOSED' FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'A política já foi aprovada; uma nova versão exige outra migração revisada.'; END IF;
    INSERT INTO public.ngo_representative_verification_retention_approval_events
        (actor_user_id,approval_reference,evidence_retention_months,audit_metadata_retention_years,justification)
    VALUES (p_actor_user_id,btrim(p_approval_reference),v_evidence_months,v_audit_years,btrim(p_justification))
    RETURNING id INTO v_event_id;
    UPDATE public.ngo_representative_verification_config
       SET retention_policy_status='APPROVED', retention_policy_approved_at=clock_timestamp(),
           retention_policy_approved_by_user_id=p_actor_user_id,
           retention_policy_approval_reference=btrim(p_approval_reference),
           retention_policy_approval_event_id=v_event_id
     WHERE id=1;
    RETURN v_event_id;
END
$$;

CREATE OR REPLACE FUNCTION public.complete_ngo_verification_evidence_disposal(
    p_evidence_id BIGINT, p_request_event_id BIGINT, p_actor_user_id INTEGER)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE
    v_application_id BIGINT;
    v_application_status TEXT;
    v_state TEXT;
    v_current_request BIGINT;
    v_completion_event_id BIGINT;
BEGIN
    SELECT s.application_id INTO v_application_id
      FROM public.ngo_representative_verification_evidence e
      JOIN public.ngo_representative_verification_submissions s ON s.id=e.submission_id
     WHERE e.id=p_evidence_id;
    IF v_application_id IS NULL THEN RAISE EXCEPTION 'Evidência inexistente.'; END IF;
    SELECT status INTO v_application_status FROM public.ngo_representative_verification_applications
     WHERE id=v_application_id FOR UPDATE;
    SELECT disposal_status, disposal_request_event_id INTO v_state, v_current_request
      FROM public.ngo_representative_verification_evidence WHERE id=p_evidence_id FOR UPDATE;
    IF v_state<>'DISPOSAL_REQUESTED' OR v_current_request IS DISTINCT FROM p_request_event_id THEN
        RAISE EXCEPTION 'Pedido de descarte não está ativo para esta evidência.';
    END IF;
    IF p_actor_user_id IS NULL OR v_application_status NOT IN ('APPROVED','REJECTED') THEN
        RAISE EXCEPTION 'Ator autorizado e solicitação finalizada são obrigatórios.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.ngo_representative_verification_config
                    WHERE id=1 AND retention_policy_status='APPROVED' AND retention_policy_approved_at IS NOT NULL) THEN
        RAISE EXCEPTION 'A política de retenção não está aprovada para concluir descarte.';
    END IF;
    INSERT INTO public.ngo_representative_verification_evidence_disposal_events
        (evidence_id,application_id,request_event_id,event_type,actor_user_id,actor_type_snapshot,reason)
    VALUES (p_evidence_id,v_application_id,p_request_event_id,'COMPLETED',p_actor_user_id,
            'ADMINISTRATOR','Arquivo privado removido do armazenamento.')
    RETURNING id INTO v_completion_event_id;
    UPDATE public.ngo_representative_verification_evidence
       SET disposal_status='DISPOSED', disposed_at=clock_timestamp(), storage_key=NULL,
           media_type=NULL, size_bytes=NULL, uploaded_by_user_id=NULL, uploaded_at=NULL,
           disposal_completed_event_id=v_completion_event_id
     WHERE id=p_evidence_id;
END
$$;

CREATE OR REPLACE FUNCTION public.record_ngo_verification_evidence_disposal_failure(
    p_evidence_id BIGINT, p_request_event_id BIGINT, p_actor_user_id INTEGER)
RETURNS VOID LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE v_application_id BIGINT; v_state TEXT; v_current_request BIGINT;
BEGIN
    SELECT s.application_id INTO v_application_id
      FROM public.ngo_representative_verification_evidence e
      JOIN public.ngo_representative_verification_submissions s ON s.id=e.submission_id
     WHERE e.id=p_evidence_id;
    IF v_application_id IS NULL THEN RAISE EXCEPTION 'Evidência inexistente.'; END IF;
    PERFORM 1 FROM public.ngo_representative_verification_applications WHERE id=v_application_id FOR UPDATE;
    SELECT disposal_status, disposal_request_event_id INTO v_state, v_current_request
      FROM public.ngo_representative_verification_evidence WHERE id=p_evidence_id FOR UPDATE;
    IF v_state<>'DISPOSAL_REQUESTED' OR v_current_request IS DISTINCT FROM p_request_event_id THEN
        RAISE EXCEPTION 'Pedido de descarte não está ativo para esta evidência.';
    END IF;
    IF p_actor_user_id IS NULL THEN RAISE EXCEPTION 'Ator autorizado obrigatório.'; END IF;
    INSERT INTO public.ngo_representative_verification_evidence_disposal_events
        (evidence_id,application_id,request_event_id,event_type,actor_user_id,actor_type_snapshot,reason)
    VALUES (p_evidence_id,v_application_id,p_request_event_id,'FAILED',p_actor_user_id,
            'ADMINISTRATOR','Falha na remoção do arquivo; descarte pendente de nova tentativa.');
END
$$;

CREATE OR REPLACE FUNCTION public.redact_ngo_verification_audit_event_metadata(
    p_event_id BIGINT, p_policy_reference VARCHAR)
RETURNS BIGINT LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE
    v_application_id BIGINT; v_event_time TIMESTAMPTZ; v_redacted BIGINT;
    v_retention_status TEXT; v_retention_years SMALLINT; v_decision_at TIMESTAMPTZ;
BEGIN
    SELECT application_id INTO v_application_id FROM public.ngo_representative_verification_events
     WHERE id=p_event_id;
    IF v_application_id IS NULL THEN RAISE EXCEPTION 'Evento de auditoria inexistente.'; END IF;
    PERFORM 1 FROM public.ngo_representative_verification_applications WHERE id=v_application_id FOR UPDATE;
    SELECT created_at, metadata_redaction_id INTO v_event_time, v_redacted
      FROM public.ngo_representative_verification_events WHERE id=p_event_id FOR UPDATE;
    IF v_redacted IS NOT NULL THEN RETURN v_redacted; END IF;
    SELECT retention_policy_status, proposed_audit_metadata_retention_years
      INTO v_retention_status, v_retention_years FROM public.ngo_representative_verification_config WHERE id=1;
    IF v_retention_status IS DISTINCT FROM 'APPROVED' THEN
        RAISE EXCEPTION 'A política de retenção ainda é proposta e não autoriza anonimização.';
    END IF;
    SELECT max(created_at) INTO v_decision_at FROM public.ngo_representative_verification_events
     WHERE application_id=v_application_id AND action IN ('APPROVED','REJECTED');
    IF v_decision_at IS NULL OR clock_timestamp() < v_decision_at + make_interval(years=>v_retention_years::INTEGER) THEN
        RAISE EXCEPTION 'O prazo aprovado para anonimização ainda não terminou.';
    END IF;
    IF p_policy_reference IS DISTINCT FROM (SELECT retention_policy_approval_reference
          FROM public.ngo_representative_verification_config WHERE id=1) THEN
        RAISE EXCEPTION 'A referência de aprovação da política não confere.';
    END IF;
    INSERT INTO public.ngo_representative_verification_audit_metadata_redactions(event_id,policy_reference)
    VALUES (p_event_id,p_policy_reference) RETURNING id INTO v_redacted;
    UPDATE public.ngo_representative_verification_events
       SET actor_user_id=NULL, justification=NULL,
           metadata_redacted_at=clock_timestamp(), metadata_redaction_id=v_redacted
     WHERE id=p_event_id;
    RETURN v_redacted;
END
$$;

CREATE OR REPLACE FUNCTION public.redact_ngo_verification_disposal_event_metadata(
    p_disposal_event_id BIGINT, p_policy_reference VARCHAR)
RETURNS BIGINT LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,public AS $$
DECLARE
    v_application_id BIGINT; v_redacted BIGINT; v_retention_status TEXT;
    v_retention_years SMALLINT; v_decision_at TIMESTAMPTZ;
BEGIN
    SELECT application_id INTO v_application_id
      FROM public.ngo_representative_verification_evidence_disposal_events WHERE id=p_disposal_event_id;
    IF v_application_id IS NULL THEN RAISE EXCEPTION 'Evento de descarte inexistente.'; END IF;
    PERFORM 1 FROM public.ngo_representative_verification_applications WHERE id=v_application_id FOR UPDATE;
    PERFORM 1 FROM public.ngo_representative_verification_evidence_disposal_events WHERE id=p_disposal_event_id FOR UPDATE;
    SELECT metadata_redaction_id INTO v_redacted
      FROM public.ngo_representative_verification_evidence_disposal_events WHERE id=p_disposal_event_id;
    IF v_redacted IS NOT NULL THEN RETURN v_redacted; END IF;
    SELECT retention_policy_status, proposed_audit_metadata_retention_years
      INTO v_retention_status, v_retention_years FROM public.ngo_representative_verification_config WHERE id=1;
    IF v_retention_status IS DISTINCT FROM 'APPROVED' THEN
        RAISE EXCEPTION 'A política de retenção ainda é proposta e não autoriza anonimização.';
    END IF;
    SELECT max(created_at) INTO v_decision_at FROM public.ngo_representative_verification_events
     WHERE application_id=v_application_id AND action IN ('APPROVED','REJECTED');
    IF v_decision_at IS NULL OR clock_timestamp() < v_decision_at + make_interval(years=>v_retention_years::INTEGER) THEN
        RAISE EXCEPTION 'O prazo aprovado para anonimização ainda não terminou.';
    END IF;
    IF p_policy_reference IS DISTINCT FROM (SELECT retention_policy_approval_reference
          FROM public.ngo_representative_verification_config WHERE id=1) THEN
        RAISE EXCEPTION 'A referência de aprovação da política não confere.';
    END IF;
    INSERT INTO public.ngo_representative_verification_disposal_metadata_redactions(disposal_event_id,policy_reference)
    VALUES (p_disposal_event_id,p_policy_reference) RETURNING id INTO v_redacted;
    UPDATE public.ngo_representative_verification_evidence_disposal_events
       SET actor_user_id=NULL, reason=NULL,
           metadata_redacted_at=clock_timestamp(), metadata_redaction_id=v_redacted
     WHERE id=p_disposal_event_id;
    RETURN v_redacted;
END
$$;

CREATE CONSTRAINT TRIGGER trg_ngo_verification_application_event_consistency
    AFTER INSERT OR UPDATE ON public.ngo_representative_verification_applications
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
    EXECUTE FUNCTION public.guard_ngo_representative_verification_event_consistency();
CREATE CONSTRAINT TRIGGER trg_ngo_verification_submission_event_consistency
    AFTER UPDATE ON public.ngo_representative_verification_submissions
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
    EXECUTE FUNCTION public.guard_ngo_verification_submission_event_consistency();
CREATE TRIGGER trg_ngo_verification_application_guard
    BEFORE UPDATE OR DELETE ON public.ngo_representative_verification_applications
    FOR EACH ROW EXECUTE FUNCTION public.guard_ngo_representative_verification_application();
CREATE TRIGGER trg_ngo_verification_application_parent_guard
    BEFORE INSERT ON public.ngo_representative_verification_applications
    FOR EACH ROW EXECUTE FUNCTION public.guard_ngo_representative_verification_application_parent();
CREATE TRIGGER trg_ngo_verification_application_no_truncate
    BEFORE TRUNCATE ON public.ngo_representative_verification_applications
    FOR EACH STATEMENT EXECUTE FUNCTION public.guard_ngo_representative_verification_application();
CREATE TRIGGER trg_ngo_verification_submission_guard
    BEFORE UPDATE OR DELETE ON public.ngo_representative_verification_submissions
    FOR EACH ROW EXECUTE FUNCTION public.guard_ngo_representative_verification_submission();
CREATE TRIGGER trg_ngo_verification_submission_no_truncate
    BEFORE TRUNCATE ON public.ngo_representative_verification_submissions
    FOR EACH STATEMENT EXECUTE FUNCTION public.reject_ngo_representative_verification_history_mutation();
CREATE TRIGGER trg_ngo_verification_evidence_guard
    BEFORE INSERT OR UPDATE OR DELETE ON public.ngo_representative_verification_evidence
    FOR EACH ROW EXECUTE FUNCTION public.guard_ngo_representative_verification_evidence();
CREATE TRIGGER trg_ngo_verification_evidence_no_truncate
    BEFORE TRUNCATE ON public.ngo_representative_verification_evidence
    FOR EACH STATEMENT EXECUTE FUNCTION public.reject_ngo_representative_verification_history_mutation();
CREATE TRIGGER trg_ngo_verification_events_immutable
    BEFORE INSERT OR UPDATE OR DELETE ON public.ngo_representative_verification_events
    FOR EACH ROW EXECUTE FUNCTION public.guard_ngo_representative_verification_audit_event();
CREATE TRIGGER trg_ngo_verification_events_no_truncate
    BEFORE TRUNCATE ON public.ngo_representative_verification_events
    FOR EACH STATEMENT EXECUTE FUNCTION public.reject_ngo_representative_verification_history_mutation();
CREATE TRIGGER trg_ngo_verification_disposal_events_guard
    BEFORE INSERT OR UPDATE OR DELETE ON public.ngo_representative_verification_evidence_disposal_events
    FOR EACH ROW EXECUTE FUNCTION public.guard_ngo_representative_verification_disposal_event();
CREATE TRIGGER trg_ngo_verification_disposal_events_no_truncate
    BEFORE TRUNCATE ON public.ngo_representative_verification_evidence_disposal_events
    FOR EACH STATEMENT EXECUTE FUNCTION public.reject_ngo_representative_verification_history_mutation();
CREATE TRIGGER trg_ngo_verification_audit_redactions_immutable
    BEFORE UPDATE OR DELETE ON public.ngo_representative_verification_audit_metadata_redactions
    FOR EACH ROW EXECUTE FUNCTION public.reject_ngo_representative_verification_history_mutation();
CREATE TRIGGER trg_ngo_verification_audit_redactions_no_truncate
    BEFORE TRUNCATE ON public.ngo_representative_verification_audit_metadata_redactions
    FOR EACH STATEMENT EXECUTE FUNCTION public.reject_ngo_representative_verification_history_mutation();
CREATE TRIGGER trg_ngo_verification_disposal_redactions_immutable
    BEFORE UPDATE OR DELETE ON public.ngo_representative_verification_disposal_metadata_redactions
    FOR EACH ROW EXECUTE FUNCTION public.reject_ngo_representative_verification_history_mutation();
CREATE TRIGGER trg_ngo_verification_disposal_redactions_no_truncate
    BEFORE TRUNCATE ON public.ngo_representative_verification_disposal_metadata_redactions
    FOR EACH STATEMENT EXECUTE FUNCTION public.reject_ngo_representative_verification_history_mutation();
CREATE TRIGGER trg_ngo_verification_config_immutable
    BEFORE UPDATE OR DELETE ON public.ngo_representative_verification_config
    FOR EACH ROW EXECUTE FUNCTION public.guard_ngo_representative_verification_config_update();
CREATE TRIGGER trg_ngo_verification_config_no_truncate
    BEFORE TRUNCATE ON public.ngo_representative_verification_config
    FOR EACH STATEMENT EXECUTE FUNCTION public.reject_ngo_representative_verification_config_mutation();
CREATE TRIGGER trg_ngo_verification_retention_approvals_immutable
    BEFORE INSERT OR UPDATE OR DELETE ON public.ngo_representative_verification_retention_approval_events
    FOR EACH ROW EXECUTE FUNCTION public.guard_ngo_representative_verification_retention_approval_event();
CREATE TRIGGER trg_ngo_verification_retention_approvals_no_truncate
    BEFORE TRUNCATE ON public.ngo_representative_verification_retention_approval_events
    FOR EACH STATEMENT EXECUTE FUNCTION public.reject_ngo_representative_verification_history_mutation();
CREATE TRIGGER trg_ngo_verification_legacy_exemptions_immutable
    BEFORE UPDATE OR DELETE ON public.ngo_representative_verification_legacy_exemptions
    FOR EACH ROW EXECUTE FUNCTION public.reject_ngo_representative_verification_history_mutation();
CREATE TRIGGER trg_ngo_verification_legacy_exemptions_no_truncate
    BEFORE TRUNCATE ON public.ngo_representative_verification_legacy_exemptions
    FOR EACH STATEMENT EXECUTE FUNCTION public.reject_ngo_representative_verification_history_mutation();

ALTER TABLE public.ngo_representative_verification_applications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ngo_representative_verification_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ngo_representative_verification_retention_approval_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ngo_representative_verification_legacy_exemptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ngo_representative_verification_submissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ngo_representative_verification_evidence ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ngo_representative_verification_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ngo_representative_verification_evidence_disposal_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ngo_representative_verification_audit_metadata_redactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ngo_representative_verification_disposal_metadata_redactions ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.ngo_representative_verification_applications,
    public.ngo_representative_verification_config,
    public.ngo_representative_verification_retention_approval_events,
    public.ngo_representative_verification_legacy_exemptions,
    public.ngo_representative_verification_submissions,
    public.ngo_representative_verification_evidence,
    public.ngo_representative_verification_events,
    public.ngo_representative_verification_evidence_disposal_events,
    public.ngo_representative_verification_audit_metadata_redactions,
    public.ngo_representative_verification_disposal_metadata_redactions FROM PUBLIC;
REVOKE ALL ON FUNCTION public.guard_ngo_representative_verification_application(),
    public.guard_ngo_representative_verification_event_consistency(),
    public.guard_ngo_representative_verification_submission(),
    public.guard_ngo_verification_submission_event_consistency(),
    public.guard_ngo_representative_verification_application_parent(),
    public.guard_ngo_representative_verification_evidence(),
    public.guard_ngo_representative_verification_audit_event(),
    public.guard_ngo_representative_verification_disposal_event(),
    public.guard_ngo_representative_verification_config_update(),
    public.guard_ngo_representative_verification_retention_approval_event(),
    public.reject_ngo_representative_verification_history_mutation(),
    public.reject_ngo_representative_verification_config_mutation(),
    public.request_ngo_verification_evidence_disposal(BIGINT,INTEGER,TEXT),
    public.complete_ngo_verification_evidence_disposal(BIGINT,BIGINT,INTEGER),
    public.record_ngo_verification_evidence_disposal_failure(BIGINT,BIGINT,INTEGER),
    public.redact_ngo_verification_audit_event_metadata(BIGINT,VARCHAR),
    public.redact_ngo_verification_disposal_event_metadata(BIGINT,VARCHAR),
    public.approve_ngo_verification_retention_policy(INTEGER,VARCHAR,TEXT),
    public.ngo_representative_is_publicly_eligible(INTEGER) FROM PUBLIC;

DO $acl$
DECLARE role_name TEXT; table_name TEXT; sequence_name TEXT;
BEGIN
    FOREACH role_name IN ARRAY ARRAY['anon','authenticated','service_role'] LOOP
        EXECUTE format('REVOKE ALL ON TABLE public.ngo_representative_verification_config, public.ngo_representative_verification_retention_approval_events, public.ngo_representative_verification_legacy_exemptions, public.ngo_representative_verification_applications, public.ngo_representative_verification_submissions, public.ngo_representative_verification_evidence, public.ngo_representative_verification_events, public.ngo_representative_verification_evidence_disposal_events, public.ngo_representative_verification_audit_metadata_redactions, public.ngo_representative_verification_disposal_metadata_redactions FROM %I', role_name);
        FOREACH table_name IN ARRAY ARRAY[
            'ngo_representative_verification_applications',
            'ngo_representative_verification_retention_approval_events',
            'ngo_representative_verification_submissions',
            'ngo_representative_verification_evidence',
            'ngo_representative_verification_events',
            'ngo_representative_verification_evidence_disposal_events',
            'ngo_representative_verification_audit_metadata_redactions',
            'ngo_representative_verification_disposal_metadata_redactions'
        ] LOOP
            sequence_name := pg_get_serial_sequence(format('public.%I', table_name), 'id');
            IF sequence_name IS NOT NULL THEN
                EXECUTE format('REVOKE ALL ON SEQUENCE %s FROM PUBLIC', sequence_name);
                EXECUTE format('REVOKE ALL ON SEQUENCE %s FROM %I', sequence_name, role_name);
            END IF;
        END LOOP;
        EXECUTE format('REVOKE ALL ON FUNCTION public.guard_ngo_representative_verification_application(), public.guard_ngo_representative_verification_event_consistency(), public.guard_ngo_representative_verification_submission(), public.guard_ngo_verification_submission_event_consistency(), public.guard_ngo_representative_verification_application_parent(), public.guard_ngo_representative_verification_evidence(), public.guard_ngo_representative_verification_audit_event(), public.guard_ngo_representative_verification_disposal_event(), public.guard_ngo_representative_verification_config_update(), public.guard_ngo_representative_verification_retention_approval_event(), public.reject_ngo_representative_verification_history_mutation(), public.reject_ngo_representative_verification_config_mutation(), public.request_ngo_verification_evidence_disposal(bigint,integer,text), public.complete_ngo_verification_evidence_disposal(bigint,bigint,integer), public.record_ngo_verification_evidence_disposal_failure(bigint,bigint,integer), public.redact_ngo_verification_audit_event_metadata(bigint,character varying), public.redact_ngo_verification_disposal_event_metadata(bigint,character varying), public.approve_ngo_verification_retention_policy(integer,character varying,text) FROM %I', role_name);
        EXECUTE format('REVOKE ALL ON FUNCTION public.ngo_representative_is_publicly_eligible(integer) FROM %I', role_name);
    END LOOP;
END
$acl$;

-- The confirmed migration role has BYPASSRLS and exact DML grants on storage.buckets.
-- Create the isolated private bucket transactionally, but do not alter Storage policies.
-- Existing policies, including those for unrelated buckets, remain untouched.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('ngo-representative-evidence', 'ngo-representative-evidence', false, 10485760,
        ARRAY['application/pdf','image/jpeg','image/png']::text[])
ON CONFLICT (id) DO NOTHING;

DO $postflight$
DECLARE missing_constraints TEXT;
BEGIN
    IF (SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN (
        'ngo_representative_verification_config',
        'ngo_representative_verification_retention_approval_events',
        'ngo_representative_verification_legacy_exemptions',
        'ngo_representative_verification_applications','ngo_representative_verification_submissions',
        'ngo_representative_verification_evidence','ngo_representative_verification_events',
        'ngo_representative_verification_evidence_disposal_events',
        'ngo_representative_verification_audit_metadata_redactions',
        'ngo_representative_verification_disposal_metadata_redactions')) <> 10 THEN
        RAISE EXCEPTION 'Postflight: tabelas de verificação incompletas.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relname IN (
        'ngo_representative_verification_config','ngo_representative_verification_retention_approval_events',
        'ngo_representative_verification_legacy_exemptions',
        'ngo_representative_verification_applications','ngo_representative_verification_submissions',
        'ngo_representative_verification_evidence','ngo_representative_verification_events',
        'ngo_representative_verification_evidence_disposal_events',
        'ngo_representative_verification_audit_metadata_redactions',
        'ngo_representative_verification_disposal_metadata_redactions')
          AND c.relowner <> (SELECT oid FROM pg_roles WHERE rolname=current_user)) THEN
        RAISE EXCEPTION 'Postflight: role executora não é owner das tabelas privadas; valide grants JDBC antes da ativação.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relname IN (
        'ngo_representative_verification_config','ngo_representative_verification_retention_approval_events',
        'ngo_representative_verification_applications','ngo_representative_verification_submissions',
        'ngo_representative_verification_legacy_exemptions',
        'ngo_representative_verification_evidence','ngo_representative_verification_events',
        'ngo_representative_verification_evidence_disposal_events',
        'ngo_representative_verification_audit_metadata_redactions',
        'ngo_representative_verification_disposal_metadata_redactions') AND c.relrowsecurity IS FALSE) THEN
        RAISE EXCEPTION 'Postflight: RLS não está habilitado em todas as tabelas privadas.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_policy p JOIN pg_class c ON c.oid=p.polrelid JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relname LIKE 'ngo_representative_verification_%') THEN
        RAISE EXCEPTION 'Postflight: tabelas privadas não devem ter políticas diretas de acesso REST.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM storage.buckets WHERE id='ngo-representative-evidence'
        AND name='ngo-representative-evidence' AND public IS FALSE
        AND file_size_limit=10485760
        AND allowed_mime_types @> ARRAY['application/pdf','image/jpeg','image/png']::text[]) THEN
        RAISE EXCEPTION 'Postflight: bucket privado de evidências ou limites de arquivo incorretos.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_policy p
        WHERE p.polrelid='storage.objects'::regclass
          AND (0=ANY(p.polroles) OR EXISTS (
              SELECT 1 FROM unnest(p.polroles) AS policy_role(role_oid)
               JOIN pg_roles r ON r.oid=policy_role.role_oid
               WHERE r.rolname IN ('anon','authenticated')))
    ) THEN
        RAISE EXCEPTION 'Postflight: uma policy de cliente foi criada ou apareceu em storage.objects; revise o bucket privado antes da ativação.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname IN ('anon','authenticated') AND rolbypassrls) THEN
        RAISE EXCEPTION 'Postflight: anon/authenticated podem ignorar RLS.';
    END IF;
    IF to_regprocedure('public.ngo_representative_is_publicly_eligible(integer)') IS NULL THEN
        RAISE EXCEPTION 'Postflight: função de elegibilidade pública ausente.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM (VALUES
          ('ngo_representative_verification_applications','trg_ngo_verification_application_guard','guard_ngo_representative_verification_application'),
          ('ngo_representative_verification_applications','trg_ngo_verification_application_parent_guard','guard_ngo_representative_verification_application_parent'),
          ('ngo_representative_verification_applications','trg_ngo_verification_application_no_truncate','guard_ngo_representative_verification_application'),
          ('ngo_representative_verification_applications','trg_ngo_verification_application_event_consistency','guard_ngo_representative_verification_event_consistency'),
          ('ngo_representative_verification_submissions','trg_ngo_verification_submission_guard','guard_ngo_representative_verification_submission'),
          ('ngo_representative_verification_submissions','trg_ngo_verification_submission_event_consistency','guard_ngo_verification_submission_event_consistency'),
          ('ngo_representative_verification_submissions','trg_ngo_verification_submission_no_truncate','reject_ngo_representative_verification_history_mutation'),
          ('ngo_representative_verification_evidence','trg_ngo_verification_evidence_guard','guard_ngo_representative_verification_evidence'),
          ('ngo_representative_verification_evidence','trg_ngo_verification_evidence_no_truncate','reject_ngo_representative_verification_history_mutation'),
          ('ngo_representative_verification_events','trg_ngo_verification_events_immutable','guard_ngo_representative_verification_audit_event'),
          ('ngo_representative_verification_events','trg_ngo_verification_events_no_truncate','reject_ngo_representative_verification_history_mutation'),
          ('ngo_representative_verification_evidence_disposal_events','trg_ngo_verification_disposal_events_guard','guard_ngo_representative_verification_disposal_event'),
          ('ngo_representative_verification_evidence_disposal_events','trg_ngo_verification_disposal_events_no_truncate','reject_ngo_representative_verification_history_mutation'),
          ('ngo_representative_verification_audit_metadata_redactions','trg_ngo_verification_audit_redactions_immutable','reject_ngo_representative_verification_history_mutation'),
          ('ngo_representative_verification_audit_metadata_redactions','trg_ngo_verification_audit_redactions_no_truncate','reject_ngo_representative_verification_history_mutation'),
          ('ngo_representative_verification_disposal_metadata_redactions','trg_ngo_verification_disposal_redactions_immutable','reject_ngo_representative_verification_history_mutation'),
          ('ngo_representative_verification_disposal_metadata_redactions','trg_ngo_verification_disposal_redactions_no_truncate','reject_ngo_representative_verification_history_mutation'),
          ('ngo_representative_verification_config','trg_ngo_verification_config_immutable','guard_ngo_representative_verification_config_update'),
          ('ngo_representative_verification_config','trg_ngo_verification_config_no_truncate','reject_ngo_representative_verification_config_mutation'),
          ('ngo_representative_verification_retention_approval_events','trg_ngo_verification_retention_approvals_immutable','guard_ngo_representative_verification_retention_approval_event'),
          ('ngo_representative_verification_retention_approval_events','trg_ngo_verification_retention_approvals_no_truncate','reject_ngo_representative_verification_history_mutation'),
          ('ngo_representative_verification_legacy_exemptions','trg_ngo_verification_legacy_exemptions_immutable','reject_ngo_representative_verification_history_mutation'),
          ('ngo_representative_verification_legacy_exemptions','trg_ngo_verification_legacy_exemptions_no_truncate','reject_ngo_representative_verification_history_mutation')
        ) AS expected(table_name, trigger_name, function_name)
        WHERE NOT EXISTS (
            SELECT 1 FROM pg_trigger t JOIN pg_proc p ON p.oid=t.tgfoid
            WHERE t.tgrelid=to_regclass('public.' || expected.table_name)
              AND t.tgname=expected.trigger_name AND p.proname=expected.function_name
              AND p.pronamespace='public'::regnamespace
              AND t.tgenabled <> 'D'
        )
    ) THEN RAISE EXCEPTION 'Postflight: trigger ausente, desabilitado ou ligado à função/tabela errada.'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger t
        WHERE t.tgrelid='public.ngo_representative_verification_applications'::regclass
          AND t.tgname='trg_ngo_verification_application_event_consistency'
          AND t.tgdeferrable AND t.tginitdeferred AND t.tgenabled <> 'D'
    ) THEN RAISE EXCEPTION 'Postflight: trigger de consistência de eventos não é deferred/enabled.'; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger t
        WHERE t.tgrelid='public.ngo_representative_verification_submissions'::regclass
          AND t.tgname='trg_ngo_verification_submission_event_consistency'
          AND t.tgdeferrable AND t.tginitdeferred AND t.tgenabled <> 'D'
    ) THEN RAISE EXCEPTION 'Postflight: trigger deferred de auditoria do envio ausente.'; END IF;
    IF EXISTS (
        SELECT 1 FROM unnest(ARRAY[
            'ngo_representative_verification_config',
            'ngo_representative_verification_retention_approval_events',
            'ngo_representative_verification_legacy_exemptions',
            'ngo_representative_verification_applications',
            'ngo_representative_verification_submissions',
            'ngo_representative_verification_evidence',
            'ngo_representative_verification_events',
            'ngo_representative_verification_evidence_disposal_events',
            'ngo_representative_verification_audit_metadata_redactions',
            'ngo_representative_verification_disposal_metadata_redactions'
        ]) AS expected(table_name)
        WHERE NOT EXISTS (
            SELECT 1 FROM pg_constraint c JOIN pg_class r ON r.oid=c.conrelid
            JOIN pg_namespace n ON n.oid=r.relnamespace
            WHERE n.nspname='public' AND r.relname=expected.table_name AND c.contype='p'
        )
    ) THEN RAISE EXCEPTION 'Postflight: falta uma chave primária de tabela de verificação.'; END IF;
    SELECT string_agg(expected.table_name || '.' || expected.constraint_name, ', ')
      INTO missing_constraints
      FROM (VALUES
          ('ngo_representative_verification_config','ngo_representative_verification_config_pkey','p'),
          ('ngo_representative_verification_config','ngo_representative_verification_config_id_check','c'),
          ('ngo_representative_verification_config','ck_ngo_verification_retention_proposal','c'),
          ('ngo_representative_verification_config','ck_ngo_verification_retention_status','c'),
          ('ngo_representative_verification_config','ck_ngo_verification_retention_approval','c'),
          ('ngo_representative_verification_config','fk_ngo_verification_retention_approver','f'),
          ('ngo_representative_verification_config','fk_ngo_verification_retention_approval_event','f'),
          ('ngo_representative_verification_retention_approval_events','ngo_representative_verification_retention_approval_events_pkey','p'),
          ('ngo_representative_verification_retention_approval_events','fk_ngo_verification_retention_approval_actor','f'),
          ('ngo_representative_verification_retention_approval_events','ck_ngo_verification_retention_event_reference','c'),
          ('ngo_representative_verification_retention_approval_events','ck_ngo_verification_retention_event_periods','c'),
          ('ngo_representative_verification_retention_approval_events','ck_ngo_verification_retention_event_justification','c'),
          ('ngo_representative_verification_legacy_exemptions','ngo_representative_verification_legacy_exemptions_pkey','p'),
          ('ngo_representative_verification_legacy_exemptions','fk_ngo_verification_legacy_osc','f'),
          ('ngo_representative_verification_applications','uq_ngo_verification_application_cycle','u'),
          ('ngo_representative_verification_applications','fk_ngo_verification_application_osc','f'),
          ('ngo_representative_verification_applications','fk_ngo_verification_application_parent','f'),
          ('ngo_representative_verification_applications','fk_ngo_verification_application_applicant','f'),
          ('ngo_representative_verification_applications','ck_ngo_verification_representation','c'),
          ('ngo_representative_verification_applications','ck_ngo_verification_status','c'),
          ('ngo_representative_verification_applications','ck_ngo_verification_version','c'),
          ('ngo_representative_verification_applications','ck_ngo_verification_application_cycle','c'),
          ('ngo_representative_verification_submissions','uq_ngo_verification_submission_version','u'),
          ('ngo_representative_verification_submissions','fk_ngo_verification_submission_application','f'),
          ('ngo_representative_verification_submissions','fk_ngo_verification_submission_user','f'),
          ('ngo_representative_verification_submissions','ck_ngo_verification_submission_version','c'),
          ('ngo_representative_verification_evidence','uq_ngo_verification_evidence_key','u'),
          ('ngo_representative_verification_evidence','fk_ngo_verification_evidence_submission','f'),
          ('ngo_representative_verification_evidence','fk_ngo_verification_evidence_uploader','f'),
          ('ngo_representative_verification_evidence','ck_ngo_verification_document_type','c'),
          ('ngo_representative_verification_evidence','ck_ngo_verification_media_type','c'),
          ('ngo_representative_verification_evidence','ck_ngo_verification_evidence_size','c'),
          ('ngo_representative_verification_evidence','ck_ngo_verification_evidence_disposal','c'),
          ('ngo_representative_verification_evidence','ck_ngo_verification_private_storage_key','c'),
          ('ngo_representative_verification_evidence','fk_ngo_verification_evidence_disposal_request','f'),
          ('ngo_representative_verification_evidence','fk_ngo_verification_evidence_disposal_completed','f'),
          ('ngo_representative_verification_events','fk_ngo_verification_event_application','f'),
          ('ngo_representative_verification_events','fk_ngo_verification_event_actor','f'),
          ('ngo_representative_verification_events','ck_ngo_verification_event_action','c'),
          ('ngo_representative_verification_events','ck_ngo_verification_event_actor_type','c'),
          ('ngo_representative_verification_events','ck_ngo_verification_event_from_status','c'),
          ('ngo_representative_verification_events','ck_ngo_verification_event_to_status','c'),
          ('ngo_representative_verification_events','ck_ngo_verification_decision_reason','c'),
          ('ngo_representative_verification_events','ck_ngo_verification_event_redaction_state','c'),
          ('ngo_representative_verification_events','fk_ngo_verification_event_redaction','f'),
          ('ngo_representative_verification_evidence_disposal_events','ngo_representative_verification_evidence_disposal_events_pkey','p'),
          ('ngo_representative_verification_evidence_disposal_events','fk_ngo_verification_disposal_evidence','f'),
          ('ngo_representative_verification_evidence_disposal_events','fk_ngo_verification_disposal_application','f'),
          ('ngo_representative_verification_evidence_disposal_events','fk_ngo_verification_disposal_actor','f'),
          ('ngo_representative_verification_evidence_disposal_events','fk_ngo_verification_disposal_request','f'),
          ('ngo_representative_verification_evidence_disposal_events','ck_ngo_verification_disposal_type','c'),
          ('ngo_representative_verification_evidence_disposal_events','ck_ngo_verification_disposal_actor_type','c'),
          ('ngo_representative_verification_evidence_disposal_events','ck_ngo_verification_disposal_request_reference','c'),
          ('ngo_representative_verification_evidence_disposal_events','ck_ngo_verification_disposal_reason','c'),
          ('ngo_representative_verification_evidence_disposal_events','fk_ngo_verification_disposal_event_redaction','f'),
          ('ngo_representative_verification_audit_metadata_redactions','ngo_representative_verification_audit_metadata_redactions_pkey','p'),
          ('ngo_representative_verification_audit_metadata_redactions','fk_ngo_verification_audit_redaction_event','f'),
          ('ngo_representative_verification_audit_metadata_redactions','ck_ngo_verification_audit_redaction_reference','c'),
          ('ngo_representative_verification_audit_metadata_redactions','ck_ngo_verification_audit_redaction_reason','c'),
          ('ngo_representative_verification_disposal_metadata_redactions','fk_ngo_verification_disposal_redaction_event','f'),
          ('ngo_representative_verification_disposal_metadata_redactions','ck_ngo_verification_disposal_redaction_reference','c'),
          ('ngo_representative_verification_disposal_metadata_redactions','ck_ngo_verification_disposal_redaction_reason','c')
        ) AS expected(table_name, constraint_name, constraint_type)
        WHERE NOT EXISTS (
            SELECT 1 FROM pg_constraint c JOIN pg_class r ON r.oid=c.conrelid
            JOIN pg_namespace n ON n.oid=r.relnamespace
            WHERE n.nspname='public' AND r.relname=expected.table_name
              AND c.conname=expected.constraint_name AND c.contype=expected.constraint_type
        )
    ;
    IF missing_constraints IS NOT NULL THEN
        RAISE EXCEPTION 'Postflight: constraint ausente ou associada a tabela errada: %', missing_constraints;
    END IF;
    IF EXISTS (
        SELECT 1 FROM (VALUES
          ('ngo_representative_verification_config','fk_ngo_verification_retention_approver'),
          ('ngo_representative_verification_retention_approval_events','fk_ngo_verification_retention_approval_actor'),
          ('ngo_representative_verification_applications','fk_ngo_verification_application_applicant'),
          ('ngo_representative_verification_submissions','fk_ngo_verification_submission_user'),
          ('ngo_representative_verification_evidence','fk_ngo_verification_evidence_uploader'),
          ('ngo_representative_verification_events','fk_ngo_verification_event_actor'),
          ('ngo_representative_verification_evidence_disposal_events','fk_ngo_verification_disposal_actor')
        ) AS expected(table_name, constraint_name)
        WHERE NOT EXISTS (
            SELECT 1 FROM pg_constraint c JOIN pg_class r ON r.oid=c.conrelid
            JOIN pg_namespace n ON n.oid=r.relnamespace
            WHERE n.nspname='public' AND r.relname=expected.table_name
              AND c.conname=expected.constraint_name AND c.contype='f' AND c.confdeltype='n'
        )
    ) THEN RAISE EXCEPTION 'Postflight: ON DELETE SET NULL ausente nas referências de usuários.'; END IF;
    IF EXISTS (SELECT 1 FROM (VALUES
        ('public.uq_ngo_verification_application_cycle'),
        ('public.uq_ngo_verification_one_active_application'),
        ('public.uq_ngo_verification_submission_version'),
        ('public.uq_ngo_verification_evidence_key'),
        ('public.uq_ngo_verification_single_draft'),
        ('public.idx_ngo_verification_queue'),
        ('public.idx_ngo_verification_applicant'),
        ('public.idx_ngo_verification_submitted_queue'),
        ('public.idx_ngo_verification_evidence_submission'),
        ('public.idx_ngo_verification_events_latest'),
        ('public.idx_ngo_verification_retention_approvals_latest'),
        ('public.idx_ngo_verification_disposal_events_evidence'),
        ('public.idx_ngo_verification_disposal_events_application'),
        ('public.uq_ngo_verification_evidence_disposal_request'),
        ('public.uq_ngo_verification_evidence_disposal_completed')
      ) AS required(index_name) WHERE to_regclass(required.index_name) IS NULL) THEN
        RAISE EXCEPTION 'Postflight: índice de unicidade/consulta ausente.';
    END IF;
    IF EXISTS (
        SELECT 1
          FROM pg_roles r
          CROSS JOIN unnest(ARRAY[
            'public.request_ngo_verification_evidence_disposal(bigint,integer,text)',
            'public.complete_ngo_verification_evidence_disposal(bigint,bigint,integer)',
            'public.record_ngo_verification_evidence_disposal_failure(bigint,bigint,integer)',
            'public.redact_ngo_verification_audit_event_metadata(bigint,character varying)',
            'public.redact_ngo_verification_disposal_event_metadata(bigint,character varying)',
            'public.approve_ngo_verification_retention_policy(integer,character varying,text)'
          ]) AS f(signature)
         WHERE r.rolname IN ('anon','authenticated','service_role')
           AND has_function_privilege(r.rolname, f.signature, 'EXECUTE')
    ) THEN
        RAISE EXCEPTION 'Postflight: role REST executa procedimento interno de retenção/descarte.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_roles r
        CROSS JOIN unnest(ARRAY[
            'ngo_representative_verification_config',
            'ngo_representative_verification_retention_approval_events',
            'ngo_representative_verification_legacy_exemptions',
            'ngo_representative_verification_applications',
            'ngo_representative_verification_submissions',
            'ngo_representative_verification_evidence',
            'ngo_representative_verification_events',
            'ngo_representative_verification_evidence_disposal_events',
            'ngo_representative_verification_audit_metadata_redactions',
            'ngo_representative_verification_disposal_metadata_redactions'
        ]) AS t(table_name)
        WHERE r.rolname IN ('anon','authenticated','service_role')
          AND has_table_privilege(r.rolname, 'public.' || t.table_name,
                                  'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
    ) THEN
        RAISE EXCEPTION 'Postflight: uma role REST possui acesso SQL direto às tabelas de verificação.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles r WHERE r.rolname IN ('anon','authenticated','service_role')
        AND has_function_privilege(r.rolname,'public.ngo_representative_is_publicly_eligible(integer)','EXECUTE')) THEN
        RAISE EXCEPTION 'Postflight: uma role REST pode executar a função privada de elegibilidade.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles r WHERE r.rolname IN ('anon','authenticated','service_role') AND (
        has_function_privilege(r.rolname,'public.guard_ngo_representative_verification_application()','EXECUTE') OR
        has_function_privilege(r.rolname,'public.guard_ngo_representative_verification_event_consistency()','EXECUTE') OR
        has_function_privilege(r.rolname,'public.guard_ngo_representative_verification_submission()','EXECUTE') OR
        has_function_privilege(r.rolname,'public.guard_ngo_verification_submission_event_consistency()','EXECUTE') OR
        has_function_privilege(r.rolname,'public.guard_ngo_representative_verification_application_parent()','EXECUTE') OR
        has_function_privilege(r.rolname,'public.reject_ngo_representative_verification_history_mutation()','EXECUTE') OR
        has_function_privilege(r.rolname,'public.reject_ngo_representative_verification_config_mutation()','EXECUTE') OR
        has_function_privilege(r.rolname,'public.guard_ngo_representative_verification_retention_approval_event()','EXECUTE'))) THEN
        RAISE EXCEPTION 'Postflight: uma role REST pode executar funções privadas de trigger.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_roles r
        CROSS JOIN unnest(ARRAY[
            'ngo_representative_verification_applications',
            'ngo_representative_verification_retention_approval_events',
            'ngo_representative_verification_submissions',
            'ngo_representative_verification_evidence',
            'ngo_representative_verification_events',
            'ngo_representative_verification_evidence_disposal_events',
            'ngo_representative_verification_audit_metadata_redactions',
            'ngo_representative_verification_disposal_metadata_redactions'
        ]) AS t(table_name)
        CROSS JOIN LATERAL (SELECT pg_get_serial_sequence(format('public.%I',t.table_name),'id') AS sequence_name) seq
        WHERE r.rolname IN ('anon','authenticated','service_role')
          AND seq.sequence_name IS NOT NULL
          AND has_sequence_privilege(r.rolname, seq.sequence_name, 'USAGE,SELECT,UPDATE')
    ) THEN
        RAISE EXCEPTION 'Postflight: uma role REST possui acesso a sequences de verificação.';
    END IF;
END
$postflight$;

COMMIT;

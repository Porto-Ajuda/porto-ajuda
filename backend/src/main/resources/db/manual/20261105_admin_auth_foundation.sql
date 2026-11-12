-- Fundação de verificação admin e posse de telefone.
-- Execução manual, transacional e única; NÃO executar sem revisão/autorização.
-- Requer backup e execução pelo role backend postgres com BYPASSRLS.

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

DO $preflight$
BEGIN
    IF current_user <> 'postgres'
       OR NOT EXISTS (
           SELECT 1 FROM pg_roles
           WHERE rolname = current_user AND rolbypassrls AND NOT rolsuper
       ) THEN
        RAISE EXCEPTION 'Migração abortada: execute como postgres com BYPASSRLS e sem SUPERUSER.';
    END IF;
    IF to_regclass('public.usuarios') IS NULL THEN
        RAISE EXCEPTION 'Migração abortada: public.usuarios não existe.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'usuarios'
          AND column_name = 'telefone_verificado'
    ) OR to_regclass('public.admin_auth_challenges') IS NOT NULL
       OR to_regclass('public.admin_auth_deliveries') IS NOT NULL
       OR to_regclass('public.phone_verification_challenges') IS NOT NULL
       OR to_regclass('public.admin_auth_controls') IS NOT NULL
       OR to_regclass('public.admin_security_audit_events') IS NOT NULL THEN
        RAISE EXCEPTION 'Migração abortada: foram encontrados objetos da migração antes da execução.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon')
       OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        RAISE EXCEPTION 'Migração abortada: roles anon/authenticated não encontradas.';
    END IF;
END
$preflight$;

LOCK TABLE public.usuarios IN SHARE ROW EXCLUSIVE MODE;

ALTER TABLE public.usuarios
    ADD COLUMN telefone_verificado BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE public.admin_auth_challenges (
    id UUID PRIMARY KEY,
    usuario_id INTEGER REFERENCES public.usuarios(id) ON DELETE SET NULL,
    account_key_hash VARCHAR(64) NOT NULL,
    ip_key_hash VARCHAR(64),
    email_snapshot_hash VARCHAR(64),
    phone_snapshot_hash VARCHAR(64),
    email_code_hash VARCHAR(64),
    sms_code_hash VARCHAR(64),
    email_attempts INTEGER NOT NULL DEFAULT 0 CHECK (email_attempts BETWEEN 0 AND 5),
    sms_attempts INTEGER NOT NULL DEFAULT 0 CHECK (sms_attempts BETWEEN 0 AND 5),
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    state VARCHAR(20) NOT NULL
        CHECK (state IN ('ISSUING', 'PENDING', 'VERIFIED', 'DECOY', 'INVALIDATED', 'FAILED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    email_verified_at TIMESTAMP WITH TIME ZONE,
    sms_verified_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    delivery_failure_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_admin_auth_challenge_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_admin_auth_challenges_user_state
    ON public.admin_auth_challenges (usuario_id, state, created_at DESC);
CREATE INDEX idx_admin_auth_challenges_account_created
    ON public.admin_auth_challenges (account_key_hash, created_at DESC);
CREATE INDEX idx_admin_auth_challenges_expiry
    ON public.admin_auth_challenges (expires_at);

-- Transactional outbox: OTPs remain encrypted until delivery and are then erased.
CREATE TABLE public.admin_auth_deliveries (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES public.admin_auth_challenges(id) ON DELETE CASCADE,
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('EMAIL', 'SMS')),
    encrypted_code BYTEA,
    state VARCHAR(12) NOT NULL CHECK (state IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 3),
    lease_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_admin_auth_delivery_challenge_channel UNIQUE (challenge_id, channel),
    CONSTRAINT ck_admin_auth_delivery_payload CHECK (
        (state IN ('PENDING', 'PROCESSING') AND encrypted_code IS NOT NULL)
        OR (state IN ('SENT', 'FAILED') AND encrypted_code IS NULL)
    ),
    CONSTRAINT ck_admin_auth_delivery_lease CHECK (
        (state = 'PROCESSING' AND lease_until IS NOT NULL)
        OR (state <> 'PROCESSING' AND lease_until IS NULL)
    )
);

CREATE INDEX idx_admin_auth_deliveries_claim
    ON public.admin_auth_deliveries (state, lease_until, created_at);

CREATE TABLE public.phone_verification_challenges (
    id UUID PRIMARY KEY,
    usuario_id INTEGER NOT NULL REFERENCES public.usuarios(id) ON DELETE CASCADE,
    phone_snapshot_hash VARCHAR(64) NOT NULL,
    code_hash VARCHAR(64),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    state VARCHAR(20) NOT NULL
        CHECK (state IN ('ISSUING', 'PENDING', 'VERIFIED', 'INVALIDATED', 'FAILED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_phone_verification_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_phone_verification_user_state
    ON public.phone_verification_challenges (usuario_id, state, created_at DESC);
CREATE INDEX idx_phone_verification_expiry
    ON public.phone_verification_challenges (expires_at);

CREATE TABLE public.admin_auth_controls (
    control_key VARCHAR(64) PRIMARY KEY,
    control_type VARCHAR(32) NOT NULL
        CHECK (control_type IN (
            'ACCOUNT_FAILURE', 'IP_FAILURE', 'START_ACCOUNT', 'START_IP', 'VERIFY_IP',
            'PHONE_ISSUE_ACCOUNT', 'PHONE_ISSUE_IP', 'PHONE_VERIFY_IP'
        )),
    counter INTEGER NOT NULL DEFAULT 0 CHECK (counter >= 0),
    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_until TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_admin_auth_controls_lock
    ON public.admin_auth_controls (locked_until)
    WHERE locked_until IS NOT NULL;

CREATE TABLE public.admin_security_audit_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(60) NOT NULL,
    usuario_id_snapshot INTEGER,
    challenge_id UUID,
    account_key_hash VARCHAR(64),
    ip_key_hash VARCHAR(64),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_admin_security_events_time
    ON public.admin_security_audit_events (occurred_at DESC);
CREATE INDEX idx_admin_security_events_account_time
    ON public.admin_security_audit_events (account_key_hash, occurred_at DESC);

-- Sem políticas públicas. Somente o role backend apropriado acessa as tabelas.
ALTER TABLE public.admin_auth_challenges ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_auth_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.phone_verification_challenges ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_auth_controls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_security_audit_events ENABLE ROW LEVEL SECURITY;

REVOKE ALL PRIVILEGES ON TABLE
    public.admin_auth_challenges,
    public.admin_auth_deliveries,
    public.phone_verification_challenges,
    public.admin_auth_controls,
    public.admin_security_audit_events
FROM anon, authenticated;

DO $postflight$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'usuarios'
          AND column_name = 'telefone_verificado'
          AND data_type = 'boolean' AND is_nullable = 'NO'
          AND column_default IN ('false', 'FALSE')
    ) THEN
        RAISE EXCEPTION 'Migração abortada: telefone_verificado não corresponde ao contrato.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_class c
        WHERE c.oid IN (
            'public.admin_auth_challenges'::regclass,
            'public.admin_auth_deliveries'::regclass,
            'public.phone_verification_challenges'::regclass,
            'public.admin_auth_controls'::regclass,
            'public.admin_security_audit_events'::regclass
        ) AND NOT c.relrowsecurity
    ) OR EXISTS (
        SELECT 1 FROM pg_policy p
        WHERE p.polrelid IN (
            'public.admin_auth_challenges'::regclass,
            'public.admin_auth_deliveries'::regclass,
            'public.phone_verification_challenges'::regclass,
            'public.admin_auth_controls'::regclass,
            'public.admin_security_audit_events'::regclass
        )
    ) THEN
        RAISE EXCEPTION 'Migração abortada: proteção RLS ou ausência de políticas divergente.';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM (VALUES ('anon'), ('authenticated')) AS roles(role_name)
        CROSS JOIN (VALUES
            ('public.admin_auth_challenges'::regclass),
            ('public.admin_auth_deliveries'::regclass),
            ('public.phone_verification_challenges'::regclass),
            ('public.admin_auth_controls'::regclass),
            ('public.admin_security_audit_events'::regclass)
        ) AS tables(table_name)
        CROSS JOIN (VALUES
            ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE'), ('TRUNCATE'),
            ('REFERENCES'), ('TRIGGER'), ('MAINTAIN')
        ) AS privileges(privilege_name)
        WHERE has_table_privilege(roles.role_name, tables.table_name, privileges.privilege_name)
    ) THEN
        RAISE EXCEPTION 'Migração abortada: privilégio efetivo público inesperado nas tabelas de autenticação.';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM (VALUES
            ('public.admin_auth_challenges'::regclass),
            ('public.admin_auth_deliveries'::regclass),
            ('public.phone_verification_challenges'::regclass),
            ('public.admin_auth_controls'::regclass),
            ('public.admin_security_audit_events'::regclass)
        ) AS tables(table_name)
        WHERE NOT has_table_privilege(current_user, tables.table_name, 'SELECT')
           OR NOT has_table_privilege(current_user, tables.table_name, 'INSERT')
           OR NOT has_table_privilege(current_user, tables.table_name, 'UPDATE')
           OR NOT has_table_privilege(current_user, tables.table_name, 'DELETE')
    ) THEN
        RAISE EXCEPTION 'Migração abortada: role executor/backend sem privilégios CRUD esperados.';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM (VALUES
            ('public.admin_auth_challenges'::regclass),
            ('public.admin_auth_deliveries'::regclass),
            ('public.phone_verification_challenges'::regclass),
            ('public.admin_auth_controls'::regclass),
            ('public.admin_security_audit_events'::regclass)
        ) AS tables(table_name)
        JOIN pg_class c ON c.oid = tables.table_name
        WHERE c.relowner <> (SELECT oid FROM pg_roles WHERE rolname = current_user)
    ) THEN
        RAISE EXCEPTION 'Migração abortada: propriedade das tabelas de autenticação inesperada.';
    END IF;
END
$postflight$;

COMMIT;

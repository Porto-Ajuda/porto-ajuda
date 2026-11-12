-- M4: sessões administrativas privilegiadas, independentes de sessoes_usuario/JWT.
-- Execução manual, transacional e única. NÃO executar sem revisão e autorização.
-- Pré-requisito: M3 aplicada; executor postgres com BYPASSRLS e sem SUPERUSER.

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
        RAISE EXCEPTION 'M4 abortada: execute como postgres com BYPASSRLS e sem SUPERUSER.';
    END IF;

    IF to_regclass('public.usuarios') IS NULL
       OR to_regclass('public.admin_auth_challenges') IS NULL
       OR to_regclass('public.admin_auth_deliveries') IS NULL
       OR to_regclass('public.phone_verification_challenges') IS NULL
       OR to_regclass('public.admin_auth_controls') IS NULL
       OR to_regclass('public.admin_security_audit_events') IS NULL THEN
        RAISE EXCEPTION 'M4 abortada: pré-requisitos M3 ausentes.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'usuarios'
          AND column_name = 'telefone_verificado'
          AND data_type = 'boolean' AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION 'M4 abortada: public.usuarios.telefone_verificado diverge do contrato M3.';
    END IF;

    IF NOT has_schema_privilege(current_user, 'public', 'USAGE')
       OR NOT has_schema_privilege(current_user, 'public', 'CREATE')
       OR pg_get_userbyid((SELECT relowner FROM pg_class
              WHERE oid = 'public.usuarios'::regclass)) <> current_user
       OR pg_get_userbyid((SELECT relowner FROM pg_class
              WHERE oid = 'public.admin_auth_challenges'::regclass)) <> current_user
       OR NOT has_table_privilege(current_user, 'public.usuarios', 'REFERENCES')
       OR NOT has_table_privilege(current_user, 'public.admin_auth_challenges', 'REFERENCES')
       OR NOT has_table_privilege(current_user, 'public.usuarios', 'UPDATE')
       OR NOT has_table_privilege(current_user, 'public.admin_auth_challenges', 'UPDATE') THEN
        RAISE EXCEPTION 'M4 abortada: executor sem ownership/ACLs para lock e FKs exigidas.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relname IN (
              'admin_privileged_sessions',
              'idx_admin_priv_sessions_user_active',
              'idx_admin_priv_sessions_idle_expiry',
              'idx_admin_priv_sessions_absolute_expiry'
          )
    ) THEN
        RAISE EXCEPTION 'M4 abortada: nome de tabela/índice M4 já existe; inspecione possível aplicação anterior.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon')
       OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        RAISE EXCEPTION 'M4 abortada: roles anon/authenticated ausentes.';
    END IF;
END
$preflight$;

LOCK TABLE public.usuarios IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE public.admin_auth_challenges IN SHARE ROW EXCLUSIVE MODE;

CREATE TABLE public.admin_privileged_sessions (
    id UUID PRIMARY KEY,
    usuario_id INTEGER NOT NULL
        REFERENCES public.usuarios(id) ON DELETE CASCADE,
    challenge_id UUID NOT NULL UNIQUE
        REFERENCES public.admin_auth_challenges(id) ON DELETE CASCADE,
    credential_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL,
    absolute_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idle_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(40),
    created_ip_hash VARCHAR(64),
    CONSTRAINT ck_admin_session_expirations CHECK (
        absolute_expires_at > created_at
        AND idle_expires_at > created_at
        AND idle_expires_at <= absolute_expires_at
    ),
    CONSTRAINT ck_admin_session_revocation CHECK (
        (revoked_at IS NULL AND revocation_reason IS NULL)
        OR (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL)
    )
);

CREATE INDEX idx_admin_priv_sessions_user_active
    ON public.admin_privileged_sessions (usuario_id, revoked_at);
CREATE INDEX idx_admin_priv_sessions_idle_expiry
    ON public.admin_privileged_sessions (idle_expires_at)
    WHERE revoked_at IS NULL;
CREATE INDEX idx_admin_priv_sessions_absolute_expiry
    ON public.admin_privileged_sessions (absolute_expires_at)
    WHERE revoked_at IS NULL;

ALTER TABLE public.admin_privileged_sessions ENABLE ROW LEVEL SECURITY;
REVOKE ALL PRIVILEGES ON TABLE public.admin_privileged_sessions FROM anon, authenticated;

DO $postflight$
DECLARE
    users_att smallint;
    challenge_att smallint;
    session_rel regclass := to_regclass('public.admin_privileged_sessions');
BEGIN
    SELECT attnum INTO users_att FROM pg_attribute
    WHERE attrelid = 'public.usuarios'::regclass AND attname = 'id' AND NOT attisdropped;
    SELECT attnum INTO challenge_att FROM pg_attribute
    WHERE attrelid = 'public.admin_auth_challenges'::regclass AND attname = 'id' AND NOT attisdropped;

    IF session_rel IS NULL
       OR NOT EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = 'public' AND table_name = 'admin_privileged_sessions'
             AND column_name = 'credential_hash' AND data_type = 'character varying'
             AND character_maximum_length = 64 AND is_nullable = 'NO'
       )
       OR NOT EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = 'public' AND table_name = 'admin_privileged_sessions'
             AND column_name = 'absolute_expires_at' AND data_type = 'timestamp with time zone'
             AND is_nullable = 'NO'
       )
       OR NOT EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = 'public' AND table_name = 'admin_privileged_sessions'
             AND column_name = 'idle_expires_at' AND data_type = 'timestamp with time zone'
             AND is_nullable = 'NO'
       ) THEN
        RAISE EXCEPTION 'M4 abortada: colunas da sessão administrativa divergem do contrato.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c
        WHERE c.conrelid = session_rel AND c.contype = 'f'
          AND c.confrelid = 'public.usuarios'::regclass
          AND c.conkey = ARRAY[(SELECT attnum FROM pg_attribute
               WHERE attrelid = session_rel AND attname = 'usuario_id' AND NOT attisdropped)]::smallint[]
          AND c.confkey = ARRAY[users_att]::smallint[]
          AND c.confdeltype = 'c' AND c.convalidated
    ) OR NOT EXISTS (
        SELECT 1 FROM pg_constraint c
        WHERE c.conrelid = session_rel AND c.contype = 'f'
          AND c.confrelid = 'public.admin_auth_challenges'::regclass
          AND c.conkey = ARRAY[(SELECT attnum FROM pg_attribute
               WHERE attrelid = session_rel AND attname = 'challenge_id' AND NOT attisdropped)]::smallint[]
          AND c.confkey = ARRAY[challenge_att]::smallint[]
          AND c.confdeltype = 'c' AND c.convalidated
    ) THEN
        RAISE EXCEPTION 'M4 abortada: FKs da sessão ausentes, divergentes ou não validadas.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_class WHERE oid = session_rel AND relrowsecurity
    ) OR EXISTS (
        SELECT 1 FROM pg_policy WHERE polrelid = session_rel
    ) THEN
        RAISE EXCEPTION 'M4 abortada: RLS deve estar habilitado sem policies.';
    END IF;

    IF pg_get_userbyid((SELECT relowner FROM pg_class WHERE oid = session_rel)) <> current_user
       OR NOT has_table_privilege(current_user, session_rel, 'SELECT')
       OR NOT has_table_privilege(current_user, session_rel, 'INSERT')
       OR NOT has_table_privilege(current_user, session_rel, 'UPDATE')
       OR NOT has_table_privilege(current_user, session_rel, 'DELETE')
       OR EXISTS (
           SELECT 1 FROM (VALUES ('anon'), ('authenticated')) AS roles(role_name)
           CROSS JOIN (VALUES ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE'), ('TRUNCATE'),
                              ('REFERENCES'), ('TRIGGER'), ('MAINTAIN')) AS privs(privilege_name)
           WHERE has_table_privilege(roles.role_name, session_rel, privs.privilege_name)
       ) THEN
        RAISE EXCEPTION 'M4 abortada: owner/privilegios efetivos divergem do contrato.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_index i
        WHERE i.indrelid = session_rel AND i.indisunique AND i.indisvalid AND i.indisready
          AND i.indpred IS NULL AND i.indexprs IS NULL AND i.indnkeyatts = 1
          AND i.indkey[0] = (SELECT attnum FROM pg_attribute
              WHERE attrelid = session_rel AND attname = 'credential_hash' AND NOT attisdropped)
    ) THEN
        RAISE EXCEPTION 'M4 abortada: índice UNIQUE simples de credential_hash ausente/inválido.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c
        WHERE c.conrelid = session_rel AND c.contype = 'u' AND c.convalidated
          AND c.conkey = ARRAY[(SELECT attnum FROM pg_attribute
              WHERE attrelid = session_rel AND attname = 'challenge_id' AND NOT attisdropped)]::smallint[]
    ) THEN
        RAISE EXCEPTION 'M4 abortada: constraint UNIQUE de challenge_id ausente/inválida.';
    END IF;
END
$postflight$;

COMMIT;

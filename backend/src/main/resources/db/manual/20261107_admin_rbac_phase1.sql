-- Fase 1 RBAC administrativo: atribuições e auditoria operacional.
-- Execução manual, única, após revisão e autorização. NÃO executar automaticamente.
-- Pré-requisitos: M3/M4 aplicadas; executor postgres com BYPASSRLS e sem SUPERUSER.

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
        RAISE EXCEPTION 'RBAC Fase 1 abortada: execute como postgres com BYPASSRLS e sem SUPERUSER.';
    END IF;

    IF to_regclass('public.usuarios') IS NULL
       OR to_regclass('public.roles') IS NULL
       OR to_regclass('public.usuario_roles') IS NULL
       OR to_regclass('public.admin_privileged_sessions') IS NULL THEN
        RAISE EXCEPTION 'RBAC Fase 1 abortada: pré-requisitos M3/M4 ausentes.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon')
       OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        RAISE EXCEPTION 'RBAC Fase 1 abortada: roles anon/authenticated ausentes.';
    END IF;

    IF to_regclass('public.admin_assignments') IS NOT NULL
       OR to_regclass('public.admin_operation_audit_events') IS NOT NULL
       OR to_regprocedure('public.prevent_admin_operation_audit_mutation()') IS NOT NULL THEN
        RAISE EXCEPTION 'RBAC Fase 1 abortada: objeto de destino já existe; inspecione antes de continuar.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'usuarios'
          AND column_name = 'ativo' AND data_type = 'boolean'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'usuarios'
          AND column_name = 'email_verificado' AND data_type = 'boolean'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'usuarios'
          AND column_name = 'telefone_verificado' AND data_type = 'boolean'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'roles'
          AND column_name = 'nome'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'usuario_roles'
          AND column_name = 'id_usuario'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'usuario_roles'
          AND column_name = 'id_role'
    ) THEN
        RAISE EXCEPTION 'RBAC Fase 1 abortada: esquema base diverge do contrato esperado.';
    END IF;

    IF NOT has_schema_privilege(current_user, 'public', 'USAGE')
       OR NOT has_schema_privilege(current_user, 'public', 'CREATE')
       OR pg_get_userbyid((SELECT relowner FROM pg_class WHERE oid = 'public.usuarios'::regclass)) <> current_user
       OR pg_get_userbyid((SELECT relowner FROM pg_class WHERE oid = 'public.roles'::regclass)) <> current_user
       OR pg_get_userbyid((SELECT relowner FROM pg_class WHERE oid = 'public.usuario_roles'::regclass)) <> current_user
       OR NOT has_table_privilege(current_user, 'public.usuarios', 'REFERENCES') THEN
        RAISE EXCEPTION 'RBAC Fase 1 abortada: executor sem ownership/ACLs para criar as tabelas e FKs.';
    END IF;
END
$preflight$;

LOCK TABLE public.usuarios IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE public.roles IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE public.usuario_roles IN SHARE ROW EXCLUSIVE MODE;

CREATE TABLE public.admin_assignments (
    id UUID PRIMARY KEY,
    usuario_id INTEGER REFERENCES public.usuarios(id) ON DELETE SET NULL,
    usuario_id_snapshot INTEGER NOT NULL,
    profile VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    granted_by_user_id_snapshot INTEGER,
    last_modified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified_by_user_id_snapshot INTEGER,
    revoked_at TIMESTAMP WITH TIME ZONE,
    reason VARCHAR(2000) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_admin_assignment_profile CHECK (
        profile IN ('SUPER_ADMIN', 'PLATFORM_ADMIN', 'MODERATOR', 'AUDITOR')
    ),
    CONSTRAINT ck_admin_assignment_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_admin_assignment_lifecycle CHECK (
        (status = 'ACTIVE' AND usuario_id IS NOT NULL AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL)
    ),
    CONSTRAINT ck_admin_assignment_reason CHECK (BTRIM(reason) <> ''),
    CONSTRAINT ck_admin_assignment_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_admin_assignments_one_active_per_user
    ON public.admin_assignments (usuario_id)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_admin_assignments_profile_status
    ON public.admin_assignments (profile, status);
CREATE INDEX idx_admin_assignments_modified
    ON public.admin_assignments (last_modified_at DESC, id);

CREATE TABLE public.admin_operation_audit_events (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_user_id_snapshot INTEGER,
    actor_profile VARCHAR(32),
    affected_profile VARCHAR(32),
    actor_session_id UUID,
    actor_source VARCHAR(24) NOT NULL,
    permission_name VARCHAR(64) NOT NULL,
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(100) NOT NULL,
    previous_state_summary VARCHAR(500),
    new_state_summary VARCHAR(500),
    reason VARCHAR(2000) NOT NULL,
    correlation_id UUID NOT NULL,
    CONSTRAINT ck_admin_operation_actor_profile CHECK (
        actor_profile IS NULL OR actor_profile IN (
            'SUPER_ADMIN', 'PLATFORM_ADMIN', 'MODERATOR', 'AUDITOR'
        )
    ),
    CONSTRAINT ck_admin_operation_affected_profile CHECK (
        affected_profile IS NULL OR affected_profile IN (
            'SUPER_ADMIN', 'PLATFORM_ADMIN', 'MODERATOR', 'AUDITOR'
        )
    ),
    CONSTRAINT ck_admin_operation_actor_source CHECK (
        actor_source IN ('ADMIN_SESSION', 'BOOTSTRAP', 'SELF_SERVICE', 'SYSTEM')
    ),
    CONSTRAINT ck_admin_operation_actor_context CHECK (
        (actor_source = 'ADMIN_SESSION' AND actor_user_id_snapshot IS NOT NULL
            AND actor_profile IS NOT NULL AND actor_session_id IS NOT NULL)
        OR (actor_source <> 'ADMIN_SESSION' AND actor_session_id IS NULL)
    ),
    CONSTRAINT ck_admin_operation_reason CHECK (BTRIM(reason) <> '')
);

CREATE INDEX idx_admin_operation_audit_occurred
    ON public.admin_operation_audit_events (occurred_at DESC, id);
CREATE INDEX idx_admin_operation_audit_target
    ON public.admin_operation_audit_events (target_type, target_id, occurred_at DESC);
CREATE INDEX idx_admin_operation_audit_actor
    ON public.admin_operation_audit_events (actor_user_id_snapshot, occurred_at DESC);

ALTER TABLE public.admin_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_operation_audit_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL PRIVILEGES ON TABLE public.admin_assignments FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.admin_operation_audit_events FROM anon, authenticated;

CREATE FUNCTION public.prevent_admin_operation_audit_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
    RAISE EXCEPTION 'admin_operation_audit_events é append-only.';
END
$function$;

CREATE TRIGGER trg_admin_operation_audit_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON public.admin_operation_audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_admin_operation_audit_mutation();

REVOKE ALL ON FUNCTION public.prevent_admin_operation_audit_mutation() FROM PUBLIC;

DO $postflight$
DECLARE
    assignment_rel regclass := to_regclass('public.admin_assignments');
    audit_rel regclass := to_regclass('public.admin_operation_audit_events');
BEGIN
    IF assignment_rel IS NULL OR audit_rel IS NULL
       OR NOT EXISTS (SELECT 1 FROM pg_class WHERE oid = assignment_rel AND relrowsecurity)
       OR NOT EXISTS (SELECT 1 FROM pg_class WHERE oid = audit_rel AND relrowsecurity)
       OR EXISTS (SELECT 1 FROM pg_policy WHERE polrelid IN (assignment_rel, audit_rel)) THEN
        RAISE EXCEPTION 'RBAC Fase 1 abortada: tabelas/RLS divergem do contrato.';
    END IF;

    IF pg_get_userbyid((SELECT relowner FROM pg_class WHERE oid = assignment_rel)) <> current_user
       OR pg_get_userbyid((SELECT relowner FROM pg_class WHERE oid = audit_rel)) <> current_user
       OR NOT has_table_privilege(current_user, assignment_rel, 'SELECT,INSERT,UPDATE,DELETE')
       OR NOT has_table_privilege(current_user, audit_rel, 'SELECT,INSERT')
       OR EXISTS (
           SELECT 1
           FROM (VALUES ('anon'), ('authenticated')) AS roles(role_name)
           CROSS JOIN (VALUES ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE'),
                              ('TRUNCATE'), ('REFERENCES'), ('TRIGGER'), ('MAINTAIN')) AS privs(privilege_name)
           WHERE has_table_privilege(roles.role_name, audit_rel, privs.privilege_name)
              OR has_table_privilege(roles.role_name, assignment_rel, privs.privilege_name)
       ) THEN
        RAISE EXCEPTION 'RBAC Fase 1 abortada: owner ou ACLs divergem do contrato.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgrelid = audit_rel AND tgname = 'trg_admin_operation_audit_append_only'
          AND NOT tgisinternal AND tgenabled = 'O'
    ) THEN
        RAISE EXCEPTION 'RBAC Fase 1 abortada: trigger append-only ausente/inativo.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_index i
        WHERE i.indrelid = assignment_rel AND i.indisunique AND i.indisvalid AND i.indisready
          AND i.indpred IS NOT NULL AND i.indnkeyatts = 1
          AND i.indkey[0] = (SELECT attnum FROM pg_attribute
              WHERE attrelid = assignment_rel AND attname = 'usuario_id' AND NOT attisdropped)
    ) THEN
        RAISE EXCEPTION 'RBAC Fase 1 abortada: índice único de atribuição ativa ausente/inválido.';
    END IF;
END
$postflight$;

COMMIT;

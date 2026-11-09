-- Bootstrap manual de um SUPER_ADMIN inicial; não executa por migrations/startup.
-- Exige aprovação operacional registrada e execução revisada em sessão controlada.
-- Informe email exato de uma conta existente, ativa, com MFA elegível e ROLE_ADMIN.
-- Digite uma referência no formato PA-12345; nunca senha, OTP, cookie ou token.

\set ON_ERROR_STOP on
\prompt 'Email exato da conta aprovada para bootstrap SUPER_ADMIN: ' target_email
\prompt 'Referência do ticket/aprovação: ' approval_reference

DO $preflight$
BEGIN
    IF current_user <> 'postgres'
       OR NOT EXISTS (
           SELECT 1 FROM pg_roles
           WHERE rolname = current_user AND rolbypassrls AND NOT rolsuper
       ) THEN
        RAISE EXCEPTION 'Bootstrap abortado: execute como postgres com BYPASSRLS e sem SUPERUSER.';
    END IF;
    IF to_regclass('public.admin_assignments') IS NULL
       OR to_regclass('public.admin_operation_audit_events') IS NULL
       OR to_regclass('public.usuarios') IS NULL
       OR to_regclass('public.usuario_roles') IS NULL
       OR to_regclass('public.roles') IS NULL THEN
        RAISE EXCEPTION 'Bootstrap abortado: migração RBAC Fase 1 ou schema base ausente.';
    END IF;
END
$preflight$;

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '2min';
SELECT pg_advisory_xact_lock(1346458434, 1);
LOCK TABLE public.admin_assignments IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE public.admin_operation_audit_events IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE _rbac_bootstrap_target ON COMMIT DROP AS
SELECT u.id AS usuario_id
  FROM public.usuarios u
  JOIN public.usuario_roles ur ON ur.id_usuario = u.id
  JOIN public.roles r ON r.id = ur.id_role
 WHERE lower(u.email) = lower(:'target_email')
   AND u.ativo = TRUE
   AND u.email_verificado = TRUE
   AND u.telefone_verificado = TRUE
   AND u.email IS NOT NULL
   AND BTRIM(u.email) <> ''
   AND u.telefone IS NOT NULL
   AND BTRIM(u.telefone) <> ''
   AND u.telefone ~ '^[+0-9[:space:]().-]+$'
   AND regexp_replace(u.telefone, '[^0-9]', '', 'g') ~ '^(55)?[1-9][0-9]9[0-9]{8}$'
   AND r.nome = 'ADMIN';

CREATE TEMP TABLE _rbac_bootstrap_request ON COMMIT DROP AS
SELECT :'approval_reference'::text AS approval_reference;

DO $validate_target$
BEGIN
    IF (SELECT COUNT(*) FROM _rbac_bootstrap_target) <> 1 THEN
        RAISE EXCEPTION 'Bootstrap abortado: a conta informada não corresponde a exatamente um ADMIN ativo e elegível.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.admin_assignments a
        JOIN _rbac_bootstrap_target t ON t.usuario_id = a.usuario_id
        WHERE a.status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'Bootstrap abortado: a conta já possui atribuição administrativa ativa.';
    END IF;

    IF (SELECT approval_reference FROM _rbac_bootstrap_request) !~* '^PA-[0-9]{1,12}$' THEN
        RAISE EXCEPTION 'Bootstrap abortado: referência de aprovação ausente/inválida.';
    END IF;
END
$validate_target$;

WITH target AS (
    SELECT usuario_id FROM _rbac_bootstrap_target
), created AS (
    INSERT INTO public.admin_assignments (
        id, usuario_id, usuario_id_snapshot, profile, status, granted_at,
        granted_by_user_id_snapshot, last_modified_at,
        last_modified_by_user_id_snapshot, revoked_at, reason, version
    )
    SELECT gen_random_uuid(), target.usuario_id, target.usuario_id,
           'SUPER_ADMIN', 'ACTIVE', now(), NULL, now(), NULL, NULL,
           request.approval_reference, 0
      FROM target CROSS JOIN _rbac_bootstrap_request request
    RETURNING id, usuario_id
)
INSERT INTO public.admin_operation_audit_events (
    id, occurred_at, actor_user_id_snapshot, actor_profile, affected_profile,
    actor_session_id, actor_source, permission_name, action,
    target_type, target_id, previous_state_summary,
    new_state_summary, reason, correlation_id
)
SELECT gen_random_uuid(), now(), NULL, NULL, 'SUPER_ADMIN', NULL, 'BOOTSTRAP',
       'ADMIN_BOOTSTRAP', 'ADMIN_ASSIGNMENT_BOOTSTRAPPED', 'USER',
       created.usuario_id::text, NULL, 'profile=SUPER_ADMIN',
       (SELECT approval_reference FROM _rbac_bootstrap_request), gen_random_uuid()
  FROM created;

DO $postflight$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.admin_assignments a
        JOIN public.usuarios u ON u.id = a.usuario_id
        JOIN public.usuario_roles ur ON ur.id_usuario = u.id
        JOIN public.roles r ON r.id = ur.id_role
        WHERE a.status = 'ACTIVE' AND a.profile = 'SUPER_ADMIN'
          AND u.ativo = TRUE AND u.email_verificado = TRUE
          AND u.telefone_verificado = TRUE AND r.nome = 'ADMIN'
          AND u.email IS NOT NULL AND BTRIM(u.email) <> ''
          AND u.telefone IS NOT NULL AND BTRIM(u.telefone) <> ''
          AND u.telefone ~ '^[+0-9[:space:]().-]+$'
          AND regexp_replace(u.telefone, '[^0-9]', '', 'g') ~ '^(55)?[1-9][0-9]9[0-9]{8}$'
    ) THEN
        RAISE EXCEPTION 'Bootstrap abortado: nenhum SUPER_ADMIN ativo elegível foi encontrado após o insert.';
    END IF;
END
$postflight$;

COMMIT;

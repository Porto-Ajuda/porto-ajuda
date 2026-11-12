-- Migração manual, transacional e de execução única.
-- Converte oscs.ativo para status e cria a trilha de auditoria de status.
-- Inativas legadas recebem origem INDETERMINADA; eventos históricos não são inferidos.
-- Requer janela de manutenção: os locks bloqueiam escrita nas tabelas afetadas.
-- Não executar sem backup, validação dos diagnósticos e autorização explícita.

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

-- =========================================================
-- PREFLIGHT
-- =========================================================

DO $preflight$
DECLARE
    oscs_rel regclass;
    usuarios_rel regclass;
    oscs_id_att smallint;
    usuarios_id_att smallint;
    ativo_att smallint;
BEGIN
    IF current_user <> 'postgres'
       OR NOT EXISTS (
           SELECT 1
           FROM pg_roles
           WHERE rolname = current_user
             AND rolbypassrls
             AND NOT rolsuper
       ) THEN
        RAISE EXCEPTION
            'M1 abortada: execute como postgres com rolsuper=false e rolbypassrls=true, conforme o papel backend esperado.';
    END IF;

    oscs_rel := to_regclass('public.oscs');
    usuarios_rel := to_regclass('public.usuarios');

    IF oscs_rel IS NULL THEN
        RAISE EXCEPTION 'M1 abortada: public.oscs não existe.';
    END IF;

    IF usuarios_rel IS NULL THEN
        RAISE EXCEPTION 'M1 abortada: public.usuarios não existe.';
    END IF;

    IF to_regclass('public.historico_status_osc') IS NOT NULL THEN
        RAISE EXCEPTION
            'M1 abortada: public.historico_status_osc já existe; inspecione possível aplicação anterior.';
    END IF;

    IF to_regclass('public.historico_status_osc_id_seq') IS NOT NULL THEN
        RAISE EXCEPTION
            'M1 abortada: sequence historico_status_osc_id_seq já existe sem a tabela; inspecione aplicação parcial.';
    END IF;

    SELECT attnum INTO oscs_id_att
    FROM pg_attribute
    WHERE attrelid = oscs_rel
      AND attname = 'id'
      AND NOT attisdropped;

    SELECT attnum INTO usuarios_id_att
    FROM pg_attribute
    WHERE attrelid = usuarios_rel
      AND attname = 'id'
      AND NOT attisdropped;

    IF oscs_id_att IS NULL OR usuarios_id_att IS NULL THEN
        RAISE EXCEPTION
            'M1 abortada: oscs.id e usuarios.id são necessários.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'oscs'
          AND column_name = 'id'
          AND data_type = 'integer'
          AND is_nullable = 'NO'
    ) OR NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'usuarios'
          AND column_name = 'id'
          AND data_type = 'integer'
          AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION
            'M1 abortada: oscs.id e usuarios.id devem ser INTEGER NOT NULL.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = oscs_rel
          AND contype = 'p'
          AND conkey = ARRAY[oscs_id_att]::smallint[]
    ) OR NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = usuarios_rel
          AND contype = 'p'
          AND conkey = ARRAY[usuarios_id_att]::smallint[]
    ) THEN
        RAISE EXCEPTION
            'M1 abortada: esperava PKs simples em oscs.id e usuarios.id.';
    END IF;

    IF pg_get_serial_sequence('public.oscs', 'id') IS NULL
       OR pg_get_serial_sequence('public.usuarios', 'id') IS NULL THEN
        RAISE EXCEPTION
            'M1 abortada: oscs.id e usuarios.id precisam de geração identity/serial compatível com JPA IDENTITY.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'oscs'
          AND column_name = 'ativo'
          AND data_type = 'boolean'
          AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION
            'M1 abortada: oscs.ativo deve existir como BOOLEAN NOT NULL.';
    END IF;

    SELECT attnum INTO ativo_att
    FROM pg_attribute
    WHERE attrelid = oscs_rel
      AND attname = 'ativo'
      AND NOT attisdropped;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = oscs_rel
          AND ativo_att = ANY(c.conkey)
          AND c.contype <> 'n'
    ) OR EXISTS (
        SELECT 1
        FROM pg_depend d
        WHERE d.refclassid = 'pg_class'::regclass
          AND d.refobjid = oscs_rel
          AND d.refobjsubid = ativo_att
          AND d.classid <> 'pg_attrdef'::regclass
          AND d.deptype <> 'i'
          AND NOT (
              d.classid = 'pg_constraint'::regclass
              AND EXISTS (
                  SELECT 1
                  FROM pg_constraint c
                  WHERE c.oid = d.objid
                    AND c.contype = 'n'
              )
          )
    ) THEN
        RAISE EXCEPTION
            'M1 abortada: há constraints/objetos dependentes de oscs.ativo; inventarie-os antes de remover a coluna.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'oscs'
          AND column_name IN (
              'status',
              'origem_ultima_desativacao',
              'data_ultima_desativacao',
              'id_usuario_desativou',
              'motivo_ultima_desativacao'
          )
    ) OR EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = oscs_rel
          AND conname IN (
              'ck_oscs_status',
              'ck_oscs_origem_ultima_desativacao',
              'fk_oscs_usuario_desativou'
          )
    ) OR EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relname IN (
              'idx_oscs_status_id',
              'idx_historico_status_osc_osc_data'
          )
    ) THEN
        RAISE EXCEPTION
            'M1 abortada: foram encontrados artefatos de aplicação parcial ou anterior.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.oscs
        WHERE ativo IS NULL
    ) THEN
        RAISE EXCEPTION 'M1 abortada: oscs.ativo contém NULL.';
    END IF;
END
$preflight$;

-- =========================================================
-- LOCKS
-- =========================================================

LOCK TABLE public.oscs IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE public.usuarios IN SHARE ROW EXCLUSIVE MODE;

-- =========================================================
-- RECHECK
-- =========================================================

DO $recheck$
DECLARE
    oscs_rel regclass := to_regclass('public.oscs');
    usuarios_rel regclass := to_regclass('public.usuarios');
    oscs_id_att smallint;
    usuarios_id_att smallint;
    ativo_att smallint;
BEGIN
    IF oscs_rel IS NULL
       OR usuarios_rel IS NULL
       OR to_regclass('public.historico_status_osc') IS NOT NULL
       OR to_regclass('public.historico_status_osc_id_seq') IS NOT NULL THEN
        RAISE EXCEPTION
            'M1 abortada: tabelas mudaram desde o preflight.';
    END IF;

    SELECT attnum INTO oscs_id_att
    FROM pg_attribute
    WHERE attrelid = oscs_rel
      AND attname = 'id'
      AND NOT attisdropped;

    SELECT attnum INTO usuarios_id_att
    FROM pg_attribute
    WHERE attrelid = usuarios_rel
      AND attname = 'id'
      AND NOT attisdropped;

    SELECT attnum INTO ativo_att
    FROM pg_attribute
    WHERE attrelid = oscs_rel
      AND attname = 'ativo'
      AND NOT attisdropped;

    IF oscs_id_att IS NULL
       OR usuarios_id_att IS NULL
       OR NOT EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conrelid = oscs_rel
             AND contype = 'p'
             AND conkey = ARRAY[oscs_id_att]::smallint[]
       )
       OR NOT EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conrelid = usuarios_rel
             AND contype = 'p'
             AND conkey = ARRAY[usuarios_id_att]::smallint[]
       )
       OR pg_get_serial_sequence('public.oscs', 'id') IS NULL
       OR pg_get_serial_sequence('public.usuarios', 'id') IS NULL
       OR ativo_att IS NULL
       OR EXISTS (
           SELECT 1
           FROM pg_constraint c
           WHERE c.conrelid = oscs_rel
             AND ativo_att = ANY(c.conkey)
             AND c.contype <> 'n'
       )
       OR EXISTS (
           SELECT 1
           FROM pg_depend d
           WHERE d.refclassid = 'pg_class'::regclass
             AND d.refobjid = oscs_rel
             AND d.refobjsubid = ativo_att
             AND d.classid <> 'pg_attrdef'::regclass
             AND d.deptype <> 'i'
             AND NOT (
                 d.classid = 'pg_constraint'::regclass
                 AND EXISTS (
                     SELECT 1
                     FROM pg_constraint c
                     WHERE c.oid = d.objid
                       AND c.contype = 'n'
                 )
             )
       )
       OR NOT EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'oscs'
             AND column_name = 'ativo'
             AND data_type = 'boolean'
             AND is_nullable = 'NO'
       )
       OR EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'oscs'
             AND column_name IN (
                 'status',
                 'origem_ultima_desativacao',
                 'data_ultima_desativacao',
                 'id_usuario_desativou',
                 'motivo_ultima_desativacao'
             )
       )
       OR EXISTS (
           SELECT 1
           FROM public.oscs
           WHERE ativo IS NULL
       ) THEN
        RAISE EXCEPTION
            'M1 abortada: as premissas de PK, ativo ou estado parcial mudaram após o preflight.';
    END IF;
END
$recheck$;

-- =========================================================
-- CONVERSÃO DE STATUS
-- =========================================================

ALTER TABLE public.oscs
    ADD COLUMN status VARCHAR(20);

UPDATE public.oscs
SET status = CASE
    WHEN ativo THEN 'ATIVA'
    ELSE 'INATIVA'
END;

ALTER TABLE public.oscs
    ALTER COLUMN status SET DEFAULT 'INATIVA',
    ALTER COLUMN status SET NOT NULL,
    ADD COLUMN origem_ultima_desativacao VARCHAR(30),
    ADD COLUMN data_ultima_desativacao TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN id_usuario_desativou INTEGER,
    ADD COLUMN motivo_ultima_desativacao TEXT;

-- Não inventar ator, motivo, data ou eventos para estados legados.
UPDATE public.oscs
SET origem_ultima_desativacao = 'INDETERMINADA'
WHERE status = 'INATIVA';

ALTER TABLE public.oscs
    ADD CONSTRAINT ck_oscs_status
        CHECK (status IN ('ATIVA', 'INATIVA')),
    ADD CONSTRAINT ck_oscs_origem_ultima_desativacao
        CHECK (
            origem_ultima_desativacao IS NULL
            OR origem_ultima_desativacao IN (
                'ONG',
                'PORTO_AJUDA',
                'INDETERMINADA'
            )
        ),
    ADD CONSTRAINT fk_oscs_usuario_desativou
        FOREIGN KEY (id_usuario_desativou)
        REFERENCES public.usuarios(id)
        ON DELETE SET NULL;

CREATE INDEX idx_oscs_status_id
    ON public.oscs (status, id);

-- =========================================================
-- TABELA DE HISTÓRICO
-- =========================================================

CREATE TABLE public.historico_status_osc (
    id BIGSERIAL PRIMARY KEY,
    id_osc INTEGER,
    id_osc_snapshot INTEGER NOT NULL,
    status_anterior VARCHAR(20),
    status_novo VARCHAR(20) NOT NULL,
    origem_acao VARCHAR(30) NOT NULL,
    origem_desativacao_anterior VARCHAR(30),
    data_desativacao_anterior TIMESTAMP WITHOUT TIME ZONE,
    id_usuario_desativou_anterior INTEGER,
    motivo_desativacao_anterior TEXT,
    id_usuario INTEGER,
    autoridade_ator VARCHAR(100) NOT NULL,
    data_criacao TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    motivo TEXT,

    CONSTRAINT fk_historico_status_osc_osc
        FOREIGN KEY (id_osc)
        REFERENCES public.oscs(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_historico_status_osc_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES public.usuarios(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_historico_status_osc_desativador_anterior
        FOREIGN KEY (id_usuario_desativou_anterior)
        REFERENCES public.usuarios(id)
        ON DELETE SET NULL,

    CONSTRAINT ck_historico_status_osc_id_snapshot
        CHECK (id_osc IS NULL OR id_osc = id_osc_snapshot),

    CONSTRAINT ck_historico_status_osc_status_anterior
        CHECK (
            status_anterior IS NULL
            OR status_anterior IN ('ATIVA', 'INATIVA')
        ),

    CONSTRAINT ck_historico_status_osc_status_novo
        CHECK (status_novo IN ('ATIVA', 'INATIVA')),

    CONSTRAINT ck_historico_status_osc_origem
        CHECK (
            origem_acao IN (
                'ONG',
                'PORTO_AJUDA',
                'INDETERMINADA'
            )
        ),

    CONSTRAINT ck_historico_status_osc_origem_anterior
        CHECK (
            origem_desativacao_anterior IS NULL
            OR origem_desativacao_anterior IN (
                'ONG',
                'PORTO_AJUDA',
                'INDETERMINADA'
            )
        )
);

-- =========================================================
-- RLS E PRIVILÉGIOS
-- =========================================================

ALTER TABLE public.historico_status_osc
    ENABLE ROW LEVEL SECURITY;

REVOKE ALL PRIVILEGES
ON TABLE public.historico_status_osc
FROM anon, authenticated;

REVOKE ALL PRIVILEGES
ON SEQUENCE public.historico_status_osc_id_seq
FROM anon, authenticated;

CREATE INDEX idx_historico_status_osc_osc_data
    ON public.historico_status_osc (id_osc_snapshot, data_criacao);

-- =========================================================
-- PRE-DROP CHECK
-- =========================================================

DO $pre_drop_check$
DECLARE
    default_status TEXT;

    oscs_id_att smallint;
    usuarios_id_att smallint;
    oscs_desativador_att smallint;

    historico_osc_att smallint;
    historico_usuario_att smallint;
    historico_desativador_att smallint;
BEGIN
    -- Obter os números das colunas para comparar as FKs exatamente.
    SELECT attnum INTO oscs_id_att
    FROM pg_attribute
    WHERE attrelid = 'public.oscs'::regclass
      AND attname = 'id'
      AND NOT attisdropped;

    SELECT attnum INTO usuarios_id_att
    FROM pg_attribute
    WHERE attrelid = 'public.usuarios'::regclass
      AND attname = 'id'
      AND NOT attisdropped;

    SELECT attnum INTO oscs_desativador_att
    FROM pg_attribute
    WHERE attrelid = 'public.oscs'::regclass
      AND attname = 'id_usuario_desativou'
      AND NOT attisdropped;

    SELECT attnum INTO historico_osc_att
    FROM pg_attribute
    WHERE attrelid = 'public.historico_status_osc'::regclass
      AND attname = 'id_osc'
      AND NOT attisdropped;

    SELECT attnum INTO historico_usuario_att
    FROM pg_attribute
    WHERE attrelid = 'public.historico_status_osc'::regclass
      AND attname = 'id_usuario'
      AND NOT attisdropped;

    SELECT attnum INTO historico_desativador_att
    FROM pg_attribute
    WHERE attrelid = 'public.historico_status_osc'::regclass
      AND attname = 'id_usuario_desativou_anterior'
      AND NOT attisdropped;

    IF oscs_id_att IS NULL
       OR usuarios_id_att IS NULL
       OR oscs_desativador_att IS NULL
       OR historico_osc_att IS NULL
       OR historico_usuario_att IS NULL
       OR historico_desativador_att IS NULL THEN
        RAISE EXCEPTION
            'M1 abortada: não foi possível identificar todas as colunas necessárias para validar as FKs.';
    END IF;

    -- Validar conversão, enumerações e origem legada.
    IF EXISTS (
        SELECT 1
        FROM public.oscs
        WHERE status NOT IN ('ATIVA', 'INATIVA')
           OR (ativo AND status <> 'ATIVA')
           OR (NOT ativo AND status <> 'INATIVA')
           OR (
               status = 'INATIVA'
               AND origem_ultima_desativacao IS DISTINCT FROM 'INDETERMINADA'
           )
           OR (
               status = 'ATIVA'
               AND origem_ultima_desativacao IS NOT NULL
           )
    ) THEN
        RAISE EXCEPTION
            'M1 abortada: conversão de status/origem não preservou os dados de origem.';
    END IF;

    -- Validar default.
    SELECT pg_get_expr(d.adbin, d.adrelid)
    INTO default_status
    FROM pg_attrdef d
    JOIN pg_attribute a
      ON a.attrelid = d.adrelid
     AND a.attnum = d.adnum
    WHERE d.adrelid = 'public.oscs'::regclass
      AND a.attname = 'status';

    IF default_status IS NULL
       OR default_status NOT LIKE '%INATIVA%' THEN
        RAISE EXCEPTION
            'M1 abortada: default de oscs.status não corresponde a INATIVA.';
    END IF;

    -- Validar FK oscs.id_usuario_desativou -> usuarios.id.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = 'public.oscs'::regclass
          AND c.conname = 'fk_oscs_usuario_desativou'
          AND c.contype = 'f'
          AND c.convalidated
          AND c.confdeltype = 'n'
          AND c.conkey = ARRAY[oscs_desativador_att]::smallint[]
          AND c.confkey = ARRAY[usuarios_id_att]::smallint[]
          AND c.confrelid = 'public.usuarios'::regclass
    ) THEN
        RAISE EXCEPTION
            'M1 abortada: FK fk_oscs_usuario_desativou não referencia exatamente oscs.id_usuario_desativou -> usuarios.id com ON DELETE SET NULL.';
    END IF;

    -- Validar FK historico.id_osc -> oscs.id.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = 'public.historico_status_osc'::regclass
          AND c.conname = 'fk_historico_status_osc_osc'
          AND c.contype = 'f'
          AND c.convalidated
          AND c.confdeltype = 'n'
          AND c.conkey = ARRAY[historico_osc_att]::smallint[]
          AND c.confkey = ARRAY[oscs_id_att]::smallint[]
          AND c.confrelid = 'public.oscs'::regclass
    ) THEN
        RAISE EXCEPTION
            'M1 abortada: FK fk_historico_status_osc_osc não referencia exatamente historico_status_osc.id_osc -> oscs.id com ON DELETE SET NULL.';
    END IF;

    -- Validar FK historico.id_usuario -> usuarios.id.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = 'public.historico_status_osc'::regclass
          AND c.conname = 'fk_historico_status_osc_usuario'
          AND c.contype = 'f'
          AND c.convalidated
          AND c.confdeltype = 'n'
          AND c.conkey = ARRAY[historico_usuario_att]::smallint[]
          AND c.confkey = ARRAY[usuarios_id_att]::smallint[]
          AND c.confrelid = 'public.usuarios'::regclass
    ) THEN
        RAISE EXCEPTION
            'M1 abortada: FK fk_historico_status_osc_usuario não referencia exatamente historico_status_osc.id_usuario -> usuarios.id com ON DELETE SET NULL.';
    END IF;

    -- Validar FK historico.id_usuario_desativou_anterior -> usuarios.id.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = 'public.historico_status_osc'::regclass
          AND c.conname = 'fk_historico_status_osc_desativador_anterior'
          AND c.contype = 'f'
          AND c.convalidated
          AND c.confdeltype = 'n'
          AND c.conkey = ARRAY[historico_desativador_att]::smallint[]
          AND c.confkey = ARRAY[usuarios_id_att]::smallint[]
          AND c.confrelid = 'public.usuarios'::regclass
    ) THEN
        RAISE EXCEPTION
            'M1 abortada: FK fk_historico_status_osc_desativador_anterior não referencia exatamente historico_status_osc.id_usuario_desativou_anterior -> usuarios.id com ON DELETE SET NULL.';
    END IF;

    -- Validar sequence.
    IF pg_get_serial_sequence(
        'public.historico_status_osc',
        'id'
    ) IS NULL THEN
        RAISE EXCEPTION
            'M1 abortada: historico_status_osc.id não tem sequência identity/serial para JPA IDENTITY.';
    END IF;

    -- Validar RLS.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_class
        WHERE oid = 'public.historico_status_osc'::regclass
          AND relrowsecurity = TRUE
    ) THEN
        RAISE EXCEPTION
            'M1 abortada: RLS não está habilitado em historico_status_osc.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_policy
        WHERE polrelid = 'public.historico_status_osc'::regclass
    ) THEN
        RAISE EXCEPTION
            'M1 abortada: políticas RLS inesperadas em historico_status_osc.';
    END IF;
END
$pre_drop_check$;

-- =========================================================
-- REMOÇÃO DA COLUNA LEGADA
-- =========================================================

ALTER TABLE public.oscs
    DROP COLUMN ativo;

-- =========================================================
-- POSTFLIGHT
-- =========================================================

DO $postflight$
DECLARE
    history_seq regclass;
BEGIN
    history_seq := pg_get_serial_sequence(
        'public.historico_status_osc',
        'id'
    )::regclass;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'oscs'
          AND column_name = 'ativo'
    ) OR NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'oscs'
          AND column_name = 'status'
          AND data_type = 'character varying'
          AND character_maximum_length = 20
          AND is_nullable = 'NO'
    ) OR NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'historico_status_osc'
          AND column_name = 'id_osc_snapshot'
          AND data_type = 'integer'
          AND is_nullable = 'NO'
    ) OR EXISTS (
        SELECT 1
        FROM public.oscs
        WHERE status NOT IN ('ATIVA', 'INATIVA')
           OR (
               status = 'INATIVA'
               AND origem_ultima_desativacao IS DISTINCT FROM 'INDETERMINADA'
           )
           OR (
               status = 'ATIVA'
               AND origem_ultima_desativacao IS NOT NULL
           )
    ) OR NOT EXISTS (
        SELECT 1
        FROM pg_class
        WHERE oid = 'public.historico_status_osc'::regclass
          AND relrowsecurity = TRUE
    ) THEN
        RAISE EXCEPTION
            'M1 abortada: pós-condições de esquema, dados ou RLS não foram satisfeitas.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_policy
        WHERE polrelid = 'public.historico_status_osc'::regclass
    ) THEN
        RAISE EXCEPTION
            'M1 abortada: foram encontradas políticas RLS não autorizadas em historico_status_osc.';
    END IF;

    IF history_seq IS NULL
       OR NOT EXISTS (
           SELECT 1
           FROM pg_class c
           WHERE c.oid = 'public.historico_status_osc'::regclass
             AND pg_get_userbyid(c.relowner) = 'postgres'
       )
       OR NOT EXISTS (
           SELECT 1
           FROM pg_class c
           WHERE c.oid = history_seq
             AND pg_get_userbyid(c.relowner) = 'postgres'
       )
       OR NOT EXISTS (
           SELECT 1
           FROM pg_depend d
           WHERE d.classid = 'pg_class'::regclass
             AND d.objid = history_seq
             AND d.refclassid = 'pg_class'::regclass
             AND d.refobjid = 'public.historico_status_osc'::regclass
             AND d.refobjsubid = (
                 SELECT a.attnum
                 FROM pg_attribute a
                 WHERE a.attrelid = 'public.historico_status_osc'::regclass
                   AND a.attname = 'id'
                   AND NOT a.attisdropped
             )
             AND d.deptype = 'a'
       ) THEN
        RAISE EXCEPTION
            'M1 abortada: ownership/sequence ownership do histórico deve permanecer com postgres.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname = 'postgres'
          AND rolbypassrls
          AND NOT rolsuper
    ) OR NOT has_table_privilege(
        'postgres',
        'public.historico_status_osc',
        'SELECT'
    ) OR NOT has_table_privilege(
        'postgres',
        'public.historico_status_osc',
        'INSERT'
    ) OR NOT has_table_privilege(
        'postgres',
        'public.historico_status_osc',
        'UPDATE'
    ) OR NOT has_table_privilege(
        'postgres',
        'public.historico_status_osc',
        'DELETE'
    ) OR NOT has_sequence_privilege(
        'postgres',
        history_seq,
        'USAGE'
    ) OR NOT has_sequence_privilege(
        'postgres',
        history_seq,
        'SELECT'
    ) OR NOT has_sequence_privilege(
        'postgres',
        history_seq,
        'UPDATE'
    ) THEN
        RAISE EXCEPTION
            'M1 abortada: a role de backend postgres precisa de BYPASSRLS e privilégios DML/sequence no histórico.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (VALUES ('anon'), ('authenticated')) AS r(role_name)
        WHERE NOT EXISTS (
                  SELECT 1
                  FROM pg_roles p
                  WHERE p.rolname = r.role_name
              )
           OR has_table_privilege(
                  r.role_name,
                  'public.historico_status_osc',
                  'SELECT'
              )
           OR has_table_privilege(
                  r.role_name,
                  'public.historico_status_osc',
                  'INSERT'
              )
           OR has_table_privilege(
                  r.role_name,
                  'public.historico_status_osc',
                  'UPDATE'
              )
           OR has_table_privilege(
                  r.role_name,
                  'public.historico_status_osc',
                  'DELETE'
              )
           OR has_table_privilege(
                  r.role_name,
                  'public.historico_status_osc',
                  'TRUNCATE'
              )
           OR has_table_privilege(
                  r.role_name,
                  'public.historico_status_osc',
                  'REFERENCES'
              )
           OR has_table_privilege(
                  r.role_name,
                  'public.historico_status_osc',
                  'TRIGGER'
              )
           OR has_table_privilege(
                  r.role_name,
                  'public.historico_status_osc',
                  'MAINTAIN'
              )
           OR has_sequence_privilege(
                  r.role_name,
                  history_seq,
                  'USAGE'
              )
           OR has_sequence_privilege(
                  r.role_name,
                  history_seq,
                  'SELECT'
              )
           OR has_sequence_privilege(
                  r.role_name,
                  history_seq,
                  'UPDATE'
              )
    ) THEN
        RAISE EXCEPTION
            'M1 abortada: anon/authenticated ainda têm privilégios efetivos na tabela ou sequence de histórico.';
    END IF;
END
$postflight$;

COMMIT;
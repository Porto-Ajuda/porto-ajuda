-- Migração manual, transacional e de execução única.
-- Adiciona arquivamento, preserva CNPJ e snapshots de atores/ONG.
-- Suporta CNPJ numérico e alfanumérico conforme especificação da Receita Federal.
-- Dependência obrigatória: M1 (20261103_status_osc.sql) plenamente aplicada.
-- Requer janela de manutenção: os locks bloqueiam escrita nas tabelas afetadas.
-- Não executar sem backup, validação dos diagnósticos e autorização explícita.

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

-- ============================================================
-- 1. PREFLIGHT
-- ============================================================

DO $preflight$
DECLARE
    oscs_rel regclass;
    usuarios_rel regclass;
    historico_rel regclass;
    historico_seq regclass;
    creator_att smallint;
    usuarios_id_att smallint;
    unique_cnpj_att smallint;
    fk_count INTEGER;
BEGIN
    oscs_rel := to_regclass('public.oscs');
    usuarios_rel := to_regclass('public.usuarios');
    historico_rel := to_regclass('public.historico_status_osc');

    IF oscs_rel IS NULL OR usuarios_rel IS NULL OR historico_rel IS NULL THEN
        RAISE EXCEPTION
            'M2 abortada: requer public.oscs, public.usuarios e public.historico_status_osc; aplique/revise M1.';
    END IF;

    -- RLS e ausência de políticas.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_class
        WHERE oid = historico_rel
          AND relrowsecurity
    ) OR EXISTS (
        SELECT 1
        FROM pg_policy
        WHERE polrelid = historico_rel
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: RLS/políticas de historico_status_osc divergem da M1.';
    END IF;

    -- Ownership, sequência, dependência e privilégios efetivos do histórico.
    historico_seq := pg_get_serial_sequence(
        'public.historico_status_osc', 'id'
    )::regclass;

    IF historico_seq IS NULL
       OR NOT EXISTS (
           SELECT 1
           FROM pg_class c
           WHERE c.oid = historico_rel
             AND pg_get_userbyid(c.relowner) = 'postgres'
       )
       OR NOT EXISTS (
           SELECT 1
           FROM pg_class c
           WHERE c.oid = historico_seq
             AND pg_get_userbyid(c.relowner) = 'postgres'
       )
       OR NOT EXISTS (
           SELECT 1
           FROM pg_depend d
           WHERE d.classid = 'pg_class'::regclass
             AND d.objid = historico_seq
             AND d.refclassid = 'pg_class'::regclass
             AND d.refobjid = historico_rel
             AND d.refobjsubid = (
                 SELECT a.attnum
                 FROM pg_attribute a
                 WHERE a.attrelid = historico_rel
                   AND a.attname = 'id'
                   AND NOT a.attisdropped
             )
             AND d.deptype = 'a'
       )
       OR NOT EXISTS (
           SELECT 1
           FROM pg_roles
           WHERE rolname = 'postgres'
             AND rolbypassrls
             AND NOT rolsuper
       )
       OR NOT has_table_privilege('postgres', historico_rel, 'SELECT')
       OR NOT has_table_privilege('postgres', historico_rel, 'INSERT')
       OR NOT has_table_privilege('postgres', historico_rel, 'UPDATE')
       OR NOT has_table_privilege('postgres', historico_rel, 'DELETE')
       OR NOT has_sequence_privilege('postgres', historico_seq, 'USAGE')
       OR NOT has_sequence_privilege('postgres', historico_seq, 'SELECT')
       OR NOT has_sequence_privilege('postgres', historico_seq, 'UPDATE')
       OR EXISTS (
           SELECT 1
           FROM (VALUES ('anon'), ('authenticated')) AS r(role_name)
           WHERE NOT EXISTS (
               SELECT 1
               FROM pg_roles p
               WHERE p.rolname = r.role_name
           )
           OR has_table_privilege(r.role_name, historico_rel, 'SELECT')
           OR has_table_privilege(r.role_name, historico_rel, 'INSERT')
           OR has_table_privilege(r.role_name, historico_rel, 'UPDATE')
           OR has_table_privilege(r.role_name, historico_rel, 'DELETE')
           OR has_table_privilege(r.role_name, historico_rel, 'TRUNCATE')
           OR has_table_privilege(r.role_name, historico_rel, 'REFERENCES')
           OR has_table_privilege(r.role_name, historico_rel, 'TRIGGER')
           OR has_table_privilege(r.role_name, historico_rel, 'MAINTAIN')
           OR has_sequence_privilege(r.role_name, historico_seq, 'USAGE')
           OR has_sequence_privilege(r.role_name, historico_seq, 'SELECT')
           OR has_sequence_privilege(r.role_name, historico_seq, 'UPDATE')
       ) THEN
        RAISE EXCEPTION
            'M2 abortada: ownership, privilégios efetivos ou proteção RLS do histórico divergem de M1.';
    END IF;

    -- Estrutura principal de M1.
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'oscs'
          AND column_name = 'status'
          AND data_type = 'character varying'
          AND character_maximum_length = 20
          AND is_nullable = 'NO'
          AND column_default LIKE '%INATIVA%'
    ) OR EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'oscs'
          AND column_name = 'ativo'
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: oscs.status/default ou remoção de oscs.ativo não corresponde à M1 completa.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = oscs_rel
          AND c.conname = 'ck_oscs_status'
          AND c.contype = 'c'
          AND c.convalidated
          AND pg_get_constraintdef(c.oid) LIKE '%ATIVA%'
          AND pg_get_constraintdef(c.oid) LIKE '%INATIVA%'
    ) OR NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = oscs_rel
          AND c.conname = 'ck_oscs_origem_ultima_desativacao'
          AND c.contype = 'c'
          AND c.convalidated
          AND pg_get_constraintdef(c.oid) LIKE '%ONG%'
          AND pg_get_constraintdef(c.oid) LIKE '%PORTO_AJUDA%'
          AND pg_get_constraintdef(c.oid) LIKE '%INDETERMINADA%'
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: CHECKs de status/origem da M1 ausentes ou não validados.';
    END IF;

    -- Colunas-base do histórico.
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'historico_status_osc'
          AND column_name = 'id'
          AND data_type = 'bigint'
          AND is_nullable = 'NO'
    ) OR NOT EXISTS (
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
          AND table_name = 'oscs'
          AND column_name = 'id_usuario_desativou'
          AND data_type = 'integer'
    ) OR NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'historico_status_osc'
          AND column_name = 'id_osc'
          AND data_type = 'integer'
          AND is_nullable = 'YES'
    ) OR NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'historico_status_osc'
          AND column_name = 'id_osc_snapshot'
          AND data_type = 'integer'
          AND is_nullable = 'NO'
    ) OR NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'historico_status_osc'
          AND column_name = 'id_usuario'
          AND data_type = 'integer'
          AND is_nullable = 'YES'
    ) OR NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'historico_status_osc'
          AND column_name = 'id_usuario_desativou_anterior'
          AND data_type = 'integer'
          AND is_nullable = 'YES'
    ) OR NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'historico_status_osc'
          AND column_name = 'data_criacao'
          AND data_type = 'timestamp without time zone'
          AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: colunas-base do histórico divergem da M1/JPA.';
    END IF;

    -- CHECKs de M1 no histórico.
    IF (
        SELECT COUNT(*)
        FROM pg_constraint c
        WHERE c.conrelid = historico_rel
          AND c.contype = 'c'
          AND c.convalidated
          AND c.conname = ANY(ARRAY[
              'ck_historico_status_osc_id_snapshot',
              'ck_historico_status_osc_status_anterior',
              'ck_historico_status_osc_status_novo',
              'ck_historico_status_osc_origem',
              'ck_historico_status_osc_origem_anterior'
          ])
    ) <> 5 THEN
        RAISE EXCEPTION
            'M2 abortada: M1 não criou os cinco CHECKs validados esperados no histórico.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = historico_rel
          AND c.conname = 'ck_historico_status_osc_id_snapshot'
          AND c.contype = 'c'
          AND c.convalidated
          AND pg_get_constraintdef(c.oid) LIKE '%id_osc_snapshot%'
    ) OR NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = historico_rel
          AND c.conname = 'ck_historico_status_osc_status_anterior'
          AND c.contype = 'c'
          AND c.convalidated
          AND pg_get_constraintdef(c.oid) LIKE '%ATIVA%'
          AND pg_get_constraintdef(c.oid) LIKE '%INATIVA%'
    ) OR NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = historico_rel
          AND c.conname = 'ck_historico_status_osc_status_novo'
          AND c.contype = 'c'
          AND c.convalidated
          AND pg_get_constraintdef(c.oid) LIKE '%ATIVA%'
          AND pg_get_constraintdef(c.oid) LIKE '%INATIVA%'
    ) OR NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = historico_rel
          AND c.conname = 'ck_historico_status_osc_origem'
          AND c.contype = 'c'
          AND c.convalidated
          AND pg_get_constraintdef(c.oid) LIKE '%ONG%'
          AND pg_get_constraintdef(c.oid) LIKE '%PORTO_AJUDA%'
          AND pg_get_constraintdef(c.oid) LIKE '%INDETERMINADA%'
    ) OR NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = historico_rel
          AND c.conname = 'ck_historico_status_osc_origem_anterior'
          AND c.contype = 'c'
          AND c.convalidated
          AND pg_get_constraintdef(c.oid) LIKE '%ONG%'
          AND pg_get_constraintdef(c.oid) LIKE '%PORTO_AJUDA%'
          AND pg_get_constraintdef(c.oid) LIKE '%INDETERMINADA%'
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: definições dos CHECKs históricos divergem dos enums Java.';
    END IF;

    -- FKs de M1: tabela, colunas, ação e validação exatas.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = oscs_rel
          AND c.conname = 'fk_oscs_usuario_desativou'
          AND c.contype = 'f'
          AND c.confrelid = usuarios_rel
          AND c.confdeltype = 'n'
          AND c.convalidated
          AND c.conkey = ARRAY[(
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = oscs_rel
                AND attname = 'id_usuario_desativou'
                AND NOT attisdropped
          )]::smallint[]
          AND c.confkey = ARRAY[(
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = usuarios_rel
                AND attname = 'id'
                AND NOT attisdropped
          )]::smallint[]
    ) OR NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = historico_rel
          AND c.conname = 'fk_historico_status_osc_osc'
          AND c.contype = 'f'
          AND c.confrelid = oscs_rel
          AND c.confdeltype = 'n'
          AND c.convalidated
          AND c.conkey = ARRAY[(
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = historico_rel
                AND attname = 'id_osc'
                AND NOT attisdropped
          )]::smallint[]
          AND c.confkey = ARRAY[(
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = oscs_rel
                AND attname = 'id'
                AND NOT attisdropped
          )]::smallint[]
    ) OR NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = historico_rel
          AND c.conname = 'fk_historico_status_osc_usuario'
          AND c.contype = 'f'
          AND c.confrelid = usuarios_rel
          AND c.confdeltype = 'n'
          AND c.convalidated
          AND c.conkey = ARRAY[(
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = historico_rel
                AND attname = 'id_usuario'
                AND NOT attisdropped
          )]::smallint[]
          AND c.confkey = ARRAY[(
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = usuarios_rel
                AND attname = 'id'
                AND NOT attisdropped
          )]::smallint[]
    ) OR NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = historico_rel
          AND c.conname = 'fk_historico_status_osc_desativador_anterior'
          AND c.contype = 'f'
          AND c.confrelid = usuarios_rel
          AND c.confdeltype = 'n'
          AND c.convalidated
          AND c.conkey = ARRAY[(
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = historico_rel
                AND attname = 'id_usuario_desativou_anterior'
                AND NOT attisdropped
          )]::smallint[]
          AND c.confkey = ARRAY[(
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = usuarios_rel
                AND attname = 'id'
                AND NOT attisdropped
          )]::smallint[]
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: FKs de M1 ausentes, não validadas ou divergentes.';
    END IF;

    -- Índices criados por M1.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_class i
        JOIN pg_namespace n ON n.oid = i.relnamespace
        JOIN pg_index ix ON ix.indexrelid = i.oid
        WHERE n.nspname = 'public'
          AND i.relname = 'idx_historico_status_osc_osc_data'
          AND ix.indrelid = historico_rel
          AND ix.indisvalid
          AND ix.indisready
          AND ix.indpred IS NULL
          AND ix.indexprs IS NULL
          AND ix.indnkeyatts = 2
          AND ix.indnatts = 2
          AND ix.indkey[0] = (
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = historico_rel
                AND attname = 'id_osc_snapshot'
                AND NOT attisdropped
          )
          AND ix.indkey[1] = (
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = historico_rel
                AND attname = 'data_criacao'
                AND NOT attisdropped
          )
    ) OR NOT EXISTS (
        SELECT 1
        FROM pg_class i
        JOIN pg_namespace n ON n.oid = i.relnamespace
        JOIN pg_index ix ON ix.indexrelid = i.oid
        WHERE n.nspname = 'public'
          AND i.relname = 'idx_oscs_status_id'
          AND ix.indrelid = oscs_rel
          AND ix.indisvalid
          AND ix.indisready
          AND ix.indpred IS NULL
          AND ix.indexprs IS NULL
          AND ix.indnkeyatts = 2
          AND ix.indnatts = 2
          AND ix.indkey[0] = (
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = oscs_rel
                AND attname = 'status'
                AND NOT attisdropped
          )
          AND ix.indkey[1] = (
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = oscs_rel
                AND attname = 'id'
                AND NOT attisdropped
          )
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: índices de status/histórico criados por M1 estão ausentes ou inválidos.';
    END IF;

    -- Identificadores e PKs.
    SELECT attnum INTO creator_att
    FROM pg_attribute
    WHERE attrelid = oscs_rel
      AND attname = 'id_usuario_criador'
      AND NOT attisdropped;

    SELECT attnum INTO usuarios_id_att
    FROM pg_attribute
    WHERE attrelid = usuarios_rel
      AND attname = 'id'
      AND NOT attisdropped;

    SELECT attnum INTO unique_cnpj_att
    FROM pg_attribute
    WHERE attrelid = oscs_rel
      AND attname = 'cnpj'
      AND NOT attisdropped;

    IF creator_att IS NULL
       OR usuarios_id_att IS NULL
       OR unique_cnpj_att IS NULL THEN
        RAISE EXCEPTION
            'M2 abortada: id_usuario_criador, usuarios.id ou oscs.cnpj não existe.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'oscs'
          AND column_name = 'id_usuario_criador'
          AND data_type = 'integer'
          AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: id_usuario_criador precisa ser INTEGER NOT NULL antes da migração.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = usuarios_rel
          AND contype = 'p'
          AND conkey = ARRAY[usuarios_id_att]::smallint[]
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: usuarios.id não é PK simples.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = oscs_rel
          AND contype = 'p'
          AND conkey = ARRAY[(
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = oscs_rel
                AND attname = 'id'
                AND NOT attisdropped
          )]::smallint[]
    ) OR NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = historico_rel
          AND contype = 'p'
          AND conkey = ARRAY[(
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = historico_rel
                AND attname = 'id'
                AND NOT attisdropped
          )]::smallint[]
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: PKs simples de oscs.id e historico_status_osc.id são necessárias.';
    END IF;

    IF pg_get_serial_sequence('public.oscs', 'id') IS NULL
       OR pg_get_serial_sequence('public.usuarios', 'id') IS NULL
       OR pg_get_serial_sequence('public.historico_status_osc', 'id') IS NULL THEN
        RAISE EXCEPTION
            'M2 abortada: sequência identity/serial dos IDs JPA está ausente.';
    END IF;

    -- CNPJ continua VARCHAR(14), agora com suporte ao formato alfanumérico.
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'oscs'
          AND column_name = 'cnpj'
          AND data_type = 'character varying'
          AND character_maximum_length = 14
          AND is_nullable = 'NO'
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: cnpj precisa ser VARCHAR(14) NOT NULL.';
    END IF;

    -- UNIQUE/índice único equivalente, simples, válido e não parcial.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_index ix
        JOIN pg_class idx ON idx.oid = ix.indexrelid
        JOIN pg_am am ON am.oid = idx.relam
        WHERE ix.indrelid = oscs_rel
          AND ix.indisunique
          AND ix.indisvalid
          AND ix.indisready
          AND ix.indpred IS NULL
          AND ix.indexprs IS NULL
          AND ix.indnkeyatts = 1
          AND ix.indnatts = 1
          AND ix.indkey[0] = unique_cnpj_att
          AND am.amname = 'btree'
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: não existe UNIQUE/índice btree único simples e válido em oscs.cnpj.';
    END IF;

    -- Uma única FK simples do criador para usuarios.id.
    SELECT COUNT(*) INTO fk_count
    FROM pg_constraint c
    WHERE c.conrelid = oscs_rel
      AND c.contype = 'f'
      AND creator_att = ANY(c.conkey);

    IF fk_count <> 1 OR NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = oscs_rel
          AND c.contype = 'f'
          AND c.confrelid = usuarios_rel
          AND c.conkey = ARRAY[creator_att]::smallint[]
          AND c.confkey = ARRAY[usuarios_id_att]::smallint[]
          AND c.convalidated
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: FK atual do criador ausente, múltipla ou diferente de id_usuario_criador -> usuarios.id.';
    END IF;

    -- Vestígios de execução anterior/parcial.
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'oscs'
          AND column_name IN (
              'id_usuario_desativou_snapshot',
              'data_arquivamento',
              'id_usuario_arquivou',
              'id_usuario_arquivou_snapshot',
              'motivo_arquivamento',
              'id_usuario_criador_snapshot'
          )
    ) OR EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'historico_status_osc'
          AND column_name IN (
              'tipo_evento',
              'id_usuario_snapshot',
              'id_usuario_desativou_anterior_snapshot',
              'data_arquivamento_anterior',
              'id_usuario_arquivou_anterior',
              'id_usuario_arquivou_anterior_snapshot',
              'motivo_arquivamento_anterior'
          )
    ) OR EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid IN (oscs_rel, historico_rel)
          AND conname IN (
              'fk_oscs_usuario_criador',
              'fk_oscs_usuario_arquivou',
              'ck_oscs_cnpj_canonico',
              'ck_historico_status_osc_tipo_evento',
              'fk_historico_status_osc_arquivador_anterior'
          )
    ) OR EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relname IN (
              'idx_oscs_status_arquivamento',
              'uq_oscs_cnpj_normalizado'
          )
    ) OR to_regprocedure('public.cnpj_valido_alfanumerico(text)') IS NOT NULL THEN
        RAISE EXCEPTION
            'M2 abortada: há sinais de aplicação parcial/anterior; inspecione manualmente.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.oscs
        WHERE status NOT IN ('ATIVA', 'INATIVA')
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: oscs.status contém valor fora do enum Java.';
    END IF;
END
$preflight$;

-- ============================================================
-- 2. LOCKS
-- ============================================================

LOCK TABLE public.oscs IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE public.historico_status_osc IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE public.usuarios IN SHARE ROW EXCLUSIVE MODE;

-- ============================================================
-- 3. RECHECK APÓS LOCKS
-- ============================================================

DO $recheck$
DECLARE
    oscs_rel regclass := to_regclass('public.oscs');
    usuarios_rel regclass := to_regclass('public.usuarios');
    historico_rel regclass := to_regclass('public.historico_status_osc');
    creator_att smallint;
    usuarios_id_att smallint;
    cnpj_att smallint;
    fk_count INTEGER;
BEGIN
    IF oscs_rel IS NULL
       OR usuarios_rel IS NULL
       OR historico_rel IS NULL THEN
        RAISE EXCEPTION
            'M2 abortada: tabela pré-requisito desapareceu após o preflight.';
    END IF;

    IF to_regprocedure('public.cnpj_valido_alfanumerico(text)') IS NOT NULL THEN
        RAISE EXCEPTION
            'M2 abortada: a função de validação de CNPJ já existe antes da migração.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_class
        WHERE oid = historico_rel
          AND relrowsecurity
    ) OR EXISTS (
        SELECT 1
        FROM pg_policy
        WHERE polrelid = historico_rel
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: RLS/políticas de historico_status_osc divergiram após M1.';
    END IF;

    SELECT attnum INTO creator_att
    FROM pg_attribute
    WHERE attrelid = oscs_rel
      AND attname = 'id_usuario_criador'
      AND NOT attisdropped;

    SELECT attnum INTO usuarios_id_att
    FROM pg_attribute
    WHERE attrelid = usuarios_rel
      AND attname = 'id'
      AND NOT attisdropped;

    SELECT attnum INTO cnpj_att
    FROM pg_attribute
    WHERE attrelid = oscs_rel
      AND attname = 'cnpj'
      AND NOT attisdropped;

    SELECT COUNT(*) INTO fk_count
    FROM pg_constraint c
    WHERE c.conrelid = oscs_rel
      AND c.contype = 'f'
      AND creator_att = ANY(c.conkey);

    IF creator_att IS NULL
       OR usuarios_id_att IS NULL
       OR cnpj_att IS NULL
       OR fk_count <> 1
       OR NOT EXISTS (
            SELECT 1
            FROM pg_constraint c
            WHERE c.conrelid = oscs_rel
              AND c.contype = 'f'
              AND c.confrelid = usuarios_rel
              AND c.conkey = ARRAY[creator_att]::smallint[]
              AND c.confkey = ARRAY[usuarios_id_att]::smallint[]
              AND c.convalidated
       )
       OR NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'oscs'
              AND column_name = 'status'
              AND data_type = 'character varying'
              AND character_maximum_length = 20
              AND is_nullable = 'NO'
       )
       OR EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'oscs'
              AND column_name IN (
                  'data_arquivamento',
                  'id_usuario_arquivou',
                  'motivo_arquivamento'
              )
       ) THEN
        RAISE EXCEPTION
            'M2 abortada: esquema ou FK mudou desde o preflight.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_index ix
        JOIN pg_class idx ON idx.oid = ix.indexrelid
        JOIN pg_am am ON am.oid = idx.relam
        WHERE ix.indrelid = oscs_rel
          AND ix.indisunique
          AND ix.indisvalid
          AND ix.indisready
          AND ix.indpred IS NULL
          AND ix.indexprs IS NULL
          AND ix.indnkeyatts = 1
          AND ix.indnatts = 1
          AND ix.indkey[0] = cnpj_att
          AND am.amname = 'btree'
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: a proteção UNIQUE simples de cnpj mudou após o preflight.';
    END IF;

    -- O valor já deve estar canônico. Não normalizar nem reescrever registros.
    IF EXISTS (
        SELECT 1
        FROM public.oscs
        WHERE (cnpj COLLATE "C") !~ '^[A-Z0-9]{12}[0-9]{2}$'
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: existe CNPJ fora do formato canônico alfanumérico.';
    END IF;

    IF EXISTS (
        SELECT cnpj
        FROM public.oscs
        GROUP BY cnpj
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: CNPJ duplicado; resolver manualmente.';
    END IF;
END
$recheck$;

-- ============================================================
-- 4. VALIDAÇÃO DOS DÍGITOS VERIFICADORES
-- ============================================================

DO $cnpj_preflight$
BEGIN
    IF EXISTS (
        WITH calculados AS (
            SELECT
                o.id,
                o.cnpj,

                (
                    SELECT SUM(
                        (ASCII(SUBSTRING(o.cnpj FROM w.pos::INTEGER FOR 1)) - 48)
                        * w.peso
                    )
                    FROM UNNEST(
                        ARRAY[5,4,3,2,9,8,7,6,5,4,3,2]
                    ) WITH ORDINALITY AS w(peso, pos)
                ) AS soma1,

                (
                    SELECT SUM(
                        (ASCII(SUBSTRING(o.cnpj FROM w.pos::INTEGER FOR 1)) - 48)
                        * w.peso
                    )
                    FROM UNNEST(
                        ARRAY[6,5,4,3,2,9,8,7,6,5,4,3,2]
                    ) WITH ORDINALITY AS w(peso, pos)
                ) AS soma2

            FROM public.oscs o
            WHERE (o.cnpj COLLATE "C") ~ '^[A-Z0-9]{12}[0-9]{2}$'
        )
        SELECT 1
        FROM calculados
        WHERE
            SUBSTRING(cnpj FROM 13 FOR 1)::integer <>
                CASE
                    WHEN soma1 % 11 < 2 THEN 0
                    ELSE 11 - (soma1 % 11)
                END
            OR
            SUBSTRING(cnpj FROM 14 FOR 1)::integer <>
                CASE
                    WHEN soma2 % 11 < 2 THEN 0
                    ELSE 11 - (soma2 % 11)
                END
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: existe CNPJ com dígitos verificadores inválidos; corrigir manualmente.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.oscs
        WHERE (cnpj COLLATE "C") !~ '^[A-Z0-9]{12}[0-9]{2}$'
           OR (
               SUBSTRING(cnpj FROM 1 FOR 1) ~ '^[0-9]$'
               AND cnpj = REPEAT(SUBSTRING(cnpj FROM 1 FOR 1), 14)
           )
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: CNPJ fora do formato canônico; corrigir manualmente.';
    END IF;

    IF EXISTS (
        SELECT cnpj
        FROM public.oscs
        GROUP BY cnpj
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: duplicidade de CNPJ; resolver manualmente.';
    END IF;
END
$cnpj_preflight$;

-- Validador imutável conforme a especificação RFB/SERPRO:
-- valor ASCII - 48, módulo 11 e pesos 2..9 da direita para a esquerda.
-- Os CNPJs numéricos legados mantêm o mesmo resultado do algoritmo anterior.
CREATE FUNCTION public.cnpj_valido_alfanumerico(p_cnpj TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
IMMUTABLE
STRICT
PARALLEL SAFE
AS $cnpj_validator$
DECLARE
    pesos_dv1 INTEGER[] := ARRAY[5,4,3,2,9,8,7,6,5,4,3,2];
    pesos_dv2 INTEGER[] := ARRAY[6,5,4,3,2,9,8,7,6,5,4,3,2];
    soma_dv1 INTEGER := 0;
    soma_dv2 INTEGER := 0;
    resto INTEGER;
    digito1 INTEGER;
    digito2 INTEGER;
    posicao INTEGER;
BEGIN
    IF (p_cnpj COLLATE "C") !~ '^[A-Z0-9]{12}[0-9]{2}$' THEN
        RETURN FALSE;
    END IF;

    -- Preserva a rejeição antiga de CNPJs compostos por um único algarismo.
    IF SUBSTRING(p_cnpj FROM 1 FOR 1) ~ '^[0-9]$'
       AND p_cnpj = REPEAT(SUBSTRING(p_cnpj FROM 1 FOR 1), 14) THEN
        RETURN FALSE;
    END IF;

    FOR posicao IN 1..12 LOOP
        soma_dv1 := soma_dv1
            + (ASCII(SUBSTRING(p_cnpj FROM posicao FOR 1)) - 48)
              * pesos_dv1[posicao];
    END LOOP;

    resto := soma_dv1 % 11;
    digito1 := CASE WHEN resto < 2 THEN 0 ELSE 11 - resto END;

    IF SUBSTRING(p_cnpj FROM 13 FOR 1)::INTEGER <> digito1 THEN
        RETURN FALSE;
    END IF;

    FOR posicao IN 1..12 LOOP
        soma_dv2 := soma_dv2
            + (ASCII(SUBSTRING(p_cnpj FROM posicao FOR 1)) - 48)
              * pesos_dv2[posicao];
    END LOOP;
    soma_dv2 := soma_dv2 + digito1 * pesos_dv2[13];

    resto := soma_dv2 % 11;
    digito2 := CASE WHEN resto < 2 THEN 0 ELSE 11 - resto END;

    RETURN SUBSTRING(p_cnpj FROM 14 FOR 1)::INTEGER = digito2;
END
$cnpj_validator$;

-- A função é usada pelo CHECK e não precisa de EXECUTE público.
REVOKE ALL PRIVILEGES
ON FUNCTION public.cnpj_valido_alfanumerico(TEXT)
FROM PUBLIC, anon, authenticated;

-- ============================================================
-- 5. ALTERAÇÕES EM OSCS
-- ============================================================

ALTER TABLE public.oscs
    ALTER COLUMN id_usuario_criador DROP NOT NULL,
    ADD COLUMN id_usuario_desativou_snapshot INTEGER,
    ADD COLUMN data_arquivamento TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN id_usuario_arquivou INTEGER,
    ADD COLUMN id_usuario_arquivou_snapshot INTEGER,
    ADD COLUMN motivo_arquivamento TEXT;

UPDATE public.oscs
SET id_usuario_desativou_snapshot = id_usuario_desativou;

DO $replace_creator_fk$
DECLARE
    fk_name TEXT;
BEGIN
    SELECT c.conname INTO fk_name
    FROM pg_constraint c
    WHERE c.conrelid = 'public.oscs'::regclass
      AND c.contype = 'f'
      AND c.confrelid = 'public.usuarios'::regclass
      AND c.conkey = ARRAY[(
          SELECT a.attnum
          FROM pg_attribute a
          WHERE a.attrelid = 'public.oscs'::regclass
            AND a.attname = 'id_usuario_criador'
            AND NOT a.attisdropped
      )]::smallint[]
      AND c.confkey = ARRAY[(
          SELECT a.attnum
          FROM pg_attribute a
          WHERE a.attrelid = 'public.usuarios'::regclass
            AND a.attname = 'id'
            AND NOT a.attisdropped
      )]::smallint[]
      AND c.convalidated;

    IF fk_name IS NULL THEN
        RAISE EXCEPTION
            'M2 abortada: FK esperada do criador não encontrada após preflight.';
    END IF;

    EXECUTE FORMAT(
        'ALTER TABLE public.oscs DROP CONSTRAINT %I',
        fk_name
    );
END
$replace_creator_fk$;

ALTER TABLE public.oscs
    ADD CONSTRAINT fk_oscs_usuario_criador
        FOREIGN KEY (id_usuario_criador)
        REFERENCES public.usuarios(id)
        ON DELETE SET NULL,

    ADD CONSTRAINT fk_oscs_usuario_arquivou
        FOREIGN KEY (id_usuario_arquivou)
        REFERENCES public.usuarios(id)
        ON DELETE SET NULL,

    ADD CONSTRAINT ck_oscs_cnpj_canonico
        CHECK (
            (cnpj COLLATE "C") ~ '^[A-Z0-9]{12}[0-9]{2}$'
            AND public.cnpj_valido_alfanumerico(cnpj)
        );

-- O índice UNIQUE existente continua protegendo todos os CNPJs,
-- inclusive os registros arquivados.
CREATE INDEX idx_oscs_status_arquivamento
    ON public.oscs (status, data_arquivamento);

-- ============================================================
-- 6. ALTERAÇÕES NO HISTÓRICO
-- ============================================================

ALTER TABLE public.historico_status_osc
    ADD COLUMN tipo_evento VARCHAR(30) NOT NULL
        DEFAULT 'TRANSICAO_STATUS',

    ADD COLUMN id_usuario_snapshot INTEGER,

    ADD COLUMN id_usuario_desativou_anterior_snapshot INTEGER,

    ADD COLUMN data_arquivamento_anterior TIMESTAMP WITHOUT TIME ZONE,

    ADD COLUMN id_usuario_arquivou_anterior INTEGER,

    ADD COLUMN id_usuario_arquivou_anterior_snapshot INTEGER,

    ADD COLUMN motivo_arquivamento_anterior TEXT;

UPDATE public.historico_status_osc
SET id_usuario_snapshot = id_usuario,
    id_usuario_desativou_anterior_snapshot =
        id_usuario_desativou_anterior;

ALTER TABLE public.historico_status_osc
    ADD CONSTRAINT ck_historico_status_osc_tipo_evento
        CHECK (
            tipo_evento IN (
                'TRANSICAO_STATUS',
                'ARQUIVAMENTO',
                'RESTAURACAO'
            )
        ),

    ADD CONSTRAINT fk_historico_status_osc_arquivador_anterior
        FOREIGN KEY (id_usuario_arquivou_anterior)
        REFERENCES public.usuarios(id)
        ON DELETE SET NULL;

-- ============================================================
-- 7. POSTFLIGHT
-- ============================================================

DO $postflight$
DECLARE
    creator_att smallint;
    users_att smallint;
    unique_cnpj_att smallint;
    historico_seq regclass;
BEGIN
    historico_seq := pg_get_serial_sequence(
        'public.historico_status_osc', 'id'
    )::regclass;

    SELECT attnum INTO creator_att
    FROM pg_attribute
    WHERE attrelid = 'public.oscs'::regclass
      AND attname = 'id_usuario_criador'
      AND NOT attisdropped;

    SELECT attnum INTO users_att
    FROM pg_attribute
    WHERE attrelid = 'public.usuarios'::regclass
      AND attname = 'id'
      AND NOT attisdropped;

    SELECT attnum INTO unique_cnpj_att
    FROM pg_attribute
    WHERE attrelid = 'public.oscs'::regclass
      AND attname = 'cnpj'
      AND NOT attisdropped;

    -- Colunas principais.
    IF creator_att IS NULL
       OR users_att IS NULL
       OR unique_cnpj_att IS NULL
       OR NOT EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'oscs'
             AND column_name = 'id_usuario_criador'
             AND data_type = 'integer'
             AND is_nullable = 'YES'
       )
       OR EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'oscs'
             AND column_name = 'id_usuario_criador_snapshot'
       )
       OR NOT EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'oscs'
             AND column_name = 'id_usuario_desativou_snapshot'
             AND data_type = 'integer'
             AND is_nullable = 'YES'
       )
       OR NOT EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'oscs'
             AND column_name = 'data_arquivamento'
             AND data_type = 'timestamp without time zone'
             AND is_nullable = 'YES'
       )
       OR NOT EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'oscs'
             AND column_name = 'id_usuario_arquivou_snapshot'
             AND data_type = 'integer'
             AND is_nullable = 'YES'
       ) THEN
        RAISE EXCEPTION
            'M2 abortada: colunas principais de oscs não correspondem ao contrato.';
    END IF;

    -- FKs novas: referência, colunas, ação e validação.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = 'public.oscs'::regclass
          AND c.conname = 'fk_oscs_usuario_criador'
          AND c.contype = 'f'
          AND c.confrelid = 'public.usuarios'::regclass
          AND c.confdeltype = 'n'
          AND c.convalidated
          AND c.conkey = ARRAY[creator_att]::smallint[]
          AND c.confkey = ARRAY[users_att]::smallint[]
    ) OR NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = 'public.oscs'::regclass
          AND c.conname = 'fk_oscs_usuario_arquivou'
          AND c.contype = 'f'
          AND c.confrelid = 'public.usuarios'::regclass
          AND c.confdeltype = 'n'
          AND c.convalidated
          AND c.conkey = ARRAY[(
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = 'public.oscs'::regclass
                AND attname = 'id_usuario_arquivou'
                AND NOT attisdropped
          )]::smallint[]
          AND c.confkey = ARRAY[users_att]::smallint[]
    ) OR NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = 'public.historico_status_osc'::regclass
          AND c.conname = 'fk_historico_status_osc_arquivador_anterior'
          AND c.contype = 'f'
          AND c.confrelid = 'public.usuarios'::regclass
          AND c.confdeltype = 'n'
          AND c.convalidated
          AND c.conkey = ARRAY[(
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = 'public.historico_status_osc'::regclass
                AND attname = 'id_usuario_arquivou_anterior'
                AND NOT attisdropped
          )]::smallint[]
          AND c.confkey = ARRAY[users_att]::smallint[]
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: FKs novas ausentes, não validadas ou com definição divergente.';
    END IF;

    -- CHECKs: existência, definição exata esperada e validação.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = 'public.oscs'::regclass
          AND c.conname = 'ck_oscs_cnpj_canonico'
          AND c.contype = 'c'
          AND c.convalidated
          AND POSITION(
              '^[A-Z0-9]{12}[0-9]{2}$'
              IN pg_get_constraintdef(c.oid)
          ) > 0
          AND POSITION(
              'cnpj_valido_alfanumerico'
              IN pg_get_constraintdef(c.oid)
          ) > 0
    ) OR NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = 'public.historico_status_osc'::regclass
          AND c.conname = 'ck_historico_status_osc_tipo_evento'
          AND c.contype = 'c'
          AND c.convalidated
          AND pg_get_constraintdef(c.oid) LIKE '%TRANSICAO_STATUS%'
          AND pg_get_constraintdef(c.oid) LIKE '%ARQUIVAMENTO%'
          AND pg_get_constraintdef(c.oid) LIKE '%RESTAURACAO%'
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: CHECKs novos ausentes, não validados ou com definição divergente.';
    END IF;

    -- Colunas do histórico.
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'historico_status_osc'
          AND column_name = 'tipo_evento'
          AND data_type = 'character varying'
          AND character_maximum_length = 30
          AND is_nullable = 'NO'
    ) OR NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'historico_status_osc'
          AND column_name = 'id_usuario_snapshot'
          AND data_type = 'integer'
          AND is_nullable = 'YES'
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: colunas do histórico divergem do contrato.';
    END IF;

    IF EXISTS (
        WITH esperadas(tabela, coluna, tipo, nulabilidade) AS (
            VALUES
                ('oscs', 'id_usuario_arquivou', 'integer', 'YES'),
                ('oscs', 'motivo_arquivamento', 'text', 'YES'),
                ('oscs', 'id_usuario_arquivou_snapshot', 'integer', 'YES'),
                ('historico_status_osc', 'id_usuario_desativou_anterior_snapshot', 'integer', 'YES'),
                ('historico_status_osc', 'data_arquivamento_anterior', 'timestamp without time zone', 'YES'),
                ('historico_status_osc', 'id_usuario_arquivou_anterior', 'integer', 'YES'),
                ('historico_status_osc', 'id_usuario_arquivou_anterior_snapshot', 'integer', 'YES'),
                ('historico_status_osc', 'motivo_arquivamento_anterior', 'text', 'YES')
        )
        SELECT 1
        FROM esperadas e
        LEFT JOIN information_schema.columns c
          ON c.table_schema = 'public'
         AND c.table_name = e.tabela
         AND c.column_name = e.coluna
        WHERE c.column_name IS NULL
           OR c.data_type <> e.tipo
           OR c.is_nullable <> e.nulabilidade
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: tipos/nulabilidade das colunas de arquivamento divergem do contrato JPA.';
    END IF;

    -- Snapshots não podem ter FKs.
    IF EXISTS (
        SELECT 1
        FROM pg_constraint c
        CROSS JOIN LATERAL UNNEST(c.conkey) AS k(attnum)
        JOIN pg_attribute a
          ON a.attrelid = c.conrelid
         AND a.attnum = k.attnum
        WHERE c.contype = 'f'
          AND (
              (
                  c.conrelid = 'public.oscs'::regclass
                  AND a.attname IN (
                      'id_usuario_desativou_snapshot',
                      'id_usuario_arquivou_snapshot'
                  )
              )
              OR (
                  c.conrelid = 'public.historico_status_osc'::regclass
                  AND a.attname IN (
                      'id_usuario_snapshot',
                      'id_usuario_desativou_anterior_snapshot',
                      'id_usuario_arquivou_anterior_snapshot'
                  )
              )
          )
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: snapshot de ator recebeu FK; snapshots devem sobreviver à exclusão do usuário.';
    END IF;

    -- UNIQUE original do CNPJ continua válida.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_index ix
        JOIN pg_class idx ON idx.oid = ix.indexrelid
        JOIN pg_am am ON am.oid = idx.relam
        WHERE ix.indrelid = 'public.oscs'::regclass
          AND ix.indisunique
          AND ix.indisvalid
          AND ix.indisready
          AND ix.indpred IS NULL
          AND ix.indexprs IS NULL
          AND ix.indnkeyatts = 1
          AND ix.indnatts = 1
          AND ix.indkey[0] = unique_cnpj_att
          AND am.amname = 'btree'
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: índice UNIQUE simples de CNPJ ausente ou inválido.';
    END IF;

    -- Índice de arquivamento: definição exata.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_class i
        JOIN pg_namespace n ON n.oid = i.relnamespace
        JOIN pg_index ix ON ix.indexrelid = i.oid
        WHERE n.nspname = 'public'
          AND i.relname = 'idx_oscs_status_arquivamento'
          AND ix.indrelid = 'public.oscs'::regclass
          AND ix.indisvalid
          AND ix.indisready
          AND NOT ix.indisunique
          AND ix.indpred IS NULL
          AND ix.indexprs IS NULL
          AND ix.indnkeyatts = 2
          AND ix.indnatts = 2
          AND ix.indkey[0] = (
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = 'public.oscs'::regclass
                AND attname = 'status'
                AND NOT attisdropped
          )
          AND ix.indkey[1] = (
              SELECT attnum
              FROM pg_attribute
              WHERE attrelid = 'public.oscs'::regclass
                AND attname = 'data_arquivamento'
                AND NOT attisdropped
          )
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: índice idx_oscs_status_arquivamento ausente, inválido ou com colunas divergentes.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_proc p
        WHERE p.oid = 'public.cnpj_valido_alfanumerico(text)'::regprocedure
          AND p.provolatile = 'i'
          AND p.proisstrict
          AND p.proparallel = 's'
          AND pg_get_userbyid(p.proowner) = 'postgres'
    ) OR has_function_privilege(
        'anon', 'public.cnpj_valido_alfanumerico(text)', 'EXECUTE'
    ) OR has_function_privilege(
        'authenticated', 'public.cnpj_valido_alfanumerico(text)', 'EXECUTE'
    ) OR NOT has_function_privilege(
        'postgres', 'public.cnpj_valido_alfanumerico(text)', 'EXECUTE'
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: validador CNPJ, imutabilidade, ownership ou privilégios EXECUTE divergem do contrato.';
    END IF;

    -- CNPJ: formato, dígitos verificadores e unicidade.
    IF EXISTS (
        SELECT 1
        FROM public.oscs
        WHERE NOT public.cnpj_valido_alfanumerico(cnpj)
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: CNPJ inválido segundo o formato ou os dígitos verificadores RFB/SERPRO.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.oscs
        GROUP BY cnpj
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: duplicidade de CNPJ após alterações.';
    END IF;

    -- Conferir cópia dos snapshots.
    IF EXISTS (
        SELECT 1
        FROM public.oscs
        WHERE id_usuario_desativou_snapshot
              IS DISTINCT FROM id_usuario_desativou
    ) OR EXISTS (
        SELECT 1
        FROM public.historico_status_osc
        WHERE id_usuario_snapshot
                  IS DISTINCT FROM id_usuario
           OR id_usuario_desativou_anterior_snapshot
                  IS DISTINCT FROM id_usuario_desativou_anterior
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: snapshots copiados não correspondem às referências originais.';
    END IF;

    -- RLS continua habilitado e sem políticas.
    IF NOT EXISTS (
        SELECT 1
        FROM pg_class
        WHERE oid = 'public.historico_status_osc'::regclass
          AND relrowsecurity
    ) OR EXISTS (
        SELECT 1
        FROM pg_policy
        WHERE polrelid = 'public.historico_status_osc'::regclass
    ) THEN
        RAISE EXCEPTION
            'M2 abortada: pós-condição RLS diverge da configuração de M1.';
    END IF;

    -- Ownership, sequência e privilégios efetivos.
    IF historico_seq IS NULL
       OR NOT EXISTS (
           SELECT 1
           FROM pg_class c
           WHERE c.oid = 'public.historico_status_osc'::regclass
             AND pg_get_userbyid(c.relowner) = 'postgres'
       )
       OR NOT EXISTS (
           SELECT 1
           FROM pg_class c
           WHERE c.oid = historico_seq
             AND pg_get_userbyid(c.relowner) = 'postgres'
       )
       OR NOT EXISTS (
           SELECT 1
           FROM pg_depend d
           WHERE d.classid = 'pg_class'::regclass
             AND d.objid = historico_seq
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
       )
       OR NOT EXISTS (
           SELECT 1
           FROM pg_roles
           WHERE rolname = 'postgres'
             AND rolbypassrls
             AND NOT rolsuper
       )
       OR NOT has_table_privilege(
           'postgres', 'public.historico_status_osc', 'SELECT'
       )
       OR NOT has_table_privilege(
           'postgres', 'public.historico_status_osc', 'INSERT'
       )
       OR NOT has_table_privilege(
           'postgres', 'public.historico_status_osc', 'UPDATE'
       )
       OR NOT has_table_privilege(
           'postgres', 'public.historico_status_osc', 'DELETE'
       )
       OR NOT has_sequence_privilege('postgres', historico_seq, 'USAGE')
       OR NOT has_sequence_privilege('postgres', historico_seq, 'SELECT')
       OR NOT has_sequence_privilege('postgres', historico_seq, 'UPDATE')
       OR EXISTS (
           SELECT 1
           FROM (VALUES ('anon'), ('authenticated')) AS r(role_name)
           WHERE NOT EXISTS (
               SELECT 1
               FROM pg_roles p
               WHERE p.rolname = r.role_name
           )
           OR has_table_privilege(
               r.role_name, 'public.historico_status_osc', 'SELECT'
           )
           OR has_table_privilege(
               r.role_name, 'public.historico_status_osc', 'INSERT'
           )
           OR has_table_privilege(
               r.role_name, 'public.historico_status_osc', 'UPDATE'
           )
           OR has_table_privilege(
               r.role_name, 'public.historico_status_osc', 'DELETE'
           )
           OR has_table_privilege(
               r.role_name, 'public.historico_status_osc', 'TRUNCATE'
           )
           OR has_table_privilege(
               r.role_name, 'public.historico_status_osc', 'REFERENCES'
           )
           OR has_table_privilege(
               r.role_name, 'public.historico_status_osc', 'TRIGGER'
           )
           OR has_table_privilege(
               r.role_name, 'public.historico_status_osc', 'MAINTAIN'
           )
           OR has_sequence_privilege(
               r.role_name, historico_seq, 'USAGE'
           )
           OR has_sequence_privilege(
               r.role_name, historico_seq, 'SELECT'
           )
           OR has_sequence_privilege(
               r.role_name, historico_seq, 'UPDATE'
           )
       ) THEN
        RAISE EXCEPTION
            'M2 abortada: privilégios efetivos, ownership ou RLS do histórico não foram preservados.';
    END IF;
END
$postflight$;

COMMIT;

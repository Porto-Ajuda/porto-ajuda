-- Porto Ajuda: ampliacao da taxonomia de causas e preferencias explicitamente salvas.
-- Migracao manual, transacional e de execucao unica.
-- Antes de executar, substitua somente o valor do INSERT abaixo pelo papel JDBC confirmado.
-- Mantenha o sentinela usado no preflight; nao escolha um papel por suposicao.
-- A migracao aborta enquanto o papel nao for confirmado. Nao presume que seja postgres.
-- Este script nao armazena visitas, pesos, impressoes nem historico comportamental.

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '2min';

-- Configure somente depois de confirmar o papel JDBC. Ambos os objetos sao temporarios.
CREATE TEMP TABLE pa_migration_config (
    backend_role NAME NOT NULL
) ON COMMIT DROP;
INSERT INTO pg_temp.pa_migration_config (backend_role)
VALUES ('__VERIFIED_BACKEND_ROLE__');

-- O preflight compara expressoes canonicalizadas pelo mesmo servidor e as colunas
-- dependentes, aceitando as formas equivalentes ANY e IN da baseline revisada.
CREATE TEMP TABLE pa_expected_cause_constraints (
    causa VARCHAR(50),
    causa_principal VARCHAR(50),
    tipo_ong VARCHAR(40),
    CONSTRAINT expected_legacy_causa_any CHECK (
        causa = ANY (ARRAY[
            'EDUCACAO'::VARCHAR,'SAUDE'::VARCHAR,'MEIO_AMBIENTE'::VARCHAR,
            'ANIMAIS'::VARCHAR,'INCLUSAO_SOCIAL'::VARCHAR,'ESPORTE'::VARCHAR,
            'CULTURA'::VARCHAR,'COMBATE_POBREZA'::VARCHAR
        ]::VARCHAR[])
    ),
    CONSTRAINT expected_legacy_causa_nullable_in CHECK (
        causa IS NULL OR causa IN (
            'EDUCACAO','SAUDE','MEIO_AMBIENTE','ANIMAIS',
            'INCLUSAO_SOCIAL','ESPORTE','CULTURA','COMBATE_POBREZA'
        )
    ),
    CONSTRAINT expected_legacy_principal_any CHECK (
        causa_principal = ANY (ARRAY[
            'EDUCACAO'::VARCHAR,'SAUDE'::VARCHAR,'MEIO_AMBIENTE'::VARCHAR,
            'ANIMAIS'::VARCHAR,'INCLUSAO_SOCIAL'::VARCHAR,'ESPORTE'::VARCHAR,
            'CULTURA'::VARCHAR,'COMBATE_POBREZA'::VARCHAR
        ]::VARCHAR[])
    ),
    CONSTRAINT expected_legacy_principal_nullable_in CHECK (
        causa_principal IS NULL OR causa_principal IN (
            'EDUCACAO','SAUDE','MEIO_AMBIENTE','ANIMAIS',
            'INCLUSAO_SOCIAL','ESPORTE','CULTURA','COMBATE_POBREZA'
        )
    ),
    CONSTRAINT expected_principal_distinct CHECK (
        causa IS NULL OR causa_principal IS NULL OR causa <> causa_principal
    ),
    CONSTRAINT expected_extended_causa CHECK (
        causa IS NULL OR causa IN (
            'EDUCACAO','SAUDE','MEIO_AMBIENTE','ANIMAIS',
            'INCLUSAO_SOCIAL','ESPORTE','CULTURA','COMBATE_POBREZA',
            'COMBATE_FOME','CRIANCAS_ADOLESCENTES','IDOSOS','MORADIA',
            'PESSOAS_COM_DEFICIENCIA','APOIO_MULHERES','CULTURA_ESPORTE',
            'AJUDA_HUMANITARIA_EMERGENCIAS','ASSISTENCIA_SOCIAL',
            'DIREITOS_HUMANOS_CIDADANIA'
        )
    ),
    CONSTRAINT expected_extended_principal CHECK (
        causa_principal IS NULL OR causa_principal IN (
            'EDUCACAO','SAUDE','MEIO_AMBIENTE','ANIMAIS',
            'INCLUSAO_SOCIAL','ESPORTE','CULTURA','COMBATE_POBREZA',
            'COMBATE_FOME','CRIANCAS_ADOLESCENTES','IDOSOS','MORADIA',
            'PESSOAS_COM_DEFICIENCIA','APOIO_MULHERES','CULTURA_ESPORTE',
            'AJUDA_HUMANITARIA_EMERGENCIAS','ASSISTENCIA_SOCIAL',
            'DIREITOS_HUMANOS_CIDADANIA'
        )
    ),
    CONSTRAINT expected_preference_type CHECK (
        tipo_ong IN (
            'alimentacao','criancas-adolescentes','idosos','saude','educacao',
            'animais','meio-ambiente','moradia','pessoas-com-deficiencia',
            'mulheres','cultura-esporte','emergencias','assistencia-social',
            'direitos-humanos-cidadania'
        )
    )
) ON COMMIT DROP;

-- Impede alteracoes concorrentes de causas entre o preflight e a troca dos CHECKs.
LOCK TABLE public.osc_causas IN SHARE ROW EXCLUSIVE MODE;

DO $preflight$
DECLARE
    v_backend_role NAME;
    v_expected_table OID;
    v_actual_definition TEXT;
    v_expected_definition TEXT;
    v_constraint_type "char";
    v_constraint_validated BOOLEAN;
    v_constraint_columns SMALLINT[];
    v_column_attnum SMALLINT;
    v_causa_attnum SMALLINT;
    v_principal_attnum SMALLINT;
    v_check RECORD;
BEGIN
    SELECT backend_role
      INTO v_backend_role
      FROM pg_temp.pa_migration_config;

    IF v_backend_role IS NULL OR v_backend_role = '__VERIFIED_BACKEND_ROLE__' THEN
        RAISE EXCEPTION
            'Preflight: substitua o papel marcador pelo papel JDBC confirmado antes da execucao.';
    END IF;
    IF lower(v_backend_role::TEXT) IN ('public','anon','authenticated') THEN
        RAISE EXCEPTION
            'Preflight: o papel do backend nao pode ser PUBLIC, anon ou authenticated.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = v_backend_role) THEN
        RAISE EXCEPTION
            'Preflight: o papel JDBC configurado nao existe neste banco.';
    END IF;
    IF NOT has_schema_privilege(v_backend_role::TEXT, 'public', 'USAGE') THEN
        RAISE EXCEPTION
            'Preflight: o papel JDBC precisa de USAGE no schema public.';
    END IF;

    IF to_regclass('public.oscs') IS NULL
       OR to_regclass('public.osc_causas') IS NULL
       OR to_regclass('public.preferencias_acessibilidade') IS NULL
       OR to_regclass('public.usuarios') IS NULL THEN
        RAISE EXCEPTION
            'Preflight: faltam tabelas legadas requeridas pelo mapeamento da aplicacao.';
    END IF;
    IF to_regclass('public.usuario_causas_preferidas') IS NOT NULL THEN
        RAISE EXCEPTION
            'Preflight: usuario_causas_preferidas ja existe; inspecione o estado antes de reaplicar.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'usuarios'
          AND column_name = 'id' AND data_type = 'integer'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'oscs'
          AND column_name = 'id' AND data_type = 'integer'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'osc_causas'
          AND column_name = 'id_osc' AND data_type = 'integer'
    ) THEN
        RAISE EXCEPTION
            'Preflight: os IDs de usuarios, oscs e osc_causas devem ser INTEGER.';
    END IF;
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint c
          JOIN pg_attribute a
            ON a.attrelid = c.conrelid
           AND a.attname = 'id'
           AND NOT a.attisdropped
         WHERE c.conrelid = 'public.usuarios'::regclass
           AND c.contype IN ('p','u')
           AND c.conkey = ARRAY[a.attnum]::SMALLINT[]
    ) THEN
        RAISE EXCEPTION
            'Preflight: usuarios.id precisa ser PRIMARY KEY ou UNIQUE para a FK.';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'osc_causas'
          AND column_name = 'causa' AND data_type = 'character varying'
          AND character_maximum_length = 50
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'osc_causas'
          AND column_name = 'causa_principal' AND data_type = 'character varying'
          AND character_maximum_length = 50
    ) THEN
        RAISE EXCEPTION
            'Preflight: colunas de causa usam tipos ou comprimentos diferentes do mapeamento JPA.';
    END IF;

    SELECT attnum INTO v_causa_attnum
      FROM pg_attribute
     WHERE attrelid = 'public.osc_causas'::regclass
       AND attname = 'causa'
       AND NOT attisdropped;
    SELECT attnum INTO v_principal_attnum
      FROM pg_attribute
     WHERE attrelid = 'public.osc_causas'::regclass
       AND attname = 'causa_principal'
       AND NOT attisdropped;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_attribute fk_col
          ON fk_col.attrelid = c.conrelid
         AND fk_col.attname = 'id_osc'
         AND NOT fk_col.attisdropped
        JOIN pg_attribute pk_col
          ON pk_col.attrelid = c.confrelid
         AND pk_col.attname = 'id'
         AND NOT pk_col.attisdropped
        WHERE c.conrelid = 'public.osc_causas'::regclass
          AND c.confrelid = 'public.oscs'::regclass
          AND c.contype = 'f'
          AND c.convalidated
          AND c.conkey = ARRAY[fk_col.attnum]::SMALLINT[]
          AND c.confkey = ARRAY[pk_col.attnum]::SMALLINT[]
    ) THEN
        RAISE EXCEPTION
            'Preflight: FK validada osc_causas.id_osc -> oscs.id nao encontrada.';
    END IF;

    SELECT c.oid
      INTO v_expected_table
      FROM pg_class c
     WHERE c.relnamespace = pg_my_temp_schema()
       AND c.relname = 'pa_expected_cause_constraints';
    IF v_expected_table IS NULL THEN
        RAISE EXCEPTION 'Preflight: tabela temporaria de verificacao indisponivel.';
    END IF;

    FOR v_check IN
        SELECT *
        FROM (VALUES
            ('osc_causas_causa_check', 'causa', 'expected_legacy_causa_any', 'expected_legacy_causa_nullable_in'),
            ('osc_causas_causa_principal_check', 'causa_principal', 'expected_legacy_principal_any', 'expected_legacy_principal_nullable_in'),
            ('ck_osc_causas_sem_repetir_principal', NULL, 'expected_principal_distinct', NULL)
        ) AS checks(actual_name, column_name, expected_any, expected_alternative)
    LOOP
        v_column_attnum := CASE v_check.column_name
            WHEN 'causa' THEN v_causa_attnum
            WHEN 'causa_principal' THEN v_principal_attnum
            ELSE NULL
        END;

        SELECT c.contype, c.convalidated, c.conkey,
               pg_get_expr(c.conbin, c.conrelid)
          INTO v_constraint_type, v_constraint_validated,
               v_constraint_columns, v_actual_definition
          FROM pg_constraint c
         WHERE c.conrelid = 'public.osc_causas'::regclass
           AND c.conname = v_check.actual_name;
        IF v_constraint_type IS DISTINCT FROM 'c'::"char"
           OR v_constraint_validated IS DISTINCT FROM TRUE THEN
            RAISE EXCEPTION
                'Preflight: CHECK ausente, nao validada ou de tipo inesperado: %.',
                v_check.actual_name;
        END IF;
        IF v_check.column_name IS NOT NULL
           AND v_constraint_columns IS DISTINCT FROM ARRAY[v_column_attnum]::SMALLINT[] THEN
            RAISE EXCEPTION
                'Preflight: CHECK % depende de colunas diferentes da baseline revisada.',
                v_check.actual_name;
        END IF;
        IF v_check.column_name IS NULL
           AND (
               array_length(v_constraint_columns, 1) IS DISTINCT FROM 2
               OR NOT (v_causa_attnum = ANY (v_constraint_columns))
               OR NOT (v_principal_attnum = ANY (v_constraint_columns))
           ) THEN
            RAISE EXCEPTION
                'Preflight: CHECK de causa principal depende de colunas inesperadas.';
        END IF;

        SELECT pg_get_expr(c.conbin, c.conrelid)
          INTO v_expected_definition
          FROM pg_constraint c
         WHERE c.conrelid = v_expected_table
           AND c.conname = v_check.expected_any;
        IF regexp_replace(v_actual_definition, '[[:space:]]+', '', 'g')
             IS DISTINCT FROM regexp_replace(v_expected_definition, '[[:space:]]+', '', 'g')
           AND v_check.expected_alternative IS NOT NULL THEN
            SELECT pg_get_expr(c.conbin, c.conrelid)
              INTO v_expected_definition
              FROM pg_constraint c
             WHERE c.conrelid = v_expected_table
               AND c.conname = v_check.expected_alternative;
        END IF;
        IF regexp_replace(v_actual_definition, '[[:space:]]+', '', 'g')
             IS DISTINCT FROM regexp_replace(v_expected_definition, '[[:space:]]+', '', 'g') THEN
            RAISE EXCEPTION
                'Preflight: expressao ou valores permitidos inesperados em %. Nenhuma constraint sera substituida.',
                v_check.actual_name;
        END IF;
    END LOOP;

    IF EXISTS (
        SELECT 1
          FROM pg_constraint c
         WHERE c.conrelid = 'public.osc_causas'::regclass
           AND c.contype = 'c'
           AND c.conname NOT IN (
               'osc_causas_causa_check',
               'osc_causas_causa_principal_check',
               'ck_osc_causas_sem_repetir_principal'
           )
           AND (
               c.conkey && ARRAY[v_causa_attnum]::SMALLINT[]
               OR c.conkey && ARRAY[v_principal_attnum]::SMALLINT[]
           )
    ) THEN
        RAISE EXCEPTION
            'Preflight: CHECK adicional depende de causa; revise sua definicao antes da migracao.';
    END IF;

    -- Valores legados validos sao preservados; qualquer valor desconhecido aborta a transacao.
    IF EXISTS (
        SELECT 1
          FROM public.osc_causas
         WHERE (causa IS NOT NULL AND NOT (causa::TEXT = ANY (ARRAY[
                   'EDUCACAO','SAUDE','MEIO_AMBIENTE','ANIMAIS',
                   'INCLUSAO_SOCIAL','ESPORTE','CULTURA','COMBATE_POBREZA',
                   'COMBATE_FOME','CRIANCAS_ADOLESCENTES','IDOSOS','MORADIA',
                   'PESSOAS_COM_DEFICIENCIA','APOIO_MULHERES','CULTURA_ESPORTE',
                   'AJUDA_HUMANITARIA_EMERGENCIAS','ASSISTENCIA_SOCIAL',
                   'DIREITOS_HUMANOS_CIDADANIA'
               ]::TEXT[])))
            OR (causa_principal IS NOT NULL AND NOT (causa_principal::TEXT = ANY (ARRAY[
                   'EDUCACAO','SAUDE','MEIO_AMBIENTE','ANIMAIS',
                   'INCLUSAO_SOCIAL','ESPORTE','CULTURA','COMBATE_POBREZA',
                   'COMBATE_FOME','CRIANCAS_ADOLESCENTES','IDOSOS','MORADIA',
                   'PESSOAS_COM_DEFICIENCIA','APOIO_MULHERES','CULTURA_ESPORTE',
                   'AJUDA_HUMANITARIA_EMERGENCIAS','ASSISTENCIA_SOCIAL',
                   'DIREITOS_HUMANOS_CIDADANIA'
               ]::TEXT[])))
    ) THEN
        RAISE EXCEPTION
            'Preflight: osc_causas contem valores fora da taxonomia Java; nenhuma alteracao aplicada.';
    END IF;
END
$preflight$;

-- Somente as duas CHECKs da baseline Supabase validada sao substituidas.
-- A constraint de nao repetir a principal permanece intacta.
ALTER TABLE public.osc_causas
    DROP CONSTRAINT osc_causas_causa_check,
    DROP CONSTRAINT osc_causas_causa_principal_check;

ALTER TABLE public.osc_causas
    ADD CONSTRAINT ck_osc_causas_causa CHECK (
        causa IS NULL OR causa IN (
            'EDUCACAO','SAUDE','MEIO_AMBIENTE','ANIMAIS',
            'INCLUSAO_SOCIAL','ESPORTE','CULTURA','COMBATE_POBREZA',
            'COMBATE_FOME','CRIANCAS_ADOLESCENTES','IDOSOS','MORADIA',
            'PESSOAS_COM_DEFICIENCIA','APOIO_MULHERES','CULTURA_ESPORTE',
            'AJUDA_HUMANITARIA_EMERGENCIAS','ASSISTENCIA_SOCIAL',
            'DIREITOS_HUMANOS_CIDADANIA'
        )
    ),
    ADD CONSTRAINT ck_osc_causas_principal CHECK (
        causa_principal IS NULL OR causa_principal IN (
            'EDUCACAO','SAUDE','MEIO_AMBIENTE','ANIMAIS',
            'INCLUSAO_SOCIAL','ESPORTE','CULTURA','COMBATE_POBREZA',
            'COMBATE_FOME','CRIANCAS_ADOLESCENTES','IDOSOS','MORADIA',
            'PESSOAS_COM_DEFICIENCIA','APOIO_MULHERES','CULTURA_ESPORTE',
            'AJUDA_HUMANITARIA_EMERGENCIAS','ASSISTENCIA_SOCIAL',
            'DIREITOS_HUMANOS_CIDADANIA'
        )
    );

CREATE TABLE public.usuario_causas_preferidas (
    id_usuario INTEGER NOT NULL,
    tipo_ong VARCHAR(40) NOT NULL,
    CONSTRAINT pk_usuario_causas_preferidas
        PRIMARY KEY (id_usuario, tipo_ong),
    CONSTRAINT fk_usuario_causas_preferidas_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES public.usuarios(id)
        ON DELETE CASCADE,
    CONSTRAINT ck_usuario_causas_preferidas_tipo CHECK (
        tipo_ong IN (
            'alimentacao','criancas-adolescentes','idosos','saude','educacao',
            'animais','meio-ambiente','moradia','pessoas-com-deficiencia',
            'mulheres','cultura-esporte','emergencias','assistencia-social',
            'direitos-humanos-cidadania'
        )
    )
);

-- A PK composta ja atende consultas por id_usuario; nenhum indice redundante e criado.
ALTER TABLE public.usuario_causas_preferidas ENABLE ROW LEVEL SECURITY;
REVOKE ALL PRIVILEGES ON TABLE public.usuario_causas_preferidas FROM PUBLIC;

DO $backend_acl$
DECLARE
    v_backend_role NAME;
    v_backend_oid OID;
    v_grantee RECORD;
BEGIN
    SELECT backend_role
      INTO v_backend_role
      FROM pg_temp.pa_migration_config;
    SELECT oid
      INTO v_backend_oid
      FROM pg_roles
     WHERE rolname = v_backend_role;

    -- Remove ACLs que possam vir de default privileges para esta tabela nova.
    FOR v_grantee IN
        SELECT DISTINCT r.rolname
          FROM pg_class c
          CROSS JOIN LATERAL aclexplode(
              COALESCE(c.relacl, acldefault('r', c.relowner))
          ) acl
          JOIN pg_roles r ON r.oid = acl.grantee
         WHERE c.oid = 'public.usuario_causas_preferidas'::regclass
           AND acl.grantee <> c.relowner
           AND acl.grantee <> v_backend_oid
    LOOP
        EXECUTE format(
            'REVOKE ALL PRIVILEGES ON TABLE public.usuario_causas_preferidas FROM %I',
            v_grantee.rolname
        );
    END LOOP;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE
            'REVOKE ALL PRIVILEGES ON TABLE public.usuario_causas_preferidas FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE
            'REVOKE ALL PRIVILEGES ON TABLE public.usuario_causas_preferidas FROM authenticated';
    END IF;

    EXECUTE format(
        'CREATE POLICY usuario_causas_preferidas_backend ON public.usuario_causas_preferidas TO %I USING (true) WITH CHECK (true)',
        v_backend_role
    );
    EXECUTE format(
        'GRANT SELECT, INSERT, DELETE ON TABLE public.usuario_causas_preferidas TO %I',
        v_backend_role
    );
END
$backend_acl$;

DO $postflight$
DECLARE
    v_backend_role NAME;
    v_backend_oid OID;
    v_expected_table OID;
    v_actual_definition TEXT;
    v_expected_definition TEXT;
    v_constraint_columns SMALLINT[];
    v_column_attnum SMALLINT;
    v_causa_attnum SMALLINT;
    v_principal_attnum SMALLINT;
    v_pk_columns TEXT[];
    v_check RECORD;
BEGIN
    SELECT backend_role
      INTO v_backend_role
      FROM pg_temp.pa_migration_config;
    SELECT oid INTO v_backend_oid FROM pg_roles WHERE rolname = v_backend_role;
    SELECT c.oid
      INTO v_expected_table
      FROM pg_class c
     WHERE c.relnamespace = pg_my_temp_schema()
       AND c.relname = 'pa_expected_cause_constraints';
    SELECT attnum INTO v_causa_attnum
      FROM pg_attribute
     WHERE attrelid = 'public.osc_causas'::regclass
       AND attname = 'causa'
       AND NOT attisdropped;
    SELECT attnum INTO v_principal_attnum
      FROM pg_attribute
     WHERE attrelid = 'public.osc_causas'::regclass
       AND attname = 'causa_principal'
       AND NOT attisdropped;

    FOR v_check IN
        SELECT *
        FROM (VALUES
            ('ck_osc_causas_causa', 'causa', 'expected_extended_causa'),
            ('ck_osc_causas_principal', 'causa_principal', 'expected_extended_principal'),
            ('ck_osc_causas_sem_repetir_principal', NULL, 'expected_principal_distinct')
        ) AS checks(actual_name, column_name, expected_name)
    LOOP
        SELECT pg_get_expr(c.conbin, c.conrelid), c.conkey
          INTO v_actual_definition, v_constraint_columns
          FROM pg_constraint c
         WHERE c.conrelid = 'public.osc_causas'::regclass
           AND c.conname = v_check.actual_name
           AND c.contype = 'c'
           AND c.convalidated;
        IF v_check.column_name IS NOT NULL THEN
            SELECT attnum INTO v_column_attnum
              FROM pg_attribute
             WHERE attrelid = 'public.osc_causas'::regclass
               AND attname = v_check.column_name
               AND NOT attisdropped;
            IF v_constraint_columns IS DISTINCT FROM ARRAY[v_column_attnum]::SMALLINT[] THEN
                RAISE EXCEPTION
                    'Postflight: CHECK % depende de colunas inesperadas.',
                    v_check.actual_name;
            END IF;
        ELSIF array_length(v_constraint_columns, 1) IS DISTINCT FROM 2
           OR NOT (v_causa_attnum = ANY (v_constraint_columns))
           OR NOT (v_principal_attnum = ANY (v_constraint_columns)) THEN
            RAISE EXCEPTION
                'Postflight: CHECK de causa principal depende de colunas inesperadas.';
        END IF;

        SELECT pg_get_expr(c.conbin, c.conrelid)
          INTO v_expected_definition
          FROM pg_constraint c
         WHERE c.conrelid = v_expected_table
           AND c.conname = v_check.expected_name;
        IF regexp_replace(v_actual_definition, '[[:space:]]+', '', 'g')
             IS DISTINCT FROM regexp_replace(v_expected_definition, '[[:space:]]+', '', 'g') THEN
            RAISE EXCEPTION
                'Postflight: CHECK ausente ou diferente do esperado: %.',
                v_check.actual_name;
        END IF;
    END LOOP;

    SELECT array_agg(a.attname::TEXT ORDER BY key.ordinality)
      INTO v_pk_columns
      FROM pg_constraint c
      CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS key(attnum, ordinality)
      JOIN pg_attribute a
        ON a.attrelid = c.conrelid
       AND a.attnum = key.attnum
     WHERE c.conrelid = 'public.usuario_causas_preferidas'::regclass
       AND c.contype = 'p';
    IF v_pk_columns IS DISTINCT FROM ARRAY['id_usuario','tipo_ong']::TEXT[] THEN
        RAISE EXCEPTION 'Postflight: chave primaria composta inesperada.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint c
          JOIN pg_attribute fk_col
            ON fk_col.attrelid = c.conrelid
           AND fk_col.attname = 'id_usuario'
           AND NOT fk_col.attisdropped
          JOIN pg_attribute pk_col
            ON pk_col.attrelid = c.confrelid
           AND pk_col.attname = 'id'
           AND NOT pk_col.attisdropped
         WHERE c.conrelid = 'public.usuario_causas_preferidas'::regclass
           AND c.confrelid = 'public.usuarios'::regclass
           AND c.contype = 'f'
           AND c.convalidated
           AND c.confdeltype = 'c'
           AND c.conkey = ARRAY[fk_col.attnum]::SMALLINT[]
           AND c.confkey = ARRAY[pk_col.attnum]::SMALLINT[]
    ) THEN
        RAISE EXCEPTION
            'Postflight: FK id_usuario -> usuarios(id) com ON DELETE CASCADE ausente.';
    END IF;

    SELECT pg_get_constraintdef(c.oid)
      INTO v_actual_definition
      FROM pg_constraint c
     WHERE c.conrelid = 'public.usuario_causas_preferidas'::regclass
       AND c.conname = 'ck_usuario_causas_preferidas_tipo'
       AND c.contype = 'c'
       AND c.convalidated;
    SELECT pg_get_constraintdef(c.oid)
      INTO v_expected_definition
      FROM pg_constraint c
     WHERE c.conrelid = v_expected_table
       AND c.conname = 'expected_preference_type';
    IF v_actual_definition IS DISTINCT FROM v_expected_definition THEN
        RAISE EXCEPTION
            'Postflight: CHECK de tipo_ong ausente ou diferente dos 14 IDs Java.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_class
        WHERE oid = 'public.usuario_causas_preferidas'::regclass
          AND relrowsecurity
    ) THEN
        RAISE EXCEPTION 'Postflight: RLS nao esta habilitada.';
    END IF;
    IF NOT EXISTS (
        SELECT 1
          FROM pg_policies p
         WHERE p.schemaname = 'public'
           AND p.tablename = 'usuario_causas_preferidas'
           AND p.policyname = 'usuario_causas_preferidas_backend'
           AND p.cmd = 'ALL'
           AND v_backend_role = ANY (p.roles)
           AND p.qual IS NOT NULL
           AND p.with_check IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'Postflight: policy RLS do papel backend ausente.';
    END IF;

    IF NOT has_table_privilege(v_backend_role, 'public.usuario_causas_preferidas', 'SELECT')
       OR NOT has_table_privilege(v_backend_role, 'public.usuario_causas_preferidas', 'INSERT')
       OR NOT has_table_privilege(v_backend_role, 'public.usuario_causas_preferidas', 'DELETE') THEN
        RAISE EXCEPTION 'Postflight: faltam privilegios SELECT, INSERT ou DELETE para o backend.';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM pg_class c
          CROSS JOIN LATERAL aclexplode(
              COALESCE(c.relacl, acldefault('r', c.relowner))
          ) acl
         WHERE c.oid = 'public.usuario_causas_preferidas'::regclass
           AND acl.grantee NOT IN (c.relowner, v_backend_oid)
    ) THEN
        RAISE EXCEPTION
            'Postflight: existe ACL direta para papel diferente do owner/backend.';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') AND (
        has_table_privilege('anon', 'public.usuario_causas_preferidas', 'SELECT')
        OR has_table_privilege('anon', 'public.usuario_causas_preferidas', 'INSERT')
        OR has_table_privilege('anon', 'public.usuario_causas_preferidas', 'UPDATE')
        OR has_table_privilege('anon', 'public.usuario_causas_preferidas', 'DELETE')
        OR has_table_privilege('anon', 'public.usuario_causas_preferidas', 'TRUNCATE')
        OR has_table_privilege('anon', 'public.usuario_causas_preferidas', 'REFERENCES')
        OR has_table_privilege('anon', 'public.usuario_causas_preferidas', 'TRIGGER')
    ) THEN
        RAISE EXCEPTION 'Postflight: anon ainda tem privilegios efetivos na tabela.';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') AND (
        has_table_privilege('authenticated', 'public.usuario_causas_preferidas', 'SELECT')
        OR has_table_privilege('authenticated', 'public.usuario_causas_preferidas', 'INSERT')
        OR has_table_privilege('authenticated', 'public.usuario_causas_preferidas', 'UPDATE')
        OR has_table_privilege('authenticated', 'public.usuario_causas_preferidas', 'DELETE')
        OR has_table_privilege('authenticated', 'public.usuario_causas_preferidas', 'TRUNCATE')
        OR has_table_privilege('authenticated', 'public.usuario_causas_preferidas', 'REFERENCES')
        OR has_table_privilege('authenticated', 'public.usuario_causas_preferidas', 'TRIGGER')
    ) THEN
        RAISE EXCEPTION 'Postflight: authenticated ainda tem privilegios efetivos na tabela.';
    END IF;
END
$postflight$;

COMMIT;

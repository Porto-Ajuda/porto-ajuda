/*
 * Diagnostico somente leitura para avaliar pre-condicoes de M3:
 * 20261105_admin_auth_foundation.sql
 *
 * Execute manualmente no SQL Editor do projeto Supabase correto, depois de M1/M2.
 * Este script NAO cria objetos, NAO altera dados/permissoes/RLS e NAO executa M3.
 * Retorna metadados e contagens agregadas; nao exibe linhas ou identificadores pessoais.
 */

BEGIN TRANSACTION READ ONLY;

SELECT '01 - sessao e roles' AS secao,
       current_database() AS banco,
       current_user AS current_user,
       session_user AS session_user,
       COALESCE(r.rolsuper, false) AS current_user_superuser,
       COALESCE(r.rolbypassrls, false) AS current_user_bypassrls,
       CASE
           WHEN current_user = 'postgres'
            AND COALESCE(r.rolbypassrls, false)
            AND NOT COALESCE(r.rolsuper, false)
           THEN 'COMPATIVEL'
           ELSE 'BLOQUEADOR: M3 requer current_user=postgres, BYPASSRLS=true e SUPERUSER=false'
       END AS resultado
FROM pg_roles r
WHERE r.rolname = current_user;

SELECT '02 - roles requeridas' AS secao,
       expected.rolname AS role_requerida,
       (actual.oid IS NOT NULL) AS existe,
       actual.rolcanlogin AS pode_login,
       actual.rolsuper AS superuser,
       actual.rolbypassrls AS bypassrls,
       CASE WHEN actual.oid IS NULL THEN 'BLOQUEADOR: role ausente'
            ELSE 'COMPATIVEL: role existe'
       END AS resultado
FROM (VALUES ('postgres'), ('anon'), ('authenticated')) AS expected(rolname)
LEFT JOIN pg_roles actual ON actual.rolname = expected.rolname
ORDER BY expected.rolname;

SELECT '03 - tabelas-base M1/M2/M3' AS secao,
       expected.relname AS relacao,
       c.relkind AS tipo_relacao,
       pg_get_userbyid(c.relowner) AS proprietario,
       CASE WHEN c.oid IS NULL THEN 'BLOQUEADOR: objeto requerido ausente'
            WHEN c.relkind NOT IN ('r', 'p') THEN 'BLOQUEADOR: objeto nao e tabela'
            ELSE 'PRESENTE'
       END AS resultado
FROM (VALUES ('usuarios'), ('oscs'), ('historico_status_osc')) AS expected(relname)
LEFT JOIN pg_class c
       ON c.relnamespace = 'public'::regnamespace
      AND c.relname = expected.relname
ORDER BY expected.relname;

/* M3 requer usuarios.id INTEGER NOT NULL, PK simples e sequence SERIAL/IDENTITY. */
WITH required AS (
    SELECT to_regclass('public.usuarios') AS users_oid,
           (SELECT attnum FROM pg_attribute
             WHERE attrelid = to_regclass('public.usuarios')
               AND attname = 'id' AND NOT attisdropped) AS users_id_att
)
SELECT '04 - chave primaria/geracao de usuarios.id' AS secao,
       c.data_type,
       c.is_nullable,
       c.is_identity,
       c.identity_generation,
       CASE WHEN to_regclass('public.usuarios') IS NULL THEN NULL
            ELSE pg_get_serial_sequence('public.usuarios', 'id') END AS sequence_associada,
       EXISTS (
           SELECT 1 FROM pg_constraint pk
           WHERE pk.conrelid = required.users_oid
             AND pk.contype = 'p'
             AND pk.conkey = ARRAY[required.users_id_att]::smallint[]
       ) AS pk_simples,
       CASE
           WHEN c.data_type = 'integer' AND c.is_nullable = 'NO'
            AND EXISTS (
                SELECT 1 FROM pg_constraint pk
                WHERE pk.conrelid = required.users_oid
                  AND pk.contype = 'p'
                  AND pk.conkey = ARRAY[required.users_id_att]::smallint[]
            )
            AND CASE WHEN to_regclass('public.usuarios') IS NULL THEN false
                     ELSE pg_get_serial_sequence('public.usuarios', 'id') IS NOT NULL END
           THEN 'COMPATIVEL'
           ELSE 'BLOQUEADOR: tipo, PK ou geracao de usuarios.id diverge do contrato M1/M2/JPA'
       END AS resultado
FROM required
LEFT JOIN information_schema.columns c
       ON c.table_schema = 'public'
      AND c.table_name = 'usuarios'
      AND c.column_name = 'id';

/* Colunas existentes de que dependem as verificacoes e o Hibernate. */
WITH expected(table_name, column_name, data_type, char_length, nullable) AS (
    VALUES
      ('usuarios','id','integer',NULL,'NO'),
      ('oscs','id','integer',NULL,'NO'),
      ('oscs','status','character varying',20,'NO'),
      ('oscs','origem_ultima_desativacao','character varying',30,'YES'),
      ('oscs','data_ultima_desativacao','timestamp without time zone',NULL,'YES'),
      ('oscs','id_usuario_desativou','integer',NULL,'YES'),
      ('oscs','id_usuario_desativou_snapshot','integer',NULL,'YES'),
      ('oscs','motivo_ultima_desativacao','text',NULL,'YES'),
      ('oscs','id_usuario_criador','integer',NULL,'YES'),
      ('oscs','cnpj','character varying',14,'NO'),
      ('oscs','data_arquivamento','timestamp without time zone',NULL,'YES'),
      ('oscs','id_usuario_arquivou','integer',NULL,'YES'),
      ('oscs','id_usuario_arquivou_snapshot','integer',NULL,'YES'),
      ('oscs','motivo_arquivamento','text',NULL,'YES'),
      ('historico_status_osc','id','bigint',NULL,'NO'),
      ('historico_status_osc','id_osc','integer',NULL,'YES'),
      ('historico_status_osc','id_osc_snapshot','integer',NULL,'NO'),
      ('historico_status_osc','status_anterior','character varying',20,'YES'),
      ('historico_status_osc','status_novo','character varying',20,'NO'),
      ('historico_status_osc','origem_acao','character varying',30,'NO'),
      ('historico_status_osc','origem_desativacao_anterior','character varying',30,'YES'),
      ('historico_status_osc','data_desativacao_anterior','timestamp without time zone',NULL,'YES'),
      ('historico_status_osc','id_usuario_desativou_anterior','integer',NULL,'YES'),
      ('historico_status_osc','motivo_desativacao_anterior','text',NULL,'YES'),
      ('historico_status_osc','id_usuario','integer',NULL,'YES'),
      ('historico_status_osc','autoridade_ator','character varying',100,'NO'),
      ('historico_status_osc','data_criacao','timestamp without time zone',NULL,'NO'),
      ('historico_status_osc','motivo','text',NULL,'YES'),
      ('historico_status_osc','tipo_evento','character varying',30,'NO'),
      ('historico_status_osc','id_usuario_snapshot','integer',NULL,'YES'),
      ('historico_status_osc','id_usuario_desativou_anterior_snapshot','integer',NULL,'YES'),
      ('historico_status_osc','data_arquivamento_anterior','timestamp without time zone',NULL,'YES'),
      ('historico_status_osc','id_usuario_arquivou_anterior','integer',NULL,'YES'),
      ('historico_status_osc','id_usuario_arquivou_anterior_snapshot','integer',NULL,'YES'),
      ('historico_status_osc','motivo_arquivamento_anterior','text',NULL,'YES')
)
SELECT '05 - colunas post-M1/M2' AS secao,
       e.table_name || '.' || e.column_name AS coluna,
       e.data_type AS tipo_esperado,
       e.char_length AS tamanho_esperado,
       e.nullable AS nulabilidade_esperada,
       c.data_type AS tipo_encontrado,
       c.character_maximum_length AS tamanho_encontrado,
       c.is_nullable AS nulabilidade_encontrada,
       CASE WHEN c.column_name IS NULL THEN 'BLOQUEADOR: coluna ausente'
            WHEN c.data_type <> e.data_type THEN 'BLOQUEADOR: tipo divergente'
            WHEN e.char_length IS NOT NULL
             AND c.character_maximum_length IS DISTINCT FROM e.char_length
              THEN 'BLOQUEADOR: tamanho divergente'
            WHEN c.is_nullable <> e.nullable THEN 'BLOQUEADOR: nulabilidade divergente'
            ELSE 'COMPATIVEL'
       END AS resultado
FROM expected e
LEFT JOIN information_schema.columns c
       ON c.table_schema = 'public'
      AND c.table_name = e.table_name
      AND c.column_name = e.column_name
ORDER BY e.table_name, e.column_name;

/* M3 deve adicionar esta coluna; os demais objetos abaixo devem estar ausentes. */
SELECT '06 - conflitos de objetos M3' AS secao,
       expected.object_name AS objeto_esperado_ausente,
       c.relkind AS tipo_existente,
       CASE WHEN c.oid IS NULL THEN 'COMPATIVEL: nome livre'
            ELSE 'BLOQUEADOR: nome ja ocupado no schema public'
       END AS resultado
FROM (VALUES
    ('admin_auth_challenges'),
    ('admin_auth_deliveries'),
    ('phone_verification_challenges'),
    ('admin_auth_controls'),
    ('admin_security_audit_events'),
    ('idx_admin_auth_challenges_user_state'),
    ('idx_admin_auth_challenges_account_created'),
    ('idx_admin_auth_challenges_expiry'),
    ('idx_admin_auth_deliveries_claim'),
    ('idx_phone_verification_user_state'),
    ('idx_phone_verification_expiry'),
    ('idx_admin_auth_controls_lock'),
    ('idx_admin_security_events_time'),
    ('idx_admin_security_events_account_time')
) AS expected(object_name)
LEFT JOIN pg_class c
       ON c.relnamespace = 'public'::regnamespace
      AND c.relname = expected.object_name
ORDER BY expected.object_name;

SELECT '07 - conflito da coluna telefone_verificado' AS secao,
       EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'usuarios'
             AND column_name = 'telefone_verificado'
       ) AS coluna_ja_existe,
       CASE WHEN EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'usuarios'
             AND column_name = 'telefone_verificado'
       ) THEN 'BLOQUEADOR: M3 tentara adicionar coluna ja existente'
       ELSE 'COMPATIVEL: coluna pode ser adicionada por M3'
       END AS resultado;

/* Verifica PKs/serial das tabelas preexistentes que M1/M2/JPA requerem. */
WITH expected(table_name) AS (VALUES ('oscs'), ('historico_status_osc'))
SELECT '08 - IDs, PKs e sequences M1/M2' AS secao,
       e.table_name,
       c.data_type AS tipo_id,
       c.is_nullable AS id_nullable,
       CASE WHEN to_regclass('public.' || e.table_name) IS NULL THEN NULL
            ELSE pg_get_serial_sequence('public.' || e.table_name, 'id') END AS sequence_associada,
       EXISTS (
           SELECT 1 FROM pg_constraint pk
           WHERE pk.conrelid = to_regclass('public.' || e.table_name)
             AND pk.contype = 'p'
             AND cardinality(pk.conkey) = 1
             AND pk.conkey[1] = (
                 SELECT a.attnum FROM pg_attribute a
                 WHERE a.attrelid = to_regclass('public.' || e.table_name)
                   AND a.attname = 'id' AND NOT a.attisdropped
             )
       ) AS pk_simples,
       CASE WHEN c.data_type IS NULL THEN 'BLOQUEADOR: coluna id ausente'
            WHEN c.is_nullable <> 'NO' THEN 'BLOQUEADOR: id aceita NULL'
            WHEN NOT EXISTS (
                SELECT 1 FROM pg_constraint pk
                WHERE pk.conrelid = to_regclass('public.' || e.table_name)
                  AND pk.contype = 'p'
                  AND cardinality(pk.conkey) = 1
                  AND pk.conkey[1] = (
                      SELECT a.attnum FROM pg_attribute a
                      WHERE a.attrelid = to_regclass('public.' || e.table_name)
                        AND a.attname = 'id' AND NOT a.attisdropped
                  )
            ) THEN 'BLOQUEADOR: PK simples ausente'
            WHEN CASE WHEN to_regclass('public.' || e.table_name) IS NULL THEN true
                      ELSE pg_get_serial_sequence('public.' || e.table_name, 'id') IS NULL END
              THEN 'BLOQUEADOR: sequence SERIAL/IDENTITY ausente'
            ELSE 'COMPATIVEL'
       END AS resultado
FROM expected e
LEFT JOIN information_schema.columns c
       ON c.table_schema = 'public'
      AND c.table_name = e.table_name
      AND c.column_name = 'id'
ORDER BY e.table_name;

/* Constraints esperadas: nome, tabela, colunas, destino, ON DELETE e validação. */
WITH expected(table_name, constraint_name, source_column, target_table, target_column, delete_action) AS (
    VALUES
      ('oscs','fk_oscs_usuario_desativou','id_usuario_desativou','usuarios','id','SET NULL'),
      ('historico_status_osc','fk_historico_status_osc_osc','id_osc','oscs','id','SET NULL'),
      ('historico_status_osc','fk_historico_status_osc_usuario','id_usuario','usuarios','id','SET NULL'),
      ('historico_status_osc','fk_historico_status_osc_desativador_anterior','id_usuario_desativou_anterior','usuarios','id','SET NULL'),
      ('oscs','fk_oscs_usuario_criador','id_usuario_criador','usuarios','id','SET NULL'),
      ('oscs','fk_oscs_usuario_arquivou','id_usuario_arquivou','usuarios','id','SET NULL'),
      ('historico_status_osc','fk_historico_status_osc_arquivador_anterior','id_usuario_arquivou_anterior','usuarios','id','SET NULL')
), actual AS (
    SELECT e.*,
           c.oid AS constraint_oid,
           c.convalidated,
           c.confdeltype,
           c.contype,
           c.confrelid,
           c.conkey,
           c.confkey,
           src.attnum AS source_attnum,
           dst.attnum AS target_attnum
    FROM expected e
    LEFT JOIN pg_constraint c
           ON c.conrelid = to_regclass('public.' || e.table_name)
          AND c.conname = e.constraint_name
          AND c.contype = 'f'
    LEFT JOIN pg_attribute src
           ON src.attrelid = to_regclass('public.' || e.table_name)
          AND src.attname = e.source_column AND NOT src.attisdropped
    LEFT JOIN pg_attribute dst
           ON dst.attrelid = to_regclass('public.' || e.target_table)
          AND dst.attname = e.target_column AND NOT dst.attisdropped
)
SELECT '09 - FKs M1/M2' AS secao,
       table_name || '.' || source_column AS relacao_origem,
       constraint_name,
       target_table || '.' || target_column AS relacao_destino,
       CASE confdeltype WHEN 'n' THEN 'SET NULL'
                        WHEN 'c' THEN 'CASCADE'
                        WHEN 'r' THEN 'RESTRICT'
                        WHEN 'a' THEN 'NO ACTION'
                        WHEN 'd' THEN 'SET DEFAULT'
                        ELSE NULL END AS on_delete_encontrado,
       convalidated AS validada,
       CASE WHEN constraint_oid IS NULL THEN 'BLOQUEADOR: FK ausente'
           WHEN source_attnum IS NULL OR target_attnum IS NULL
              OR confrelid <> to_regclass('public.' || target_table)
              OR conkey <> ARRAY[source_attnum]::smallint[]
              OR confkey <> ARRAY[target_attnum]::smallint[]
              OR confdeltype <> 'n' OR NOT convalidated
              THEN 'BLOQUEADOR: tabela/coluna/acao/validacao diverge'
            ELSE 'COMPATIVEL'
       END AS resultado
FROM actual
ORDER BY table_name, constraint_name;

/* Constraints nomeadas que M1/M2 devem ter validado. A definicao nao inclui dados. */
WITH expected(table_name, constraint_name, constraint_type, definition_fragment) AS (
    VALUES
      ('oscs','ck_oscs_status','c','ATIVA'),
      ('oscs','ck_oscs_origem_ultima_desativacao','c','INDETERMINADA'),
      ('historico_status_osc','ck_historico_status_osc_status_novo','c','ATIVA'),
      ('historico_status_osc','ck_historico_status_osc_origem','c','PORTO_AJUDA'),
      ('oscs','ck_oscs_cnpj_canonico','c','cnpj_valido_alfanumerico'),
      ('historico_status_osc','ck_historico_status_osc_tipo_evento','c','ARQUIVAMENTO')
)
SELECT '10 - CHECK constraints M1/M2' AS secao,
       e.table_name,
       e.constraint_name,
       c.convalidated AS validada,
       CASE WHEN c.oid IS NULL THEN NULL ELSE pg_get_constraintdef(c.oid) END AS definicao,
       CASE WHEN c.oid IS NULL THEN 'BLOQUEADOR: constraint ausente'
            WHEN NOT c.convalidated THEN 'BLOQUEADOR: constraint NOT VALID'
            WHEN POSITION(e.definition_fragment IN pg_get_constraintdef(c.oid)) = 0
              THEN 'BLOQUEADOR: definicao diverge'
            ELSE 'COMPATIVEL'
       END AS resultado
FROM expected e
LEFT JOIN pg_constraint c
       ON c.conrelid = to_regclass('public.' || e.table_name)
      AND c.conname = e.constraint_name
      AND c.contype = e.constraint_type
ORDER BY e.table_name, e.constraint_name;

/* Historico M1: RLS sem politicas; ownership e ACLs efetivas M1/M2. */
WITH hist AS (
    SELECT to_regclass('public.historico_status_osc') AS table_oid,
           CASE WHEN to_regclass('public.historico_status_osc') IS NULL THEN NULL
                ELSE pg_get_serial_sequence('public.historico_status_osc','id') END AS seq_name
)
SELECT '11 - seguranca do historico de status' AS secao,
       pg_get_userbyid(c.relowner) AS proprietario_tabela,
       pg_get_userbyid(seq.relowner) AS proprietario_sequence,
       c.relrowsecurity AS rls_habilitado,
       c.relforcerowsecurity AS force_rls,
       (SELECT count(*) FROM pg_policy p WHERE p.polrelid = c.oid) AS quantidade_policies,
       hist.seq_name AS sequence_id,
       CASE WHEN c.oid IS NULL THEN 'BLOQUEADOR: historico_status_osc ausente'
            WHEN NOT c.relrowsecurity THEN 'BLOQUEADOR: RLS desabilitado'
            WHEN EXISTS (SELECT 1 FROM pg_policy p WHERE p.polrelid = c.oid)
              THEN 'BLOQUEADOR: existe policy RLS'
            WHEN pg_get_userbyid(c.relowner) <> 'postgres'
              THEN 'BLOQUEADOR: owner diferente de postgres'
            WHEN hist.seq_name IS NULL THEN 'BLOQUEADOR: sequence do historico ausente'
            WHEN pg_get_userbyid(seq.relowner) <> 'postgres'
              THEN 'BLOQUEADOR: owner da sequence diverge de M1/M2'
            ELSE 'REVISAR ACLs abaixo'
       END AS resultado
FROM hist
LEFT JOIN pg_class c ON c.oid = hist.table_oid
LEFT JOIN pg_class seq ON seq.oid = CASE WHEN hist.seq_name IS NULL THEN NULL
                                         ELSE to_regclass(hist.seq_name) END;

WITH objects AS (
    SELECT 'TABELA' AS object_type,
           'public.historico_status_osc' AS object_name,
           to_regclass('public.historico_status_osc') AS object_oid
    UNION ALL
    SELECT 'SEQUENCE',
           'sequence de historico_status_osc.id',
           NULL::regclass
), sequence_object AS (
    SELECT 'SEQUENCE' AS object_type,
           CASE WHEN to_regclass('public.historico_status_osc') IS NULL THEN NULL
                ELSE pg_get_serial_sequence('public.historico_status_osc','id') END AS object_name,
           CASE WHEN to_regclass('public.historico_status_osc') IS NULL THEN NULL
                ELSE to_regclass(pg_get_serial_sequence('public.historico_status_osc','id')) END AS object_oid
)
SELECT '12 - privilegios efetivos historico' AS secao,
       role_names.rolname AS role_name,
       o.object_type,
       o.object_name,
       privilege_list.privilege_name AS privilegio,
       CASE
           WHEN o.object_oid IS NULL THEN NULL
           WHEN o.object_type = 'TABELA'
             THEN has_table_privilege(role_names.rolname, o.object_oid, privilege_list.privilege_name)
           ELSE has_sequence_privilege(role_names.rolname, o.object_oid, privilege_list.privilege_name)
       END AS permitido,
       CASE
           WHEN o.object_oid IS NULL THEN 'BLOQUEADOR: objeto ausente'
           WHEN role_names.rolname IN ('anon','authenticated')
            AND CASE WHEN o.object_type = 'TABELA'
                     THEN has_table_privilege(role_names.rolname,o.object_oid,privilege_list.privilege_name)
                     ELSE has_sequence_privilege(role_names.rolname,o.object_oid,privilege_list.privilege_name)
                END THEN 'BLOQUEADOR: privilegio publico efetivo'
           WHEN role_names.rolname = 'postgres'
            AND NOT CASE WHEN o.object_type = 'TABELA'
                         THEN has_table_privilege(role_names.rolname,o.object_oid,privilege_list.privilege_name)
                         ELSE has_sequence_privilege(role_names.rolname,o.object_oid,privilege_list.privilege_name)
                    END THEN 'BLOQUEADOR: role postgres sem privilegio esperado'
           ELSE 'COMPATIVEL'
       END AS resultado
FROM (SELECT * FROM objects WHERE object_type = 'TABELA'
      UNION ALL SELECT * FROM sequence_object) o
CROSS JOIN (VALUES ('postgres'), ('anon'), ('authenticated')) AS role_names(rolname)
CROSS JOIN LATERAL (
    SELECT unnest(CASE WHEN o.object_type = 'TABELA'
        THEN ARRAY['SELECT','INSERT','UPDATE','DELETE','TRUNCATE','REFERENCES','TRIGGER','MAINTAIN']::text[]
        ELSE ARRAY['USAGE','SELECT','UPDATE']::text[] END) AS privilege_name
) privilege_list
JOIN pg_roles r ON r.rolname = role_names.rolname
ORDER BY o.object_type, role_names.rolname, privilege_list.privilege_name;

/* CNPJ: somente contagens; nenhuma identificacao/valor de OSC e exibido. */
DO $cnpj_read_only_check$
DECLARE invalid_count BIGINT;
DECLARE noncanonical_count BIGINT;
DECLARE normalized_duplicate_excess BIGINT;
BEGIN
    IF to_regclass('public.oscs') IS NULL THEN
        RAISE NOTICE 'CNPJ checks: NOT RUN; public.oscs ausente.';
    ELSIF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'oscs' AND column_name = 'cnpj'
    ) THEN
        RAISE NOTICE 'CNPJ checks: NOT RUN; public.oscs.cnpj ausente.';
    ELSE
        EXECUTE $q$
            SELECT count(*) FILTER (
                       WHERE cnpj IS NULL
                          OR (cnpj COLLATE "C") !~ '^[A-Z0-9]{12}[0-9]{2}$'
                   ),
                   count(*) - count(DISTINCT upper(regexp_replace(cnpj, '[^A-Za-z0-9]', '', 'g')))
            FROM public.oscs
        $q$ INTO noncanonical_count, normalized_duplicate_excess;
        RAISE NOTICE 'CNPJ agregados: nao canonicos=%; excesso em grupos duplicados normalizados=% (sem valores pessoais).',
            noncanonical_count, normalized_duplicate_excess;
        IF to_regprocedure('public.cnpj_valido_alfanumerico(text)') IS NULL THEN
            RAISE NOTICE 'CNPJ DV check: BLOQUEADOR; funcao M2 ausente.';
        ELSE
            EXECUTE $q$
                SELECT count(*) FROM public.oscs
                WHERE NOT public.cnpj_valido_alfanumerico(cnpj)
            $q$ INTO invalid_count;
            RAISE NOTICE 'CNPJ DV agregado: registros invalidos=% (sem valores pessoais).', invalid_count;
        END IF;
    END IF;
END
$cnpj_read_only_check$;

/* Contrato da funcao criada por M2 e permissao EXECUTE efetiva. */
SELECT '14 - funcao de validacao CNPJ M2' AS secao,
       p.provolatile AS volatility,
       p.proisstrict AS strict,
       p.proparallel AS parallel_mode,
       pg_get_userbyid(p.proowner) AS proprietario,
       has_function_privilege('postgres',p.oid,'EXECUTE')
           AS postgres_execute,
       has_function_privilege('anon',p.oid,'EXECUTE')
           AS anon_execute,
       has_function_privilege('authenticated',p.oid,'EXECUTE')
           AS authenticated_execute,
       CASE WHEN p.oid IS NULL THEN 'BLOQUEADOR: funcao ausente'
            WHEN p.provolatile <> 'i' OR NOT p.proisstrict OR p.proparallel <> 's'
              THEN 'BLOQUEADOR: propriedades da funcao divergem de M2'
            WHEN pg_get_userbyid(p.proowner) <> 'postgres'
              THEN 'BLOQUEADOR: owner diverge de M2'
            WHEN NOT has_function_privilege('postgres',p.oid,'EXECUTE')
              OR has_function_privilege('anon',p.oid,'EXECUTE')
              OR has_function_privilege('authenticated',p.oid,'EXECUTE')
              THEN 'BLOQUEADOR: grants EXECUTE divergem de M2'
            ELSE 'COMPATIVEL'
       END AS resultado
FROM (VALUES (1)) AS seed(one_row)
LEFT JOIN pg_proc p
       ON p.oid = to_regprocedure('public.cnpj_valido_alfanumerico(text)');

/* Índices M1/M2 devem existir, ser válidos e apontar às colunas esperadas. */
WITH expected(index_name, table_name, unique_expected) AS (
    VALUES
      ('idx_oscs_status_id','oscs',false),
      ('idx_historico_status_osc_osc_data','historico_status_osc',false),
      ('idx_oscs_status_arquivamento','oscs',false)
)
SELECT '15 - indices M1/M2' AS secao,
       e.index_name,
       e.table_name,
       ix.indisvalid AS valido,
       ix.indisready AS pronto,
       ix.indisunique AS unico,
       pg_get_indexdef(ix.indexrelid) AS definicao,
       CASE WHEN ix.indexrelid IS NULL THEN 'BLOQUEADOR: indice ausente'
            WHEN NOT ix.indisvalid OR NOT ix.indisready
              THEN 'BLOQUEADOR: indice invalido ou nao pronto'
            WHEN ix.indisunique <> e.unique_expected
              THEN 'BLOQUEADOR: unicidade diverge'
            ELSE 'PRESENTE: confirme definicao/colunas exibidas'
       END AS resultado
FROM expected e
LEFT JOIN pg_class idx
       ON idx.relnamespace = 'public'::regnamespace
      AND idx.relname = e.index_name
LEFT JOIN pg_index ix
       ON ix.indexrelid = idx.oid
      AND ix.indrelid = to_regclass('public.' || e.table_name)
ORDER BY e.index_name;

/* UNIQUE CNPJ: btree simples, nao parcial, valido, nao baseado em expressao. */
SELECT '16 - indice UNIQUE CNPJ' AS secao,
       found.index_name AS indice,
       found.indisvalid AS valido,
       found.indisready AS pronto,
       found.indpred IS NULL AS nao_parcial,
       found.indexprs IS NULL AS sem_expressao,
       found.indnkeyatts AS colunas_chave,
       found.access_method AS metodo,
       CASE WHEN found.index_oid IS NULL THEN NULL ELSE pg_get_indexdef(found.index_oid) END AS definicao,
       CASE WHEN found.index_oid IS NULL THEN 'BLOQUEADOR: unique CNPJ ausente'
            WHEN NOT found.indisunique OR NOT found.indisvalid OR NOT found.indisready
              OR found.indpred IS NOT NULL OR found.indexprs IS NOT NULL
              OR found.indnkeyatts <> 1 OR found.indnatts <> 1
              OR found.access_method <> 'btree'
              THEN 'BLOQUEADOR: unique CNPJ parcial/expressivo/multicoluna/invalido'
            ELSE 'COMPATIVEL'
       END AS resultado
FROM (VALUES (1)) AS seed(one_row)
LEFT JOIN LATERAL (
    SELECT ix.indexrelid AS index_oid,
           idx.relname AS index_name,
           ix.indisunique,
           ix.indisvalid,
           ix.indisready,
           ix.indpred,
           ix.indexprs,
           ix.indnkeyatts,
           ix.indnatts,
           am.amname AS access_method
    FROM pg_index ix
    JOIN pg_class idx ON idx.oid = ix.indexrelid
    JOIN pg_am am ON am.oid = idx.relam
    WHERE ix.indrelid = to_regclass('public.oscs')
      AND ix.indisunique
      AND ix.indkey[0] = (
          SELECT attnum FROM pg_attribute
          WHERE attrelid = to_regclass('public.oscs')
            AND attname = 'cnpj' AND NOT attisdropped
      )
    ORDER BY idx.relname
    LIMIT 1
) found ON true;

/* M3 precisa alterar public.usuarios; privilege CRUD nao substitui ownership. */
SELECT '17 - ownership/ACL para ALTER TABLE usuarios' AS secao,
       pg_get_userbyid(c.relowner) AS owner_usuarios,
       current_user AS executor_diagnostico,
       (c.relowner = (SELECT oid FROM pg_roles WHERE rolname = current_user)) AS executor_e_owner,
       has_table_privilege(current_user,c.oid,'SELECT') AS pode_select,
       has_table_privilege(current_user,c.oid,'INSERT') AS pode_insert,
       has_table_privilege(current_user,c.oid,'UPDATE') AS pode_update,
       CASE WHEN c.oid IS NULL THEN 'BLOQUEADOR: usuarios ausente'
            WHEN c.relowner <> (SELECT oid FROM pg_roles WHERE rolname = current_user)
              THEN 'BLOQUEADOR: executor atual nao e owner; M3 ALTER TABLE pode falhar'
            WHEN current_user <> 'postgres'
              THEN 'BLOQUEADOR: M3 exige current_user postgres'
            ELSE 'COMPATIVEL para ALTER TABLE pelo owner'
       END AS resultado
FROM pg_class c
WHERE c.oid = to_regclass('public.usuarios');

SELECT '18 - resumo do diagnostico' AS secao,
       'Revise as linhas marcadas BLOQUEADOR; NOTICES do CNPJ mostram apenas contagens.' AS instrucao,
       'Nenhum SELECT deste script retorna linhas de usuarios/OSCs ou valores de CNPJ.' AS privacidade;

COMMIT;

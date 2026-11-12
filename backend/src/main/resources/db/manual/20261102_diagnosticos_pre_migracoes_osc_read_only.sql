-- DIAGNOSTICO SOMENTE LEITURA para revisar antes das migrações M1 e M2.
-- Todas as instruções deste arquivo são SELECTs/CTEs somente leitura.
-- Copie cada consulta para o Supabase SQL Editor. Este arquivo não adquire locks,
-- não inicia/encerra transação, não cria objetos e não modifica registros.
-- Não substitui backup, revisão das migrações nem autorização de execução.

-- 1. Presença das tabelas necessárias para M1/M2.
SELECT nome_tabela,
       to_regclass(format('public.%I', nome_tabela)) IS NOT NULL AS existe
FROM (VALUES ('oscs'), ('usuarios'), ('historico_status_osc')) AS t(nome_tabela)
ORDER BY nome_tabela;

-- 2. Colunas, tipos, nulabilidade, tamanho, default e identidade das tabelas envolvidas.
SELECT table_name, ordinal_position, column_name, data_type,
       udt_name, is_nullable, character_maximum_length,
       column_default, is_identity, identity_generation
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('oscs', 'usuarios', 'historico_status_osc')
ORDER BY table_name, ordinal_position;

-- 3. Primary keys, unique constraints, checks e respectivas definições.
SELECT c.conrelid::regclass AS tabela, c.conname AS constraint_name,
       c.contype AS tipo, pg_get_constraintdef(c.oid, true) AS definicao,
       c.convalidated AS validada
FROM pg_constraint c
WHERE c.connamespace = 'public'::regnamespace
  AND c.conrelid IN (
      to_regclass('public.oscs'),
      to_regclass('public.usuarios'),
      to_regclass('public.historico_status_osc')
  )
  AND c.contype IN ('p', 'u', 'c')
ORDER BY c.conrelid::regclass::text, c.contype, c.conname;

-- 4. Foreign keys, colunas de origem/destino, ação de exclusão e validação.
SELECT c.conrelid::regclass AS tabela_origem,
       c.conname AS constraint_name,
       array_agg(a_src.attname ORDER BY key_col.ordinality) AS colunas_origem,
       c.confrelid::regclass AS tabela_destino,
       array_agg(a_dst.attname ORDER BY key_col.ordinality) AS colunas_destino,
       CASE c.confdeltype
           WHEN 'a' THEN 'NO ACTION'
           WHEN 'r' THEN 'RESTRICT'
           WHEN 'c' THEN 'CASCADE'
           WHEN 'n' THEN 'SET NULL'
           WHEN 'd' THEN 'SET DEFAULT'
           ELSE 'DESCONHECIDA'
       END AS on_delete,
       c.convalidated AS validada
FROM pg_constraint c
CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS key_col(attnum, ordinality)
JOIN pg_attribute a_src
  ON a_src.attrelid = c.conrelid AND a_src.attnum = key_col.attnum
JOIN LATERAL unnest(c.confkey) WITH ORDINALITY AS ref_col(attnum, ordinality)
  ON ref_col.ordinality = key_col.ordinality
JOIN pg_attribute a_dst
  ON a_dst.attrelid = c.confrelid AND a_dst.attnum = ref_col.attnum
WHERE c.contype = 'f'
  AND c.connamespace = 'public'::regnamespace
  AND c.conrelid IN (
      to_regclass('public.oscs'),
      to_regclass('public.usuarios'),
      to_regclass('public.historico_status_osc')
  )
GROUP BY c.oid, c.conrelid, c.conname, c.confrelid, c.confdeltype, c.convalidated
ORDER BY c.conrelid::regclass::text, c.conname;

-- 5. Índices existentes, incluindo unique, validade, parcialidade e expressão.
SELECT schemaname, tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename IN ('oscs', 'usuarios', 'historico_status_osc')
ORDER BY tablename, indexname;

-- 6. PK e configuração identity/serial das colunas ID usadas por JPA/FKs.
SELECT t.nome_tabela, t.nome_coluna,
       a.attnotnull AS not_null,
       a.attidentity AS identity_kind,
       CASE WHEN to_regclass(format('public.%I', t.nome_tabela)) IS NULL THEN NULL
            ELSE pg_get_serial_sequence(format('public.%I', t.nome_tabela), t.nome_coluna)
       END AS sequence_name,
       EXISTS (
           SELECT 1 FROM pg_constraint p
           WHERE p.conrelid = to_regclass(format('public.%I', t.nome_tabela))
             AND p.contype = 'p'
             AND p.conkey = ARRAY[a.attnum]::smallint[]
       ) AS single_column_primary_key
FROM (VALUES ('oscs', 'id'), ('usuarios', 'id'), ('historico_status_osc', 'id'))
     AS t(nome_tabela, nome_coluna)
LEFT JOIN pg_attribute a
  ON a.attrelid = to_regclass(format('public.%I', t.nome_tabela))
 AND a.attname = t.nome_coluna
 AND NOT a.attisdropped
ORDER BY t.nome_tabela;

-- 7. Estado legado de oscs.ativo. Esperado: somente TRUE/FALSE, sem NULL.
SELECT ativo, COUNT(*) AS quantidade
FROM public.oscs
GROUP BY ativo
ORDER BY ativo NULLS FIRST;

-- 8. CNPJs que não normalizam por pontuação para 12 caracteres A-Z/0-9
-- seguidos por dois dígitos verificadores. Letras minúsculas não são canônicas.
SELECT id, cnpj, regexp_replace(cnpj, '[./-]', '', 'g') AS cnpj_normalizado
FROM public.oscs
WHERE cnpj IS NULL
   OR length(regexp_replace(cnpj, '[./-]', '', 'g')) <> 14
   OR (regexp_replace(cnpj, '[./-]', '', 'g') COLLATE "C") !~ '^[A-Z0-9]{12}[0-9]{2}$'
ORDER BY id;

-- 9. CNPJs duplicados depois de remover pontuação e normalizar caixa.
SELECT upper(regexp_replace(cnpj, '[./-]', '', 'g')) AS cnpj_normalizado,
       COUNT(*) AS quantidade,
       array_agg(id ORDER BY id) AS ids_oscs
FROM public.oscs
GROUP BY upper(regexp_replace(cnpj, '[./-]', '', 'g'))
HAVING COUNT(*) > 1
ORDER BY cnpj_normalizado;

-- 10. CNPJs com dígitos verificadores inválidos segundo a regra RFB/SERPRO:
-- valor ASCII - 48, módulo 11 e pesos 2..9 da direita para a esquerda.
WITH normalizados AS (
    SELECT id, regexp_replace(cnpj, '[./-]', '', 'g') AS digitos
    FROM public.oscs
), calculados AS (
    SELECT n.id, n.digitos,
           (SELECT SUM((ASCII(substring(n.digitos FROM w.pos::integer FOR 1)) - 48) * w.peso)
            FROM unnest(ARRAY[5,4,3,2,9,8,7,6,5,4,3,2]) WITH ORDINALITY AS w(peso, pos)) AS soma1,
           (SELECT SUM((ASCII(substring(n.digitos FROM w.pos::integer FOR 1)) - 48) * w.peso)
            FROM unnest(ARRAY[6,5,4,3,2,9,8,7,6,5,4,3,2]) WITH ORDINALITY AS w(peso, pos)) AS soma2
    FROM normalizados n
    WHERE length(n.digitos) = 14
      AND (n.digitos COLLATE "C") ~ '^[A-Z0-9]{12}[0-9]{2}$'
)
SELECT id, digitos AS cnpj_normalizado
FROM calculados
WHERE (ASCII(substring(digitos FROM 13 FOR 1)) - 48) <>
          CASE WHEN soma1 % 11 < 2 THEN 0 ELSE 11 - (soma1 % 11) END
   OR (ASCII(substring(digitos FROM 14 FOR 1)) - 48) <>
          CASE WHEN soma2 % 11 < 2 THEN 0 ELSE 11 - (soma2 % 11) END
   OR digitos = repeat(substring(digitos FROM 1 FOR 1), 14)
ORDER BY id;

-- 11. Confirmar unicidade simples de btree em oscs.cnpj, válida e não parcial.
SELECT i.indexrelid::regclass AS index_name,
       i.indisunique AS is_unique,
       i.indisvalid AS valido,
       i.indisready AS pronto,
       i.indpred IS NULL AS nao_parcial,
       i.indexprs IS NULL AS sem_expressao,
       i.indnkeyatts AS colunas_chave,
       pg_get_indexdef(i.indexrelid) AS definicao
FROM pg_index i
JOIN pg_class idx ON idx.oid = i.indexrelid
JOIN pg_am am ON am.oid = idx.relam
WHERE i.indrelid = to_regclass('public.oscs')
  AND am.amname = 'btree'
  AND i.indisunique
  AND i.indkey[0] = (
      SELECT attnum FROM pg_attribute
      WHERE attrelid = to_regclass('public.oscs')
        AND attname = 'cnpj' AND NOT attisdropped
  )
ORDER BY i.indexrelid::regclass::text;

-- 12. Relações/índices com nomes reservados. Antes de M1, espera-se zero linha
-- para historico_status_osc e zero índices novos de status.
SELECT n.nspname AS schema_name, c.relname AS objeto,
       c.relkind AS tipo_objeto
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relname IN (
      'historico_status_osc',
      'idx_oscs_status_id', 'idx_historico_status_osc_osc_data',
      'idx_oscs_status_arquivamento'
  )
ORDER BY objeto;

-- 13. Constraints com nomes reservados pelas migrações. Antes de M1, espera-se
-- zero linha. Depois de M1/M2, compare com as definições esperadas nos scripts.
SELECT c.conrelid::regclass AS tabela, c.conname AS constraint_name,
       c.contype AS tipo, pg_get_constraintdef(c.oid, true) AS definicao
FROM pg_constraint c
WHERE c.connamespace = 'public'::regnamespace
  AND c.conname IN (
      'ck_oscs_status', 'ck_oscs_origem_ultima_desativacao',
      'fk_oscs_usuario_desativou', 'fk_oscs_usuario_criador',
      'fk_oscs_usuario_arquivou', 'ck_oscs_cnpj_canonico',
      'fk_historico_status_osc_osc', 'fk_historico_status_osc_usuario',
      'fk_historico_status_osc_desativador_anterior',
      'ck_historico_status_osc_id_snapshot',
      'ck_historico_status_osc_status_anterior',
      'ck_historico_status_osc_status_novo',
      'ck_historico_status_osc_origem',
      'ck_historico_status_osc_origem_anterior',
      'ck_historico_status_osc_tipo_evento',
      'fk_historico_status_osc_arquivador_anterior'
  )
ORDER BY c.conrelid::regclass::text, c.conname;

-- 14. Snapshot inicial de status. Execute somente antes de M1, pois M1 remove ativo.
SELECT id, ativo
FROM public.oscs
ORDER BY id;

-- 15. Resumo de presença das colunas de M1/M2. Execute novamente entre M1 e M2
-- para confirmar o estado intermediário esperado.
SELECT table_name, column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND (table_name, column_name) IN (
      ('oscs', 'ativo'), ('oscs', 'status'), ('oscs', 'origem_ultima_desativacao'),
      ('oscs', 'data_ultima_desativacao'), ('oscs', 'id_usuario_desativou'),
      ('oscs', 'id_usuario_desativou_snapshot'), ('oscs', 'data_arquivamento'),
      ('oscs', 'id_usuario_arquivou'), ('oscs', 'id_usuario_arquivou_snapshot'),
      ('oscs', 'motivo_arquivamento'), ('historico_status_osc', 'id'),
      ('historico_status_osc', 'id_osc'), ('historico_status_osc', 'id_osc_snapshot'),
      ('historico_status_osc', 'tipo_evento'), ('historico_status_osc', 'id_usuario_snapshot'),
      ('historico_status_osc', 'id_usuario_desativou_anterior_snapshot'),
      ('historico_status_osc', 'id_usuario_arquivou_anterior'),
      ('historico_status_osc', 'id_usuario_arquivou_anterior_snapshot')
  )
ORDER BY table_name, column_name;

-- 16. Dependências PostgreSQL catalogadas diretamente sobre oscs.ativo.
-- Qualquer linha precisa ser analisada antes do DROP COLUMN.
SELECT pg_describe_object(d.classid, d.objid, d.objsubid) AS objeto_dependente,
       d.deptype AS tipo_dependencia
FROM pg_depend d
JOIN pg_attribute a
  ON a.attrelid = d.refobjid AND a.attnum = d.refobjsubid
WHERE d.refclassid = 'pg_class'::regclass
  AND d.refobjid = to_regclass('public.oscs')
  AND a.attname = 'ativo'
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
ORDER BY objeto_dependente;

-- 17. Rotinas cujo texto menciona ativo (pode haver falsos positivos).
-- pg_depend não registra necessariamente nomes de colunas em corpos PL/pgSQL,
-- SQL dinâmico ou código externo; revisar os resultados manualmente.
SELECT n.nspname AS schema_name,
       p.oid::regprocedure AS routine_signature,
       p.prokind AS routine_kind,
       p.prosrc ILIKE '%oscs%' AS mentions_oscs,
       p.prosrc ILIKE '%ativo%' AS mentions_ativo
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
JOIN pg_language l ON l.oid = p.prolang
WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
  AND l.lanname IN ('sql', 'plpgsql')
  AND p.prosrc ILIKE '%ativo%'
ORDER BY n.nspname, p.oid::regprocedure::text;

-- 18. Triggers associados a oscs (examinar se algum depende de oscs.ativo).
SELECT c.relname AS tabela,
       t.tgname AS trigger_name,
       p.oid::regprocedure AS trigger_function,
       p.prosrc ILIKE '%ativo%' AS function_mentions_ativo
FROM pg_trigger t
JOIN pg_class c ON c.oid = t.tgrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
JOIN pg_proc p ON p.oid = t.tgfoid
WHERE n.nspname = 'public'
  AND c.relname = 'oscs'
  AND NOT t.tgisinternal
ORDER BY t.tgname;

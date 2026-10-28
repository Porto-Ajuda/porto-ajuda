-- Migração manual, transacional e de execução única.
-- Preparada para o schema verificado: osc_causas(id_osc, causa, causa_principal).
-- Mantém a tabela original completa em osc_causas_legado_20261027.
-- Valores antigos sem uma principal identificada não são promovidos a principal.
-- Não executar sem backup, revisão do preflight e autorização manual.

BEGIN;

LOCK TABLE public.oscs IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE public.osc_causas IN ACCESS EXCLUSIVE MODE;

DO $preflight$
DECLARE
    principal_invalida RECORD;
    causa_invalida RECORD;
    principal_repetida RECORD;
BEGIN
    IF to_regclass('public.osc_causas_legado_20261027') IS NOT NULL THEN
        RAISE EXCEPTION 'Migração interrompida: public.osc_causas_legado_20261027 já existe. Não reaplique este script.';
    END IF;

    IF to_regclass('public.oscs') IS NULL
       OR NOT EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = 'public' AND table_name = 'oscs' AND column_name = 'id'
       ) THEN
        RAISE EXCEPTION 'Migração interrompida: public.oscs(id) não existe.';
    END IF;

    IF NOT EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = 'public' AND table_name = 'osc_causas' AND column_name = 'id_osc'
       ) OR NOT EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = 'public' AND table_name = 'osc_causas' AND column_name = 'causa'
       ) OR NOT EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = 'public' AND table_name = 'osc_causas' AND column_name = 'causa_principal'
       ) THEN
        RAISE EXCEPTION 'Migração interrompida: osc_causas não corresponde ao schema verificado (esperadas id_osc, causa e causa_principal).';
    END IF;

    SELECT antiga.id_osc, count(DISTINCT antiga.causa_principal) AS quantidade
      INTO principal_repetida
      FROM public.osc_causas antiga
     WHERE antiga.causa_principal IS NOT NULL
     GROUP BY antiga.id_osc
    HAVING count(DISTINCT antiga.causa_principal) > 1
     LIMIT 1;
    IF FOUND THEN
        RAISE EXCEPTION 'Migração interrompida: OSC % tem mais de uma causa_principal distinta.', principal_repetida.id_osc;
    END IF;

    SELECT antiga.id_osc, antiga.causa_principal
      INTO principal_invalida
      FROM public.osc_causas antiga
     WHERE antiga.causa_principal IS NOT NULL
       AND antiga.causa_principal NOT IN (
           'EDUCACAO', 'SAUDE', 'MEIO_AMBIENTE', 'ANIMAIS',
           'INCLUSAO_SOCIAL', 'ESPORTE', 'CULTURA', 'COMBATE_POBREZA'
       )
     LIMIT 1;
    IF FOUND THEN
        RAISE EXCEPTION 'Migração interrompida: causa_principal inválida na OSC %: %.',
            principal_invalida.id_osc, principal_invalida.causa_principal;
    END IF;

    SELECT antiga.id_osc, antiga.causa
      INTO causa_invalida
      FROM public.osc_causas antiga
     WHERE antiga.causa IS NOT NULL
       AND antiga.causa NOT IN (
           'EDUCACAO', 'SAUDE', 'MEIO_AMBIENTE', 'ANIMAIS',
           'INCLUSAO_SOCIAL', 'ESPORTE', 'CULTURA', 'COMBATE_POBREZA'
       )
     LIMIT 1;
    IF FOUND THEN
        RAISE EXCEPTION 'Migração interrompida: causa histórica inválida na OSC %: %.',
            causa_invalida.id_osc, causa_invalida.causa;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM public.osc_causas antiga
          LEFT JOIN public.oscs o ON o.id = antiga.id_osc
         WHERE o.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Migração interrompida: existem causas sem ONG correspondente; resolva as referências antes da migração.';
    END IF;
END
$preflight$;

-- Arquiva a tabela antiga completa, sem excluir registros.
ALTER TABLE public.osc_causas
    RENAME TO osc_causas_legado_20261027;

-- This table matches the current JPA embeddable collection mapping.
CREATE TABLE public.osc_causas (
    id_osc INTEGER NOT NULL,
    causa VARCHAR(50),
    causa_principal VARCHAR(50),
    CONSTRAINT fk_osc_causas_osc
        FOREIGN KEY (id_osc) REFERENCES public.oscs(id),
    CONSTRAINT ck_osc_causas_causa
        CHECK (
            causa IS NULL OR causa IN (
                'EDUCACAO', 'SAUDE', 'MEIO_AMBIENTE', 'ANIMAIS',
                'INCLUSAO_SOCIAL', 'ESPORTE', 'CULTURA', 'COMBATE_POBREZA'
            )
        ),
    CONSTRAINT ck_osc_causas_principal
        CHECK (
            causa_principal IS NULL OR causa_principal IN (
                'EDUCACAO', 'SAUDE', 'MEIO_AMBIENTE', 'ANIMAIS',
                'INCLUSAO_SOCIAL', 'ESPORTE', 'CULTURA', 'COMBATE_POBREZA'
            )
        ),
    CONSTRAINT ck_osc_causas_sem_repetir_principal
        CHECK (
            causa IS NULL OR causa_principal IS NULL OR causa <> causa_principal
        )
);

-- Preserva qualquer principal explícita e mantém todas as causas históricas.
-- Sem principal explícita, nenhuma causa é escolhida como principal.
WITH principal_por_osc AS (
    SELECT o.id AS id_osc,
           max(antiga.causa_principal) AS causa_principal
    FROM public.oscs o
    LEFT JOIN public.osc_causas_legado_20261027 antiga
           ON antiga.id_osc = o.id
    GROUP BY o.id
), secundarias AS (
    SELECT DISTINCT p.id_osc, antiga.causa, p.causa_principal
    FROM principal_por_osc p
    JOIN public.osc_causas_legado_20261027 antiga
      ON antiga.id_osc = p.id_osc
    WHERE antiga.causa IS NOT NULL
      AND antiga.causa IS DISTINCT FROM p.causa_principal
)
INSERT INTO public.osc_causas (id_osc, causa, causa_principal)
SELECT id_osc, causa, causa_principal
FROM secundarias
UNION ALL
SELECT p.id_osc, NULL, p.causa_principal
FROM principal_por_osc p
WHERE NOT EXISTS (
    SELECT 1 FROM secundarias s WHERE s.id_osc = p.id_osc
);

CREATE UNIQUE INDEX uq_osc_causas_secundaria
    ON public.osc_causas (id_osc, causa)
    WHERE causa IS NOT NULL;
CREATE UNIQUE INDEX uq_osc_causas_sem_secundarias
    ON public.osc_causas (id_osc)
    WHERE causa IS NULL;

-- Verificações bloqueantes antes de confirmar a transação.
DO $postflight$
BEGIN
    IF (SELECT count(DISTINCT id_osc) FROM public.osc_causas)
       <> (SELECT count(*) FROM public.oscs) THEN
        RAISE EXCEPTION 'Migração interrompida: quantidade de linhas de osc_causas diferente da quantidade de ONGs.';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM public.osc_causas_legado_20261027 antiga
          JOIN public.osc_causas nova ON nova.id_osc = antiga.id_osc
         WHERE antiga.causa IS NOT NULL
           AND antiga.causa IS DISTINCT FROM nova.causa_principal
           AND NOT EXISTS (
               SELECT 1 FROM public.osc_causas copia
               WHERE copia.id_osc = antiga.id_osc
                 AND copia.causa = antiga.causa
           )
    ) THEN
        RAISE EXCEPTION 'Migração interrompida: ao menos uma causa histórica não aparece na nova estrutura.';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM public.osc_causas_legado_20261027 antiga
          JOIN public.osc_causas nova ON nova.id_osc = antiga.id_osc
         WHERE antiga.causa_principal IS NOT NULL
           AND antiga.causa_principal IS DISTINCT FROM nova.causa_principal
    ) THEN
        RAISE EXCEPTION 'Migração interrompida: uma causa principal previamente registrada não foi preservada.';
    END IF;
END
$postflight$;

-- Lista ONGs que conservam causas históricas e precisam definir a principal.
SELECT id_osc, causa_principal,
       array_agg(causa ORDER BY causa) FILTER (WHERE causa IS NOT NULL) AS causas_secundarias
FROM public.osc_causas
WHERE causa_principal IS NULL
  AND causa IS NOT NULL
GROUP BY id_osc, causa_principal
ORDER BY id_osc;

COMMIT;

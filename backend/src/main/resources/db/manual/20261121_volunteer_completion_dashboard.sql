BEGIN;

-- Additive migration for opportunity completion and private per-participant certificates.
-- Verify the canonical production schema before changing it; this migration assumes the
-- table/column names mapped by Vaga and CandidaturasVoluntariado entities.
DO $preflight$
DECLARE
    unknown_statuses TEXT;
    status_att SMALLINT;
    constraint_name TEXT;
BEGIN
    IF to_regclass('public.vagas_voluntariado') IS NULL
       OR to_regclass('public.candidaturas_voluntariado') IS NULL
       OR to_regclass('public.post') IS NULL
       OR to_regclass('public.osc_membros') IS NULL THEN
        RAISE EXCEPTION 'Preflight: expected NGO volunteering tables are missing; no changes applied.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='vagas_voluntariado' AND column_name='id_post' AND data_type='integer')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='vagas_voluntariado' AND column_name='quantidade_vagas' AND data_type='integer')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='candidaturas_voluntariado' AND column_name='id_vaga' AND data_type='integer')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='candidaturas_voluntariado' AND column_name='status') THEN
        RAISE EXCEPTION 'Preflight: canonical vacancy/application columns differ from the mapped application schema.';
    END IF;
    SELECT string_agg(DISTINCT status, ', ' ORDER BY status) INTO unknown_statuses
      FROM public.candidaturas_voluntariado
     WHERE status NOT IN ('PENDENTE','APROVADA','RECUSADA','DESISTIU','CONCLUIDA','CANCELADA_PELA_ONG');
    IF unknown_statuses IS NOT NULL THEN
        RAISE EXCEPTION 'Preflight: unrecognized application statuses exist (%); no changes applied.', unknown_statuses;
    END IF;
    SELECT attnum INTO status_att FROM pg_attribute
     WHERE attrelid='public.candidaturas_voluntariado'::regclass AND attname='status' AND NOT attisdropped;
    IF EXISTS (SELECT 1 FROM pg_constraint
                WHERE conrelid='public.candidaturas_voluntariado'::regclass AND contype='c'
                  AND status_att=ANY(conkey) AND cardinality(conkey)>1) THEN
        RAISE EXCEPTION 'Preflight: a composite application check references status; review its lifecycle rule before migration.';
    END IF;
    FOR constraint_name IN
        SELECT conname FROM pg_constraint
         WHERE conrelid='public.candidaturas_voluntariado'::regclass AND contype='c'
           AND status_att = ANY(conkey) AND cardinality(conkey)=1
    LOOP
        EXECUTE format('ALTER TABLE public.candidaturas_voluntariado DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END
$preflight$;

ALTER TABLE public.vagas_voluntariado
    ADD COLUMN IF NOT EXISTS data_inicio_real DATE,
    ADD COLUMN IF NOT EXISTS data_fim_real DATE,
    ADD COLUMN IF NOT EXISTS data_conclusao TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS horas_totais_realizadas NUMERIC(8,2);

ALTER TABLE public.candidaturas_voluntariado
    ADD COLUMN IF NOT EXISTS data_conclusao DATE,
    ADD COLUMN IF NOT EXISTS horas_realizadas NUMERIC(8,2),
    ADD COLUMN IF NOT EXISTS certificado_object_key TEXT;

ALTER TABLE public.candidaturas_voluntariado
    ADD CONSTRAINT ck_candidaturas_voluntariado_status
        CHECK (status IN ('PENDENTE','APROVADA','RECUSADA','DESISTIU','CONCLUIDA','CANCELADA_PELA_ONG')),
    ADD CONSTRAINT ck_candidatura_completion_fields
        CHECK ((status='CONCLUIDA' AND data_conclusao IS NOT NULL AND horas_realizadas IS NOT NULL AND horas_realizadas > 0)
            OR (status<>'CONCLUIDA' AND data_conclusao IS NULL AND horas_realizadas IS NULL));

ALTER TABLE public.vagas_voluntariado
    ADD CONSTRAINT ck_vaga_completion_fields
        CHECK ((data_conclusao IS NULL AND data_inicio_real IS NULL AND data_fim_real IS NULL AND horas_totais_realizadas IS NULL)
            OR (data_conclusao IS NOT NULL AND status='ENCERRADA' AND data_inicio_real IS NOT NULL
                AND data_fim_real IS NOT NULL AND data_fim_real >= data_inicio_real
                AND horas_totais_realizadas IS NOT NULL AND horas_totais_realizadas >= 0));

CREATE INDEX IF NOT EXISTS idx_candidaturas_voluntariado_vaga_status
    ON public.candidaturas_voluntariado(id_vaga,status);
CREATE INDEX IF NOT EXISTS idx_vagas_voluntariado_completion
    ON public.vagas_voluntariado(data_conclusao) WHERE data_conclusao IS NOT NULL;

COMMIT;

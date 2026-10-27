-- Aplicar uma vez no PostgreSQL antes de publicar a versão que remove o Pix
-- direto da entidade Osc. A migração preserva os valores já existentes.
ALTER TABLE oscs
    ALTER COLUMN chave_pix DROP NOT NULL,
    ALTER COLUMN tipo_chave_pix DROP NOT NULL;

ALTER TABLE oscs ADD COLUMN IF NOT EXISTS descricao_curta VARCHAR(200);
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS ano_fundacao INTEGER;
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS whatsapp VARCHAR(20);
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS linkedin VARCHAR(500);
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS youtube VARCHAR(500);
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS area_geografica VARCHAR(30);
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS estado VARCHAR(100);
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS pais VARCHAR(100);
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS horario_abertura TIME;
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS horario_fechamento TIME;
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS atendimento_presencial BOOLEAN;
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS atendimento_online BOOLEAN;
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS necessita_agendamento BOOLEAN;
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS aceita_voluntarios BOOLEAN;
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS frequencia_voluntariado VARCHAR(30);
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS idade_minima_voluntario INTEGER;
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS processo_seletivo_voluntario BOOLEAN;
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS emite_certificado_voluntario BOOLEAN;
ALTER TABLE oscs ADD COLUMN IF NOT EXISTS atividades_voluntariado TEXT;

CREATE TABLE IF NOT EXISTS osc_ods (
    id_osc INTEGER NOT NULL REFERENCES oscs(id) ON DELETE CASCADE,
    ods INTEGER NOT NULL CHECK (ods BETWEEN 1 AND 17),
    PRIMARY KEY (id_osc, ods)
);

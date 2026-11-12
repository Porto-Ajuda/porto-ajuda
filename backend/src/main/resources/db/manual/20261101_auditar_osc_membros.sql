-- Auditoria somente leitura antes de migrar osc_membros.cargo_osc para CargoOsc.
-- Execute apenas no banco local e registre os resultados antes de preparar UPDATEs.

SELECT cargo_osc, COUNT(*) AS quantidade
FROM osc_membros
GROUP BY cargo_osc
ORDER BY cargo_osc;

SELECT id_osc, id_usuario
FROM osc_membros
WHERE cargo_osc IS NULL;

SELECT id_osc, id_usuario, COUNT(*) AS quantidade
FROM osc_membros
GROUP BY id_osc, id_usuario
HAVING COUNT(*) > 1;

SELECT o.id AS id_osc, o.id_usuario_criador
FROM oscs o
LEFT JOIN osc_membros m
    ON m.id_osc = o.id
   AND m.id_usuario = o.id_usuario_criador
WHERE m.id_osc IS NULL;

SELECT o.id AS id_osc, o.id_usuario_criador, m.id_usuario, m.cargo_osc
FROM oscs o
JOIN osc_membros m ON m.id_osc = o.id
WHERE (m.cargo_osc = 'CRIADOR' AND m.id_usuario <> o.id_usuario_criador)
   OR (m.id_usuario = o.id_usuario_criador AND m.cargo_osc <> 'CRIADOR');

SELECT id_osc, COUNT(*) AS quantidade_criadores
FROM osc_membros
WHERE cargo_osc = 'CRIADOR'
GROUP BY id_osc
HAVING COUNT(*) > 1;

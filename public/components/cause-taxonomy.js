/** Display taxonomy. Legacy NGO enum values are aliases; persisted IDs stay unchanged. */
export const CAUSE_TAXONOMY = Object.freeze([
  { id: "alimentacao", label: "Combate à fome", icon: "fa-utensils", causes: ["COMBATE_FOME"] },
  { id: "criancas-adolescentes", label: "Crianças e adolescentes", icon: "fa-child", causes: ["CRIANCAS_ADOLESCENTES"] },
  { id: "idosos", label: "Pessoas idosas", icon: "fa-person-cane", causes: ["IDOSOS"] },
  { id: "saude", label: "Saúde", icon: "fa-heart-pulse", causes: ["SAUDE"] },
  { id: "educacao", label: "Educação", icon: "fa-graduation-cap", causes: ["EDUCACAO"] },
  { id: "animais", label: "Proteção animal", icon: "fa-paw", causes: ["ANIMAIS"] },
  { id: "meio-ambiente", label: "Meio ambiente", icon: "fa-leaf", causes: ["MEIO_AMBIENTE"] },
  { id: "moradia", label: "Moradia", icon: "fa-house", causes: ["MORADIA"] },
  { id: "pessoas-com-deficiencia", label: "Pessoas com deficiência", icon: "fa-wheelchair", causes: ["PESSOAS_COM_DEFICIENCIA"] },
  { id: "mulheres", label: "Apoio às mulheres", icon: "fa-venus", causes: ["APOIO_MULHERES"] },
  { id: "cultura-esporte", label: "Cultura e esporte", icon: "fa-masks-theater", causes: ["CULTURA_ESPORTE", "CULTURA", "ESPORTE"] },
  { id: "emergencias", label: "Ajuda humanitária e emergências", icon: "fa-life-ring", causes: ["AJUDA_HUMANITARIA_EMERGENCIAS"] },
  { id: "assistencia-social", label: "Assistência social", icon: "fa-hand-holding-heart", causes: ["ASSISTENCIA_SOCIAL", "INCLUSAO_SOCIAL", "COMBATE_POBREZA"] },
  { id: "direitos-humanos-cidadania", label: "Direitos humanos e cidadania", icon: "fa-scale-balanced", causes: ["DIREITOS_HUMANOS_CIDADANIA", "INCLUSAO_SOCIAL"] }
]);

export const CAUSE_IDS = Object.freeze(CAUSE_TAXONOMY.map(item => item.id));
const byId = new Map(CAUSE_TAXONOMY.map(item => [item.id, item]));

export function normalizeCausePreferences(values) {
  const selected = new Set(Array.isArray(values) ? values.map(String) : []);
  return CAUSE_IDS.filter(id => selected.has(id));
}

export function causeCategoriesForNgo(ngo) {
  const raw = [ngo?.causaPrincipal, ...(ngo?.causas || []), ...(ngo?.causasSecundarias || [])]
    .filter(value => typeof value === "string");
  const values = new Set(raw);
  return CAUSE_TAXONOMY.filter(category => category.causes.some(value => values.has(value)))
    .map(category => category.id);
}

export function causeLabel(id) { return byId.get(id)?.label || "Organização social"; }

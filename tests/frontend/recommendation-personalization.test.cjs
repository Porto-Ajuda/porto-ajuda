const test = require("node:test");
const assert = require("node:assert/strict");

async function models() {
  const taxonomy = await import("../../public/components/cause-taxonomy.js");
  const model = await import("../../public/components/recommendation-model.js");
  return { ...taxonomy, ...model };
}

test("taxonomy has fourteen display groups and maps old NGO cause identifiers without renaming them", async () => {
  const { CAUSE_TAXONOMY, causeCategoriesForNgo } = await models();
  assert.equal(CAUSE_TAXONOMY.length, 14);
  assert.ok(CAUSE_TAXONOMY.some(item => item.label === "Combate à fome"));
  assert.ok(CAUSE_TAXONOMY.some(item => item.label === "Ajuda humanitária e emergências"));
  assert.deepEqual(causeCategoriesForNgo({ causaPrincipal: "CULTURA", causasSecundarias: ["ESPORTE", "INCLUSAO_SOCIAL"] }),
    ["cultura-esporte", "assistencia-social", "direitos-humanos-cidadania"]);
});

test("baseline totals 100, gives saved causes twice the weight, and respects the floor", async () => {
  const { CAUSE_IDS, baselineWeights, RECOMMENDATION_FLOOR } = await models();
  const equal = baselineWeights([]);
  assert.ok(Math.abs(Object.values(equal).reduce((sum, value) => sum + value, 0) - 100) < 1e-10);
  assert.ok(Object.values(equal).every(value => value >= RECOMMENDATION_FLOOR));
  const selected = CAUSE_IDS[0];
  const other = CAUSE_IDS[1];
  const baseline = baselineWeights([selected]);
  assert.ok(Math.abs(baseline[selected] / baseline[other] - 2) < 1e-12);
  assert.ok(Math.abs(Object.values(baseline).reduce((sum, value) => sum + value, 0) - 100) < 1e-10);
});

test("profile visit transfers no more than two points, equally across NGO causes, without crossing the floor", async () => {
  const { CAUSE_IDS, baselineWeights, transferVisitWeight, RECOMMENDATION_FLOOR } = await models();
  const start = baselineWeights([]);
  const [firstTarget, secondTarget] = CAUSE_IDS;
  const next = transferVisitWeight(start, [firstTarget, secondTarget, firstTarget]);
  assert.ok(Math.abs(next[firstTarget] - start[firstTarget] - 1) < 1e-10);
  assert.ok(Math.abs(next[secondTarget] - start[secondTarget] - 1) < 1e-10);
  assert.ok(Math.abs(Object.values(next).reduce((sum, value) => sum + value, 0) - 100) < 1e-10);
  assert.ok(Object.values(next).every(value => value >= RECOMMENDATION_FLOOR - 1e-10));
  const noDonorCapacity = Object.fromEntries(CAUSE_IDS.map((id, index) => [id, index === 0 ? 74 : 2]));
  assert.deepEqual(transferVisitWeight(noDonorCapacity, [firstTarget]), noDonorCapacity);
});

test("weighted cause draw is deterministic and returns every NGO once, including uncategorized records", async () => {
  const { baselineWeights, weightedNgoOrder } = await models();
  const records = [
    { id: 1, causaPrincipal: "ANIMAIS" },
    { id: 2, causaPrincipal: "ANIMAIS", causasSecundarias: ["SAUDE"] },
    { id: 3, causaPrincipal: "EDUCACAO" },
    { id: 4, causaPrincipal: null }
  ];
  const weights = baselineWeights(["animais"]);
  const first = weightedNgoOrder(records, weights, () => 0.25);
  const repeat = weightedNgoOrder(records, weights, () => 0.25);
  assert.deepEqual(first.map(ngo => ngo.id), repeat.map(ngo => ngo.id));
  assert.equal(first.length, records.length);
  assert.equal(new Set(first.map(ngo => ngo.id)).size, records.length);
  assert.ok(first.some(ngo => ngo.id === 4), "uncategorized NGOs use the documented equal-weight fallback pool");
});

test("saved recommendation order remains stable when the loaded page is revisited with changed weights", async () => {
  const { baselineWeights, weightedNgoOrder, restoreNgoOrder } = await models();
  const candidates = [
    { id: 11, causaPrincipal: "ANIMAIS" },
    { id: 12, causaPrincipal: "SAUDE" },
    { id: 13, causaPrincipal: "EDUCACAO" }
  ];
  const initial = weightedNgoOrder(candidates, baselineWeights(["animais"]), () => 0.91);
  const savedIds = initial.map(item => String(item.id));
  const resumed = restoreNgoOrder(candidates, savedIds);
  assert.deepEqual(resumed.map(item => item.id), initial.map(item => item.id));
  assert.deepEqual(restoreNgoOrder(candidates.slice(0, 2), savedIds).map(item => item.id),
    initial.filter(item => item.id !== 13).map(item => item.id));
});

test("all membership roles exclude the NGO from visit learning; public eligibility is required", async () => {
  const { profileMembershipExcludesNgo, eligibleVisitTarget } = await import("../../public/components/recommendation-policy.js");
  for (const cargo of ["CRIADOR", "ADMINISTRADOR", "EDITOR", "MEMBRO"]) {
    assert.equal(profileMembershipExcludesNgo([{ id: 44, cargo }], 44), true);
  }
  assert.equal(profileMembershipExcludesNgo([{ id: 45, cargo: "MEMBRO" }], 44), false);
  assert.equal(eligibleVisitTarget({ id: 44, status: "ATIVA", arquivada: false, publicEligible: true }), true);
  assert.equal(eligibleVisitTarget({ id: 44, status: "INATIVA", publicEligible: true }), false);
  assert.equal(eligibleVisitTarget({ id: 44, status: "ATIVA", publicEligible: false }), false);
});

import { CAUSE_IDS, causeCategoriesForNgo, normalizeCausePreferences } from "./cause-taxonomy.js";

export const RECOMMENDATION_TOTAL = 100;
export const RECOMMENDATION_FLOOR = 2;
export const VISIT_GAIN = 2;

export function normalizeWeights(weights, categories = CAUSE_IDS) {
  const keys = [...new Set(categories)];
  if (!keys.length) return {};
  const raw = keys.map(key => Math.max(0, Number(weights?.[key]) || 0));
  const sum = raw.reduce((total, value) => total + value, 0);
  const scores = sum > 0 ? raw : raw.map(() => 1);
  const output = Object.fromEntries(keys.map((key, index) => [key, RECOMMENDATION_TOTAL * scores[index] / scores.reduce((total, value) => total + value, 0)]));
  let fixed = new Set();
  for (;;) {
    const below = keys.filter(key => !fixed.has(key) && output[key] < RECOMMENDATION_FLOOR);
    if (!below.length) break;
    below.forEach(key => { output[key] = RECOMMENDATION_FLOOR; fixed.add(key); });
    const remainingKeys = keys.filter(key => !fixed.has(key));
    const remainingRaw = remainingKeys.reduce((total, key) => total + scores[keys.indexOf(key)], 0);
    const remainingWeight = RECOMMENDATION_TOTAL - fixed.size * RECOMMENDATION_FLOOR;
    if (!remainingKeys.length || remainingRaw <= 0) break;
    remainingKeys.forEach(key => { output[key] = remainingWeight * scores[keys.indexOf(key)] / remainingRaw; });
  }
  // Remove floating point drift without crossing the per-cause floor.
  const drift = RECOMMENDATION_TOTAL - Object.values(output).reduce((total, value) => total + value, 0);
  if (Math.abs(drift) > 1e-10) {
    const receiver = keys.find(key => output[key] + drift >= RECOMMENDATION_FLOOR) || keys[0];
    output[receiver] += drift;
  }
  return output;
}

export function baselineWeights(selectedCauses = []) {
  const selected = new Set(normalizeCausePreferences(selectedCauses));
  return normalizeWeights(Object.fromEntries(CAUSE_IDS.map(id => [id, selected.has(id) ? 2 : 1])));
}

export function transferVisitWeight(weights, ngoCauses) {
  const current = normalizeWeights(weights);
  const targets = [...new Set(normalizeCausePreferences(ngoCauses))];
  if (!targets.length) return current;
  const targetSet = new Set(targets);
  const donors = CAUSE_IDS.filter(id => !targetSet.has(id));
  const capacities = donors.map(id => Math.max(0, current[id] - RECOMMENDATION_FLOOR));
  const capacity = capacities.reduce((sum, value) => sum + value, 0);
  const transfer = Math.min(VISIT_GAIN, capacity);
  if (transfer <= 1e-10) return current;
  const next = { ...current };
  donors.forEach((id, index) => { next[id] -= transfer * capacities[index] / capacity; });
  targets.forEach(id => { next[id] += transfer / targets.length; });
  return normalizeWeights(next);
}

function randomUnit(random) {
  const value = Number(random());
  return Number.isFinite(value) ? Math.min(0.999999999999, Math.max(0, value)) : 0;
}

function pickWeighted(entries, weights, random) {
  const total = entries.reduce((sum, entry) => sum + Math.max(0, weights[entry] || 0), 0);
  if (!(total > 0)) return entries[Math.floor(randomUnit(random) * entries.length)];
  let point = randomUnit(random) * total;
  for (const entry of entries) {
    point -= Math.max(0, weights[entry] || 0);
    if (point < 0) return entry;
  }
  return entries.at(-1);
}

/** Weighted cause draw, then uniform NGO draw within that cause, without duplicate cards. */
export function weightedNgoOrder(ngos, weights = baselineWeights(), random = Math.random) {
  weights = normalizeWeights(weights);
  const candidates = [...new Map((Array.isArray(ngos) ? ngos : [])
    .filter(ngo => ngo && ngo.id != null).map(ngo => [String(ngo.id), ngo])).values()];
  const pools = new Map(CAUSE_IDS.map(id => [id, []]));
  const uncategorized = [];
  for (const ngo of candidates) {
    const categories = causeCategoriesForNgo(ngo);
    if (!categories.length) { uncategorized.push(ngo); continue; }
    for (const cause of categories) pools.get(cause)?.push(ngo);
  }
  const remaining = new Map([...pools].map(([id, items]) => [id, [...items]]));
  const output = [];
  const selected = new Set();
  while (selected.size < candidates.length) {
    const availableCauses = CAUSE_IDS.filter(id => (remaining.get(id) || []).some(ngo => !selected.has(String(ngo.id))));
    const hasUncategorized = uncategorized.some(ngo => !selected.has(String(ngo.id)));
    const causeOptions = hasUncategorized ? [...availableCauses, "__UNCATEGORIZED__"] : availableCauses;
    if (!causeOptions.length) break;
    const causeWeights = { ...weights, __UNCATEGORIZED__: RECOMMENDATION_TOTAL / CAUSE_IDS.length };
    const cause = pickWeighted(causeOptions, causeWeights, random);
    const pool = cause === "__UNCATEGORIZED__" ? uncategorized : remaining.get(cause);
    const available = pool.filter(ngo => !selected.has(String(ngo.id)));
    if (!available.length) continue;
    const ngo = available[Math.floor(randomUnit(random) * available.length)];
    selected.add(String(ngo.id));
    output.push(ngo);
  }
  // Defensive fallback retains every public candidate even with malformed category data.
  for (const ngo of candidates) if (!selected.has(String(ngo.id))) output.push(ngo);
  return output;
}

export function restoreNgoOrder(ngos, savedIds) {
  const records = [...new Map((Array.isArray(ngos) ? ngos : []).filter(item => item?.id != null)
    .map(item => [String(item.id), item])).values()];
  if (!Array.isArray(savedIds)) return records;
  const rank = new Map(savedIds.map((id, index) => [String(id), index]));
  return records.sort((a, b) => (rank.get(String(a.id)) ?? Number.MAX_SAFE_INTEGER)
    - (rank.get(String(b.id)) ?? Number.MAX_SAFE_INTEGER));
}

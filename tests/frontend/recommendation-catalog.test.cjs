const test = require("node:test");
const assert = require("node:assert/strict");
const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");

const read = path => readFile(resolve(__dirname, "../..", path), "utf8");

test("homepage and Para você scan the complete eligible NGO page set before weighted ordering", async () => {
  const home = await read("public/components/home-showcases.js");
  const catalog = await read("public/components/catalogo.js");
  assert.match(home, /for \(let page = 1; page < Number\(first\.totalPages\); page \+= 1\)/);
  assert.match(home, /unique\.length !== Number\(first\.totalElements\)/);
  assert.match(home, /await personalizationReady\(\);\s*return orderNgoRecommendations\(unique\)/);
  assert.match(catalog, /new Option\("Para você", "para-voce"\)/);
  assert.match(catalog, /fullRecommendationPool \? sourceTotalPages/);
  assert.match(catalog, /sourceItems\.length !== sourceTotalElements/);
  assert.match(catalog, /if \(config\.kind === "ongs" && order === "para-voce"\)/);
  assert.match(catalog, /causeCategoriesForNgo\(item\)\.includes\(selected\)/);
});

test("catalog keeps an order snapshot per history entry and stable page URL state", async () => {
  const catalog = await read("public/components/catalogo.js");
  assert.match(catalog, /sessionStorage\.setItem\(`\$\{ORDER_KEY_PREFIX\}\$\{currentRecommendationEntry\}`/);
  assert.match(catalog, /paRecommendationKey/);
  assert.match(catalog, /restoreNgoOrder\(resultItems, savedIds\)/);
  assert.match(catalog, /url\.searchParams\.set\("page", String\(resultPage\)\)/);
  assert.match(catalog, /event\.state\?\.paRecommendationKey/);
  assert.match(catalog, /updateUrl \? null : readRecommendationOrder/);
});

test("the profile records a visit only after the successful public-eligible response and separate membership check", async () => {
  const profile = await read("public/components/ong-profile.js");
  const profileAccess = await read("public/components/ong-profile-access.js");
  const engine = await read("public/components/recommendation-personalization.js");
  assert.match(profile, /const acesso = await carregarPerfilOngAcessivel\(id/);
  assert.match(profile, /if \(acesso\.origem === "publica"\) void recordEligibleProfileVisit\(\{ \.\.\.osc, publicEligible: true \}\)/);
  assert.match(profile, /void carregarPermissoes\(id, acesso\.organizacoes\)/);
  assert.match(profileAccess, /perfilPublicamenteDisponivel\(perfilPublico\)/);
  assert.match(profileAccess, /if \(vinculo\?\.cargo\)/);
  assert.match(profileAccess, /if \(!await podeLerPerfilPrivadoComoAdmin\(\)\) return \{ naoEncontrado: true, organizacoes \}/);
  assert.match(engine, /await resolveMemberships\(\)/);
  assert.match(engine, /if \(!membership\.ok \|\| profileMembershipExcludesNgo/);
  assert.match(engine, /alreadyLearnedVisit\(state\.visits, ngoId, day\)/);
  assert.match(engine, /VISIT_TTL_DAYS = 90/);
});

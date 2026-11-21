const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

async function locationModule() {
    const source = await readFile(resolve(__dirname, "../../public/components/catalog-location.js"), "utf8");
    return import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
}

test("normalizes city, city/state, full state names, and CEP", async () => {
    const { parseCatalogLocation } = await locationModule();
    assert.deepEqual(parseCatalogLocation("Praia Grande"), { ok: true, type: "city", city: "Praia Grande", state: "" });
    assert.deepEqual(parseCatalogLocation("Praia Grande, SP"), { ok: true, type: "city", city: "Praia Grande", state: "SP" });
    assert.deepEqual(parseCatalogLocation("Praia Grande SP"), { ok: true, type: "city", city: "Praia Grande", state: "SP" });
    assert.deepEqual(parseCatalogLocation("Santos São Paulo"), { ok: true, type: "city", city: "Santos", state: "SP" });
    assert.deepEqual(parseCatalogLocation("11700-000"), { ok: true, type: "cep", cep: "11700000", city: "", state: "" });
});

test("validates selected state and rejects malformed city and CEP", async () => {
    const { parseCatalogLocation } = await locationModule();
    assert.equal(parseCatalogLocation("Praia Grande", "SP").state, "SP");
    assert.equal(parseCatalogLocation("Praia Grande RJ", "SP").ok, false);
    assert.equal(parseCatalogLocation("11700-00").ok, false);
    assert.equal(parseCatalogLocation(" ").ok, false);
    assert.equal(parseCatalogLocation("Praia\nGrande").ok, false);
});

test("validates coordinates and computes geographic distance", async () => {
    const { validCatalogCoordinates, distanceKm } = await locationModule();
    const origin = validCatalogCoordinates(-23.99, -46.4);
    const nearby = validCatalogCoordinates(-23.99, -46.4);
    assert.deepEqual(origin, { latitude: -23.99, longitude: -46.4 });
    assert.equal(distanceKm(origin, nearby), 0);
    assert.equal(validCatalogCoordinates(999, -46.4), null);
    assert.equal(validCatalogCoordinates(NaN, -46.4), null);
});

test("manual location and browser location apply the same radius across municipalities", async () => {
    const { distanceKm, matchesCatalogLocation } = await locationModule();
    const manualPoint = { latitude: -24, longitude: -46.4 };
    const ngo = { latitude: "-24.05", longitude: "-46.4" };
    const nearbyDistance = distanceKm(manualPoint, ngo);
    assert.ok(nearbyDistance < 10);
    assert.equal(matchesCatalogLocation({
        kind: "ongs", local: "praia grande sp", place: "sao vicente sao paulo brasil",
        origin: manualPoint, ...ngo, radiusKm: "10"
    }), true);
    assert.equal(matchesCatalogLocation({
        kind: "ongs", origin: { ...manualPoint }, ...ngo, radiusKm: "10"
    }), true);
    assert.equal(matchesCatalogLocation({
        kind: "ongs", local: "praia grande sp", place: "sao vicente sao paulo brasil",
        origin: manualPoint, latitude: -24.11, longitude: -46.4, radiusKm: "10"
    }), false);
});

test("geographic filters reject invalid or missing coordinates and invalid radii", async () => {
    const { matchesCatalogLocation } = await locationModule();
    const filter = { kind: "ongs", origin: { latitude: -24, longitude: -46.4 }, radiusKm: 10 };
    assert.equal(matchesCatalogLocation({ ...filter, latitude: "invalid", longitude: -46.4 }), false);
    assert.equal(matchesCatalogLocation({ ...filter, latitude: 0, longitude: undefined }), false);
    assert.equal(matchesCatalogLocation({ ...filter, latitude: -24, longitude: -46.4, radiusKm: "invalid" }), false);
    assert.equal(matchesCatalogLocation({ ...filter, latitude: -24, longitude: -46.4, radiusKm: -1 }), false);
});

test("manual volunteer locality matching and remote opportunities retain their existing behavior", async () => {
    const { matchesCatalogLocation } = await locationModule();
    const filter = { kind: "volunteer", local: "praia grande sp", place: "sao vicente sp" };
    assert.equal(matchesCatalogLocation(filter), false);
    assert.equal(matchesCatalogLocation({ ...filter, remote: true }), true);
});

test("manual location URL parameters resolve city, state, and radius before filtering", async () => {
    const { parseCatalogLocation } = await locationModule();
    const script = await readFile(resolve(__dirname, "../../public/components/catalogo.js"), "utf8");
    const params = new URLSearchParams("local=Praia+Grande&estado=SP&distancia=10");
    assert.deepEqual(parseCatalogLocation(params.get("local"), params.get("estado")), {
        ok: true, type: "city", city: "Praia Grande", state: "SP"
    });
    assert.match(script, /matchesCatalogLocation\(\{/);
    assert.match(script, /params\.get\("distancia"\)\s*\|\|\s*"10"/);
    assert.match(script, /await restaurarLocalizacaoDaUrl\(\)/);
});

test("geocoding failures leave the active location untouched and catalog loading awaits URL restoration", async () => {
    const script = await readFile(resolve(__dirname, "../../public/components/catalogo.js"), "utf8");
    const failureBranch = script.indexOf("if (!resolved.ok)");
    const locationAssignment = script.indexOf("locationPoint = resolved.coordinates");
    assert.ok(failureBranch >= 0 && locationAssignment > failureBranch);
    assert.match(script.slice(failureBranch, locationAssignment), /return false;/);
    assert.match(script, /if \(version !== requestVersion\) return;[\s\S]*?await restaurarLocalizacaoDaUrl\(\)/);
});

test("geocoding errors distinguish missing route, ambiguous result, and provider failure", async () => {
    const { geocodingFailureMessage } = await locationModule();
    assert.match(geocodingFailureMessage(404), /backend|rota/i);
    assert.match(geocodingFailureMessage(409, "Informe SP"), /Informe SP/);
    assert.match(geocodingFailureMessage(422), /não foi localizado/i);
    assert.match(geocodingFailureMessage(429), /ocupado/i);
    assert.match(geocodingFailureMessage(503), /resultados atuais foram mantidos/i);
});

test("catalog uses the registered profile CEP only on request and never hides loaded results on geocoding failure", async () => {
    const script = await readFile(resolve(__dirname, "../../public/components/catalogo.js"), "utf8");
    const html = await readFile(resolve(__dirname, "../../public/pages/catalogo.html"), "utf8");
    assert.match(script, /fetchAutenticado\("\/usuario\/perfil"\)/);
    assert.match(script, /\/geocoding\/location/);
    assert.match(script, /A consulta expirou\. Seus resultados atuais foram mantidos\./);
    assert.match(script, /resolved\.ok\)\s*\{/);
    assert.match(script, /return false;/);
    assert.match(html, /id="catalogo-local-toggle"[^>]+aria-expanded="false"/);
    assert.match(html, /id="catalogo-estado"/);
});

test("filter controls remain compact and category-specific advanced filters stay collapsed", async () => {
    const html = await readFile(resolve(__dirname, "../../public/pages/catalogo.html"), "utf8");
    const script = await readFile(resolve(__dirname, "../../public/components/catalogo.js"), "utf8");
    const css = await readFile(resolve(__dirname, "../../public/styles/catalogo.css"), "utf8");
    assert.match(html, /class="catalogo-search-row"/);
    assert.match(html, /<details id="catalogo-avancados"/);
    assert.match(script, /catalogo-vagas-label.*hidden = false/);
    assert.match(script, /catalogo-ods-label.*hidden = false/);
    assert.match(css, /@media\(max-width:600px\)/);
    assert.match(css, /overflow-x:auto/);
});

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

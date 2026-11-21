const test = require("node:test");
const assert = require("node:assert/strict");
const { readFile } = require("node:fs/promises");
const { resolve } = require("node:path");

class MemoryStorage {
  constructor(blocked = false) { this.values = new Map(); this.blocked = blocked; }
  getItem(key) { if (this.blocked) throw new Error("storage denied"); return this.values.has(key) ? this.values.get(key) : null; }
  setItem(key, value) { if (this.blocked) throw new Error("storage denied"); this.values.set(String(key), String(value)); }
  removeItem(key) { if (this.blocked) throw new Error("storage denied"); this.values.delete(String(key)); }
  clear() { if (this.blocked) throw new Error("storage denied"); this.values.clear(); }
  key(index) { return [...this.values.keys()][index] ?? null; }
  get length() { return this.values.size; }
}

function response(body, status = 200) {
  return { ok: status >= 200 && status < 300, status, async json() { return body; } };
}

test("consent, explicit causes, membership exclusion, per-day dedupe, no event upload, logout and account switching", async t => {
  const previous = { window: global.window, localStorage: global.localStorage, sessionStorage: global.sessionStorage,
    fetch: global.fetch, CustomEvent: global.CustomEvent };
  const listeners = new Map();
  const local = new MemoryStorage();
  const session = new MemoryStorage();
  let activeUser = "42";
  const requests = [];
  global.CustomEvent = class CustomEvent { constructor(type, init = {}) { this.type = type; this.detail = init.detail; } };
  global.window = {
    location: { hostname: "localhost", origin: "http://localhost:8080" },
    addEventListener(type, listener) { listeners.set(type, listener); },
    dispatchEvent(event) { listeners.get(event.type)?.(event); return true; }
  };
  global.localStorage = local; global.sessionStorage = session;
  global.fetch = async (input, options = {}) => {
    const url = new URL(String(input), "http://localhost:8080");
    requests.push({ path: url.pathname, method: String(options.method || "GET").toUpperCase(), body: options.body || null });
    if (url.pathname === "/usuario/sessao-atual") return response({ autenticado: true, usuario: activeUser });
    if (url.pathname === "/usuario/preferencias") return response({ causasPersistidas: true, tiposOng: ["animais"] });
    if (url.pathname === "/osc/minhas") return response([{ id: 77, cargo: "EDITOR" }]);
    return response({}, 404);
  };
  const module = await import(`../../public/components/recommendation-personalization.js?state-test=${Date.now()}`);
  const { pararRenovacaoPeriodica } = await import("../../public/components/auth.js");
  t.after(() => {
    pararRenovacaoPeriodica();
    for (const [key, value] of Object.entries(previous)) {
      if (value === undefined) delete global[key]; else global[key] = value;
    }
  });

  const initial = await module.ready();
  assert.equal(initial.identity, "42");
  assert.deepEqual(initial.causes, ["animais"]);
  assert.equal(module.isPersonalizationEnabled(), false);

  module.acceptPersonalization();
  assert.equal(await module.recordEligibleProfileVisit({ id: 77, status: "ATIVA", arquivada: false, publicEligible: true, causaPrincipal: "ANIMAIS" }), false,
    "membership in any role suppresses learning");
  const visit = { id: 78, status: "ATIVA", arquivada: false, publicEligible: true, causas: ["ANIMAIS", "SAUDE"] };
  assert.equal(await module.recordEligibleProfileVisit(visit), true);
  assert.equal(await module.recordEligibleProfileVisit(visit), false, "the same NGO counts only once on the local calendar day");
  assert.equal(await module.recordEligibleProfileVisit({ ...visit, id: 79, status: "INATIVA" }), false);
  const learnedKey = "portoAjudaRecomendacoes:v1:42";
  const learned = JSON.parse(local.getItem(learnedKey));
  assert.equal(learned.visits.length, 1);
  assert.equal(learned.visits[0].ngoId, 78);
  assert.ok(Math.abs(Object.values(learned.weights).reduce((sum, value) => sum + value, 0) - 100) < 1e-9);
  assert.ok(requests.every(request => request.method === "GET" && !request.body), "behavioral history/weights are never sent to the backend");
  assert.ok(requests.every(request => !/analytics|visit|event|recommendation/i.test(request.path)), "no analytics event endpoint is called");

  activeUser = "84";
  window.dispatchEvent(new CustomEvent("portoajuda:sessao-autenticada"));
  const switched = await module.ready();
  assert.equal(switched.identity, "84");
  assert.equal(local.getItem(learnedKey), null, "account switching deletes the previous account's behavioral state");
  assert.equal(local.getItem("portoAjudaRecomendacoes:v1:84"), null,
    "the new account starts from its explicit profile baseline, without importing old/anonymous visit history");

  window.dispatchEvent(new CustomEvent("portoajuda:sessao-anonima"));
  assert.equal(local.getItem("portoAjudaRecomendacoes:v1:84"), null, "logout deletes learned data");
  assert.ok(Math.abs(Object.values(module.getWeights()).reduce((sum, value) => sum + value, 0) - 100) < 1e-9,
    "logout returns the current browser to an anonymous baseline without carrying account history");
});

test("consent and recommendations degrade gracefully when browser storage is denied", async t => {
  const previous = { window: global.window, localStorage: global.localStorage, sessionStorage: global.sessionStorage,
    fetch: global.fetch, CustomEvent: global.CustomEvent };
  const listeners = new Map();
  global.CustomEvent = class CustomEvent { constructor(type, init = {}) { this.type = type; this.detail = init.detail; } };
  global.window = { location: { hostname: "localhost", origin: "http://localhost:8080" },
    addEventListener(type, listener) { listeners.set(type, listener); }, dispatchEvent(event) { listeners.get(event.type)?.(event); return true; } };
  global.localStorage = new MemoryStorage(true); global.sessionStorage = new MemoryStorage(true);
  global.fetch = async input => new URL(String(input), "http://localhost:8080").pathname === "/usuario/sessao-atual"
    ? response({ autenticado: false, usuario: "anonymousUser" }) : response({}, 401);
  const module = await import(`../../public/components/recommendation-personalization.js?storage-test=${Date.now()}`);
  const { pararRenovacaoPeriodica } = await import("../../public/components/auth.js");
  t.after(() => {
    pararRenovacaoPeriodica();
    for (const [key, value] of Object.entries(previous)) {
      if (value === undefined) delete global[key]; else global[key] = value;
    }
  });
  await module.ready();
  module.acceptPersonalization();
  assert.equal(await module.recordEligibleProfileVisit({ id: 2, status: "ATIVA", publicEligible: true, causas: ["EDUCACAO"] }), true,
    "in-memory operation remains available while persistence is unavailable");
  assert.equal(Object.values(module.getWeights()).length, 14);
  module.rejectPersonalization();
  assert.equal(module.isPersonalizationEnabled(), false);
});

test("the fixed navigation cannot cover the visible personalization consent controls", async () => {
  const css = await readFile(resolve(__dirname, "../../public/styles/personalization.css"), "utf8");
  assert.match(css, /\.pa-personalization-consent\s*\{[^}]*position:\s*fixed;[^}]*top:\s*96px;[^}]*z-index:\s*10001/s);
  assert.match(css, /max-height:\s*calc\(100dvh - 110px\)/);
});

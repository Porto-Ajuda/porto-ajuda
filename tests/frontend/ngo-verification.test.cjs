const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

class Element {
  constructor(id = "") {
    this.id = id;
    this.hidden = true;
    this.disabled = false;
    this.value = "";
    this.checked = false;
    this.files = [];
    this.textContent = "";
    this.dataset = {};
    this.children = [];
    this.listeners = new Map();
    this.button = { disabled: false };
    this.input = { value: "", files: [], setCustomValidity() { }, reportValidity() { return true; } };
  }
  addEventListener(type, handler) { this.listeners.set(type, handler); }
  querySelector(selector) {
    if (selector === "button") return this.button;
    if (selector === 'input[type="file"]') return this.input;
    return new Element(selector);
  }
  querySelectorAll() { return []; }
  append(...items) { this.children.push(...items); }
  replaceChildren(...items) { this.children = [...items]; }
  reportValidity() { return true; }
  setCustomValidity() { }
}
function response(body, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

test("perfil do solicitante permite iniciar verificação e envia o vínculo/consentimento ao endpoint real", async () => {
  const sourcePath = path.join(__dirname, "../../public/components/verificacao-representante.js");
  const source = fs.readFileSync(sourcePath, "utf8").replace(/^import .*;\s*$/gm, "");
  const ids = [
    "verificacao-representante-aplicacao", "verificacao-representante-status", "verificacao-representante-feedback",
    "verificacao-impacto-operacional",
    "verificacao-representante-arquivos", "form-evidencia-representacao", "form-evidencia-delegacao",
    "solicitar-revisao-verificacao", "form-iniciar-verificacao", "tipo-representacao-verificacao",
    "confirmar-impacto-verificacao", "form-reaplicar-verificacao", "tipo-representacao-reaplicacao",
    "confirmar-reaplicacao-verificacao", "painel-dados"
  ];
  const elements = Object.fromEntries(ids.map(id => [id, new Element(id)]));
  elements["painel-dados"].hidden = false;
  const requests = [];
  const document = {
    getElementById(id) { return elements[id] || null; },
    createElement(tag) { return new Element(tag); }
  };
  const initial = {
    applicationId: null, oscId: 50, status: "NOT_STARTED", representationType: null,
    submissionVersion: 0, submittedAt: null, canStartApplication: true,
    reviewFeedback: "A adesão não altera o funcionamento da ONG.", evidence: [], legacyExempt: true,
    applicationNumber: 0, maximumApplications: 3, canReapply: false
  };
  const started = { ...initial, applicationId: 71, status: "PENDING", representationType: "AUTHORIZED_DELEGATE", canStartApplication: false, reviewFeedback: null };
  const fetchAutenticado = async (url, options = {}) => {
    requests.push({ url, options });
    if (options.method === "POST") return response(started, 201);
    return response(initial);
  };
  const context = {
    document,
    window: { location: { search: "?id=50", origin: "http://localhost:8080" }, open: () => null },
    URLSearchParams,
    getAPI_URL: () => "http://localhost:8080",
    fetchAutenticado,
    fetch: async () => response({ enabled: true })
  };
  vm.runInNewContext(source, context, { filename: sourcePath });
  await new Promise(resolve => setTimeout(resolve, 0));

  assert.equal(elements["verificacao-representante-aplicacao"].hidden, false);
  assert.equal(elements["form-iniciar-verificacao"].hidden, false);
  assert.match(elements["verificacao-representante-status"].textContent, /Verificação ainda não iniciada/);
  assert.match(elements["verificacao-impacto-operacional"].textContent, /não mudam a visibilidade/);

  elements["tipo-representacao-verificacao"].value = "AUTHORIZED_DELEGATE";
  elements["confirmar-impacto-verificacao"].checked = true;
  await elements["form-iniciar-verificacao"].listeners.get("submit")({ preventDefault() { } });

  const post = requests.find(item => item.options.method === "POST");
  assert.ok(post);
  assert.equal(post.url, "/osc/50/verificacao/solicitar");
  assert.deepEqual(JSON.parse(post.options.body), {
    representationType: "AUTHORIZED_DELEGATE",
    confirmPendingAndVisibilityEffect: true
  });
  assert.equal(elements["form-iniciar-verificacao"].hidden, true);
  assert.equal(elements["form-evidencia-representacao"].hidden, false);
  assert.equal(elements["form-evidencia-delegacao"].hidden, false);
  assert.match(elements["verificacao-representante-feedback"].textContent, /Solicitação iniciada/);
  assert.match(elements["verificacao-impacto-operacional"].textContent, /não mudam a visibilidade/);
});

test("reapresentação após recusa usa ação e endpoint separados, sem reutilizar a solicitação anterior", async () => {
  const sourcePath = path.join(__dirname, "../../public/components/verificacao-representante.js");
  const source = fs.readFileSync(sourcePath, "utf8").replace(/^import .*;\s*$/gm, "");
  const ids = [
    "verificacao-representante-aplicacao", "verificacao-representante-status", "verificacao-representante-feedback",
    "verificacao-impacto-operacional", "verificacao-representante-arquivos", "form-evidencia-representacao",
    "form-evidencia-delegacao", "solicitar-revisao-verificacao", "form-iniciar-verificacao",
    "tipo-representacao-verificacao", "confirmar-impacto-verificacao", "form-reaplicar-verificacao",
    "tipo-representacao-reaplicacao", "confirmar-reaplicacao-verificacao", "painel-dados"
  ];
  const elements = Object.fromEntries(ids.map(id => [id, new Element(id)]));
  elements["painel-dados"].hidden = false;
  const rejected = {
    applicationId: 71, oscId: 50, status: "REJECTED", representationType: "LEGAL_REPRESENTATIVE",
    submissionVersion: 1, submittedAt: "2026-10-01T00:00:00Z", canStartApplication: false,
    reviewFeedback: "A documentação não demonstrou poderes vigentes.", evidence: [], legacyExempt: true,
    applicationNumber: 1, maximumApplications: 3, canReapply: true
  };
  const newCycle = { ...rejected, applicationId: 72, status: "PENDING", representationType: "AUTHORIZED_DELEGATE",
    submissionVersion: 0, submittedAt: null, applicationNumber: 2, canReapply: false, reviewFeedback: null };
  const requests = [];
  let current = rejected;
  const document = {
    getElementById(id) { return elements[id] || null; },
    createElement(tag) { return new Element(tag); }
  };
  const fetchAutenticado = async (url, options = {}) => {
    requests.push({ url, options });
    if (options.method === "POST") { current = newCycle; return response(newCycle, 201); }
    return response(current);
  };
  const context = {
    document,
    window: { location: { search: "?id=50", origin: "http://localhost:8080" }, open: () => null },
    URLSearchParams, getAPI_URL: () => "http://localhost:8080", fetchAutenticado,
    fetch: async () => response({ enabled: true })
  };
  vm.runInNewContext(source, context, { filename: sourcePath });
  await new Promise(resolve => setTimeout(resolve, 0));

  assert.equal(elements["form-reaplicar-verificacao"].hidden, false);
  elements["tipo-representacao-reaplicacao"].value = "AUTHORIZED_DELEGATE";
  elements["confirmar-reaplicacao-verificacao"].checked = true;
  await elements["form-reaplicar-verificacao"].listeners.get("submit")({ preventDefault() { } });

  const post = requests.find(item => item.options.method === "POST");
  assert.equal(post.url, "/osc/50/verificacao/reaplicar");
  assert.deepEqual(JSON.parse(post.options.body), {
    representationType: "AUTHORIZED_DELEGATE", confirmNewApplication: true
  });
  assert.equal(rejected.status, "REJECTED");
  assert.equal(rejected.applicationId, 71);
  assert.equal(elements["form-reaplicar-verificacao"].hidden, true);
  assert.match(elements["verificacao-representante-feedback"].textContent, /documentos anteriores foram preservados/);
});

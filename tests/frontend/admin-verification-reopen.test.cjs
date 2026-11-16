const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

class Element {
  constructor(tag = "div") {
    this.tagName = tag;
    this.hidden = false;
    this.disabled = false;
    this.value = "";
    this.textContent = "";
    this.children = [];
    this.listeners = new Map();
    this.attributes = new Map();
  }
  addEventListener(type, handler) { this.listeners.set(type, handler); }
  append(...items) { this.children.push(...items); }
  replaceChildren(...items) { this.children = [...items]; }
  get childElementCount() { return this.children.length; }
  setAttribute(name, value) { this.attributes.set(name, value); }
  showModal() { this.open = true; }
  close() { this.open = false; }
  reportValidity() { return true; }
  setCustomValidity(value) { this.customValidity = value; }
  querySelector() { return null; }
}

function response(body, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

test("reabertura administrativa rejeitada exige justificativa e usa endpoint auditado", async () => {
  const sourcePath = path.join(__dirname, "../../public/components/admin-verification.js");
  const source = fs.readFileSync(sourcePath, "utf8").replace(/^import .*;\s*$/gm, "").replace(/^export /gm, "");
  const ids = [
    "status-verificacao-representantes", "lista-verificacao-representantes", "filtro-verificacao-representantes",
    "atualizar-verificacoes-representantes", "verificacoes-representantes-anterior",
    "verificacoes-representantes-proxima", "verificacoes-representantes-pagina",
    "dialogo-verificacao-representante", "descricao-dialogo-verificacao-representante",
    "documentos-verificacao-representante", "analise-ia-verificacao", "conteudo-analise-ia-verificacao",
    "iniciar-revisao-verificacao", "formulario-revisao-verificacao",
    "decisao-verificacao-representante", "campo-decisao-verificacao", "justificativa-verificacao-representante",
    "rotulo-justificativa-verificacao", "feedback-verificacao-representante", "cancelar-revisao-verificacao",
    "confirmar-revisao-verificacao", "reabrir-verificacao"
  ];
  const elements = Object.fromEntries(ids.map(id => [id, new Element(id)]));
  elements["dialogo-verificacao-representante"].showModal = function () { this.open = true; };
  elements["dialogo-verificacao-representante"].close = function () { this.open = false; };
  const document = {
    getElementById(id) { return elements[id] || null; },
    createElement(tag) { return new Element(tag); }
  };
  const queue = { items: [{ applicationId: 44, oscId: 8, organizationName: "ONG sintética", status: "REJECTED",
    representationType: "LEGAL_REPRESENTATIVE", currentVersion: 4, submittedAt: "2026-10-01T00:00:00Z" }], totalElements: 1 };
  const rejected = { applicationId: 44, oscId: 8, organizationName: "ONG sintética", status: "REJECTED",
    representationType: "LEGAL_REPRESENTATIVE", submissionVersion: 4, evidence: [], aiAnalysis: {
      status: "COMPLETED", conclusion: "INCONCLUSIVO", provider: "gemini", model: "gemini-3.1-flash-lite",
      inputTokens: 800, outputTokens: 200,
      fields: [{ field: "CNPJ", status: "ENCONTRADO", references: [{ evidenceId: 77, page: 2 }] }],
      discrepancies: [{ code: "PODERES_NAO_COMPROVADOS", severity: "ALTA",
        references: [{ evidenceId: 77, page: 2 }] }]
    } };
  const reopened = { ...rejected, status: "INFORMATION_REQUESTED" };
  const requests = [];
  const fetchImpl = async (url, options = {}) => {
    requests.push({ url, options });
    if (url.endsWith("/usuario/csrf")) return response({ headerName: "X-CSRF-TOKEN", token: "synthetic-csrf" });
    if (options.method === "POST") return response(reopened);
    if (url.includes("/admin/ngo-verifications/44")) return response(rejected);
    return response(queue);
  };
  const context = {
    document,
    window: { location: { origin: "http://localhost:8080" } },
    URL,
    URLSearchParams,
    Intl,
    showAdminDialog: dialog => dialog.showModal()
  };
  vm.runInNewContext(source, context, { filename: sourcePath });
  const initialize = context.initializeAdminVerification;
  const api = initialize({ apiUrl: "http://localhost:8080", fetchImpl });
  assert.equal(await api.load(0), true);
  const openButton = elements["lista-verificacao-representantes"].children[0].children.at(-1);
  await openButton.listeners.get("click")();
  await new Promise(resolve => setTimeout(resolve, 0));

  assert.equal(elements["reabrir-verificacao"].hidden, false);
  assert.equal(elements["analise-ia-verificacao"].hidden, false);
  const analysisText = (node) => [node.textContent || "", ...(node.children || []).map(analysisText)].join(" ");
  assert.match(analysisText(elements["conteudo-analise-ia-verificacao"]), /Resultado inconclusivo/);
  assert.match(analysisText(elements["conteudo-analise-ia-verificacao"]), /CNPJ: Encontrado/);
  assert.match(analysisText(elements["conteudo-analise-ia-verificacao"]), /evidência 77, página 2/);
  assert.match(analysisText(elements["conteudo-analise-ia-verificacao"]), /Poderes não comprovados/);
  assert.equal(elements["campo-decisao-verificacao"].hidden, true);
  assert.equal(elements["justificativa-verificacao-representante"].disabled, false);
  await elements["reabrir-verificacao"].listeners.get("click")();
  assert.equal(requests.some(request => request.options.method === "POST"), false);

  elements["justificativa-verificacao-representante"].value = "Correção documental necessária.";
  await elements["reabrir-verificacao"].listeners.get("click")();
  await new Promise(resolve => setTimeout(resolve, 0));
  const post = requests.find(request => request.options.method === "POST");
  assert.equal(post.url, "http://localhost:8080/admin/ngo-verifications/44/reabrir");
  assert.deepEqual(JSON.parse(post.options.body), { justification: "Correção documental necessária." });
  assert.equal(elements["dialogo-verificacao-representante"].open, false);
});

test("resultado armazenado permanece visível sem disparar IA ao iniciar revisão humana", async () => {
  const sourcePath = path.join(__dirname, "../../public/components/admin-verification.js");
  const htmlPath = path.join(__dirname, "../../public/admin/index.html");
  const source = fs.readFileSync(sourcePath, "utf8").replace(/^import .*;\s*$/gm, "").replace(/^export /gm, "");
  const html = fs.readFileSync(htmlPath, "utf8");
  assert.match(html, /Iniciar revisão humana/);
  assert.match(html, /não dispara nem repete uma chamada ao provedor de IA/);

  const ids = [
    "status-verificacao-representantes", "lista-verificacao-representantes", "filtro-verificacao-representantes",
    "atualizar-verificacoes-representantes", "verificacoes-representantes-anterior",
    "verificacoes-representantes-proxima", "verificacoes-representantes-pagina",
    "dialogo-verificacao-representante", "descricao-dialogo-verificacao-representante",
    "documentos-verificacao-representante", "analise-ia-verificacao", "conteudo-analise-ia-verificacao",
    "iniciar-revisao-verificacao", "formulario-revisao-verificacao",
    "decisao-verificacao-representante", "campo-decisao-verificacao", "justificativa-verificacao-representante",
    "rotulo-justificativa-verificacao", "feedback-verificacao-representante", "cancelar-revisao-verificacao",
    "confirmar-revisao-verificacao", "reabrir-verificacao"
  ];
  const elements = Object.fromEntries(ids.map(id => [id, new Element(id)]));
  elements["dialogo-verificacao-representante"].showModal = function () { this.open = true; };
  elements["dialogo-verificacao-representante"].close = function () { this.open = false; };
  const document = {
    getElementById(id) { return elements[id] || null; },
    createElement(tag) { return new Element(tag); }
  };
  const analysis = {
    status: "COMPLETED", conclusion: "DIVERGENCIA", provider: "gemini", model: "gemini-3.1-flash-lite",
    inputTokens: 869, outputTokens: 534,
    identityComparison: "MATCHES_REGISTERED_DATA",
    delegationEvidenceChain: "CONSISTENT_DOCUMENTARY_CHAIN",
    apparentValidity: "APPARENTLY_CURRENT", expiryDate: "2027-01-01",
    fields: [{ field: "CNPJ", status: "ENCONTRADO", references: [{ evidenceId: 992, page: 1 }] }],
    discrepancies: []
  };
  const pending = {
    applicationId: 904, oscId: 209, organizationName: "ONG fictícia para teste", status: "PENDING",
    representationType: "LEGAL_REPRESENTATIVE", submissionVersion: 3, evidence: [],
    aiAnalysis: analysis, aiAnalysisProcessingEnabled: false, aiAnalysisResultsReadable: true
  };
  const underReview = { ...pending, status: "UNDER_REVIEW" };
  const requests = [];
  const fetchImpl = async (url, options = {}) => {
    requests.push({ url, options });
    if (url.endsWith("/usuario/csrf")) return response({ headerName: "X-CSRF-TOKEN", token: "synthetic-csrf" });
    if (options.method === "POST") return response(underReview);
    if (url.includes("/admin/ngo-verifications/904")) return response(pending);
    return response({ items: [{ applicationId: 904, oscId: 209, organizationName: pending.organizationName,
      status: "PENDING", representationType: "LEGAL_REPRESENTATIVE", submittedAt: "2026-11-14T00:00:00Z" }], totalElements: 1 });
  };
  const context = {
    document, URL, URLSearchParams, Intl,
    window: { location: { origin: "http://localhost:8080" } },
    showAdminDialog: dialog => dialog.showModal()
  };
  vm.runInNewContext(source, context, { filename: sourcePath });
  const api = context.initializeAdminVerification({ apiUrl: "http://localhost:8080", fetchImpl });
  assert.equal(await api.load(0), true);
  const open = elements["lista-verificacao-representantes"].children[0].children.at(-1);
  await open.listeners.get("click")();
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.match(elements["descricao-dialogo-verificacao-representante"].textContent, /versão documental 3/);
  const textOf = node => [node.textContent || "", ...(node.children || []).map(textOf)].join(" ");
  const summaryText = textOf(elements["conteudo-analise-ia-verificacao"]);
  assert.match(summaryText, /Resultado associado à versão enviada 3/);
  assert.match(summaryText, /Análise concluída/);
  assert.match(summaryText, /Compatível com nome civil\/social cadastrado \(não verificado\)/);
  assert.match(summaryText, /Cadeia documental consistente \(não verificada independentemente\)/);
  assert.match(summaryText, /Data documental aparentemente vigente \(não verificada\)/);
  assert.match(summaryText, /2027-01-01/);
  assert.match(summaryText, /evidência 992, página 1/);
  await elements["iniciar-revisao-verificacao"].listeners.get("click")();
  await new Promise(resolve => setTimeout(resolve, 0));
  assert.equal(requests.filter(item => item.options.method === "POST").length, 1);
  assert.equal(requests.some(item => /gemini|\/analysis|\/jobs/i.test(item.url)), false);
  assert.equal(elements["iniciar-revisao-verificacao"].hidden, true);
  assert.equal(elements["campo-decisao-verificacao"].hidden, false);
  assert.equal(elements["justificativa-verificacao-representante"].disabled, false);
  assert.equal(elements["confirmar-revisao-verificacao"].hidden, false);
  const postCount = requests.filter(item => item.options.method === "POST").length;
  await elements["formulario-revisao-verificacao"].listeners.get("submit")({ preventDefault() {} });
  assert.equal(requests.filter(item => item.options.method === "POST").length, postCount,
    "An empty decision/justification must not submit a decision.");
  assert.equal(elements["justificativa-verificacao-representante"].customValidity, "A justificativa é obrigatória.");
});

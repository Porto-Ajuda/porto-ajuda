import { getAPI_URL } from "./modoBack.js";
import { fetchAutenticado } from "./api.js";

const API = getAPI_URL().replace(/\/+$/, "");
const tab = document.getElementById("aba-dashboard");
const panel = document.getElementById("painel-dashboard");
const id = Number(new URLSearchParams(location.search).get("id"));
const $ = selector => panel?.querySelector(selector);
let loaded = false;
let loading = false;
let currentPeriod = "30";

tab?.addEventListener("click", () => { if (!loaded) void load(); });
panel?.querySelectorAll("[data-dashboard-period]").forEach(button => button.addEventListener("click", () => { currentPeriod = button.dataset.dashboardPeriod; void load(true); }));
panel?.querySelector("[data-dashboard-custom]")?.addEventListener("click", () => { currentPeriod = "custom"; void load(true); });
if (tab) new MutationObserver(() => {
  if (tab.getAttribute("aria-selected") === "true" && !loaded) void load();
}).observe(tab, { attributes: true, attributeFilter: ["aria-selected"] });
window.addEventListener("porto:volunteer-capacity-changed", event => {
  if (Number(event.detail?.oscId) !== id) return;
  loaded = false;
  if (tab?.getAttribute("aria-selected") === "true") void load(true);
});

async function load(refresh = false) {
  if (loading || !panel || !Number.isSafeInteger(id) || id < 1) return;
  loading = true;
  if (refresh) loaded = false;
  const status = $("#dashboard-status");
  const error = $("#dashboard-erro");
  const metrics = $("#dashboard-metricas");
  status.hidden = false; status.textContent = "Carregando indicadores…";
  error.hidden = true; metrics.setAttribute("aria-busy", "true");
  try {
    const query = new URLSearchParams();
    const today = new Date(); today.setHours(12, 0, 0, 0);
    const to = panel.querySelector("#dashboard-ate")?.value || today.toISOString().slice(0, 10);
    const fromField = panel.querySelector("#dashboard-de")?.value;
    if (currentPeriod === "30" || currentPeriod === "90") { const from = new Date(`${to}T12:00:00`); from.setDate(from.getDate() - Number(currentPeriod) + 1); query.set("de", from.toISOString().slice(0,10)); query.set("ate", to); }
    else if (currentPeriod === "all") { query.set("de", "2000-01-01"); query.set("ate", to); }
    else if (currentPeriod === "custom" && fromField) { query.set("de", fromField); query.set("ate", to); }
    else if (currentPeriod === "custom") throw new Error("Informe a data inicial para aplicar o período personalizado.");
    const response = await fetchAutenticado(`/osc/${id}/dashboard${query.size ? `?${query}` : ""}`, { headers: { Accept: "application/json" } });
    if (response.status === 401) throw new Error("Entre com uma conta autorizada desta organização.");
    if (response.status === 403) throw new Error("O dashboard está disponível somente para o criador e administradores desta ONG.");
    if (!response.ok) throw new Error("Não foi possível carregar o dashboard. Tente novamente.");
    const data = await response.json();
    render(data);
    loaded = true; status.textContent = "Indicadores atualizados em " + new Date().toLocaleString("pt-BR");
  } catch (cause) {
    error.textContent = cause.message || "Não foi possível carregar o dashboard.";
    error.hidden = false; status.textContent = "Os indicadores não foram carregados.";
  } finally { loading = false; metrics.setAttribute("aria-busy", "false"); }
}

function metric(label, value, detail = "") {
  const article = document.createElement("article"); article.className = "dashboard-metrica";
  const title = document.createElement("h3"); title.textContent = label;
  const number = document.createElement("strong"); number.textContent = String(value ?? "Indisponível");
  article.append(title, number);
  if (detail) { const note = document.createElement("p"); note.textContent = detail; article.append(note); }
  return article;
}
function render(data) {
  const roles = data.membersByRole || [];
  const roleText = roles.length ? roles.map(item => `${roleName(item.label)}: ${item.total}`).join(" · ") : "Sem membros registrados";
  const publications = data.publications || {};
  const opportunities = data.opportunities || {};
  const slots = data.opportunities || {};
  const applications = data.applications || {};
  const campaigns = data.campaigns || {};
  const raised = campaigns.available
    ? new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(Number(campaigns.confirmedNetRaisedCents || 0) / 100)
    : "Indisponível";
  const cards = [
    metric("Membros por cargo", roles.reduce((sum, item) => sum + Number(item.total || 0), 0), roleText),
    metric("Publicações", `${publications.published || 0} publicadas`, `${publications.unpublished || 0} não publicadas`),
    metric("Oportunidades", `${opportunities.open || 0} abertas · ${opportunities.full || 0} lotadas`, `${opportunities.completed || 0} concluídas · ${opportunities.canceled || 0} canceladas`),
    metric("Vagas", `${slots.availableSlots || 0} disponíveis`, `${slots.occupiedSlots || 0} ocupadas de ${slots.totalCapacity || 0} na capacidade das oportunidades abertas`),
    metric("Candidaturas", String(applications.pending || 0) + " pendentes · " + String(applications.accepted || 0) + " aprovadas", String(applications.rejected || 0) + " recusadas · " + String(applications.canceled || 0) + " canceladas"),
    metric("Participantes concluídos", applications.completed || 0, "Participantes com conclusão registrada pela ONG."),
    metric("Horas voluntárias registradas", applications.completedHours == null ? "Indisponível" : Number(applications.completedHours).toLocaleString("pt-BR", { maximumFractionDigits: 2 }) + " h", "Soma das horas individuais registradas; não inclui carga horária planejada."),
    metric("Vaquinhas", campaigns.available ? `${Object.values(campaigns.states || {}).reduce((n, item) => n + Number(item.count || 0), 0)} campanhas` : "Indisponível",
      campaigns.available ? `Metas líquidas: ${money(campaigns.goalNetCents)} · ${raised} arrecadados líquidos confirmados` : campaigns.label || "Indisponível"),
    metric("Saldo para saque", "Indisponível", "O painel não tem acesso ao saldo Stripe ou à disponibilidade de payout.")
  ];
  $("#dashboard-metricas").replaceChildren(...cards);
  const actions = $("#dashboard-acoes"); actions.replaceChildren();
  const pending = Number(data.pendingActions?.pendingApplications || 0);
  const drafts = Number(data.pendingActions?.draftCampaigns || 0);
  let actionCount = 0;
  if (pending) {
    const link = document.createElement("a"); link.href = "#gestao-conteudo";
    link.textContent = `Revisar ${pending} candidatura(s) pendente(s)`;
    link.addEventListener("click", () => document.getElementById("aba-conteudo")?.click());
    actions.append(link); actionCount += 1;
  }
  if (drafts) {
    const link = document.createElement("a"); link.href = "#gestao-conteudo";
    link.textContent = `Publicar ou revisar ${drafts} vaquinha(s) em rascunho`;
    link.addEventListener("click", () => document.getElementById("aba-conteudo")?.click());
    actions.append(link); actionCount += 1;
  }
  if (!actionCount) actions.textContent = "Nenhuma ação pendente.";
  const activity = $("#dashboard-atividade");
  const rows = Array.isArray(data.recentActivity) ? data.recentActivity : [];
  activity.replaceChildren(...rows.map(item => {
    const line = document.createElement("li");
    const title = document.createElement("strong"); title.textContent = item.kind || "Atividade";
    const name = document.createElement("span"); name.textContent = " · " + (item.title || "Registro");
    const when = document.createElement("time");
    const date = new Date(item.occurredAt); when.textContent = Number.isNaN(date.getTime()) ? "" : " · " + date.toLocaleDateString("pt-BR");
    const manage = document.createElement("a"); manage.href = "#gestao-conteudo"; manage.textContent = "Gerenciar";
    manage.addEventListener("click", () => document.getElementById("aba-conteudo")?.click());
    line.append(title, name, when, manage); return line;
  }));
  if (!rows.length) { const empty = document.createElement("li"); empty.textContent = "Não há atividades recentes registradas."; activity.append(empty); }
  drawChart("dashboard-arrecadacao", data.fundraisingEvolution || [], "netCents", money, "Nenhum lançamento financeiro no período registrado.");
  drawChart("dashboard-candidaturas-tempo", data.applicationsOverTime || [], "total", value => String(value), "Nenhuma candidatura registrada no período.");
  drawChart("dashboard-status-candidaturas", data.applicationStatusDistribution || [], "total", value => String(value), "Nenhuma candidatura no período.", item => statusName(item.label));
  drawChart("dashboard-progresso-campanhas", data.campaignComparison || [], "confirmedNetCents", money, "Nenhuma vaquinha registrada.", item => item.title, item => `Meta ${money(item.goalNetCents)}`);
  drawChart("dashboard-membros-cargos", roles, "total", value => String(value), "Nenhum membro registrado.", item => roleName(item.label));
}
function drawChart(id, rows, key, format, emptyMessage, label = item => new Date(item.period).toLocaleDateString("pt-BR", { month: "short", year: "numeric" }), note = () => "") {
  const host = document.getElementById(id); if (!host) return;
  if (!rows.length) { host.textContent = emptyMessage; return; }
  const max = Math.max(1, ...rows.map(row => Number(row[key]) || 0));
  const chart = document.createElement("div"); chart.className = "dashboard-barras"; chart.setAttribute("role", "img"); chart.setAttribute("aria-label", `Gráfico de barras: ${rows.map(row => `${label(row)} ${format(Number(row[key]) || 0)}`).join("; ")}`);
  const table = document.createElement("table"); table.className = "dashboard-tabela-grafico"; table.innerHTML = "<thead><tr><th scope=\"col\">Categoria/período</th><th scope=\"col\">Valor</th><th scope=\"col\">Detalhe</th></tr></thead>";
  const body = document.createElement("tbody");
  for (const row of rows) { const value = Math.max(0, Number(row[key]) || 0); const line = document.createElement("div"); line.className = "dashboard-barra-linha"; const name = document.createElement("span"); name.textContent = label(row); const track = document.createElement("span"); track.className = "dashboard-barra-trilho"; const bar = document.createElement("span"); bar.className = "dashboard-barra-valor"; bar.style.width = `${Math.max(value ? 2 : 0, value / max * 100)}%`; track.append(bar); const amount = document.createElement("strong"); amount.textContent = format(value); line.append(name, track, amount); chart.append(line); const tr = document.createElement("tr"); for (const text of [label(row), format(value), note(row)]) { const td = document.createElement("td"); td.textContent = text || "—"; tr.append(td); } body.append(tr); }
  table.append(body); host.replaceChildren(chart, table);
}
function roleName(value) { return ({ CRIADOR: "Criador", ADMINISTRADOR: "Administradores", EDITOR: "Editores", MEMBRO: "Membros" })[value] || value; }
function money(cents) { return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(Number(cents || 0) / 100); }
function statusName(value) { return ({ PENDENTE: "Pendente", APROVADA: "Aceita", RECUSADA: "Rejeitada", DESISTIU: "Cancelada pela pessoa", CONCLUIDA: "Concluída", CANCELADA_PELA_ONG: "Cancelada pela ONG" })[value] || value; }

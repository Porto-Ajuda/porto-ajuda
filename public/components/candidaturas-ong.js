import { getAPI_URL } from "./modoBack.js";
import { obterTokenCSRF } from "./auth.js";
import { confirmarAcao } from "./confirm-modal.js";

const API = getAPI_URL();
const PAGE_SIZE = 30;
const $ = (parent, selector) => parent.querySelector(selector);
const statusNames = { PENDENTE: "Pendente", APROVADA: "Aprovada", RECUSADA: "Recusada", DESISTIU: "Cancelada", CONCLUIDA: "Concluída", CANCELADA_PELA_ONG: "Cancelada pela ONG" };

function getOscId() {
    const id = Number(new URLSearchParams(location.search).get("id"));
    return Number.isSafeInteger(id) && id > 0 ? id : null;
}
function createPanel() {
    const tab = document.getElementById("aba-conteudo");
    const panelId = tab?.getAttribute("aria-controls");
    const panel = panelId ? document.getElementById(panelId) : null;
    if (!panel || panel.querySelector("[data-candidaturas-ong]") || !getOscId()) return;
    const section = document.createElement("section");
    section.className = "gestao-candidaturas";
    section.dataset.candidaturasOng = "true";
    const requestedVacancy = Number(new URLSearchParams(location.search).get("vagaCandidatura"));
    if (Number.isSafeInteger(requestedVacancy) && requestedVacancy > 0) section.dataset.vagaId = String(requestedVacancy);
    section.setAttribute("aria-labelledby", "titulo-gestao-candidaturas");
    section.innerHTML = `
      <div class="gestao-candidaturas__cabecalho">
        <div><span class="candidaturas-kicker">VOLUNTARIADO</span><h2 id="titulo-gestao-candidaturas">Candidaturas recebidas</h2><p data-candidaturas-filtro-oportunidade>Revise inscrições nas oportunidades desta ONG.</p><button type="button" data-candidaturas-limpar-vaga hidden>Mostrar todas as oportunidades</button></div>
        <label>Filtrar por status
          <select data-candidaturas-status aria-label="Filtrar candidaturas por status">
            <option value="">Todos os status</option><option value="PENDENTE">Pendentes</option><option value="APROVADA">Aprovadas</option><option value="RECUSADA">Recusadas</option><option value="DESISTIU">Canceladas pela pessoa</option><option value="CONCLUIDA">Concluídas</option><option value="CANCELADA_PELA_ONG">Canceladas ao finalizar</option>
          </select>
        </label>
      </div>
      <p class="gestao-candidaturas__status" data-candidaturas-feedback role="status" aria-live="polite">Carregando candidaturas…</p>
      <div class="gestao-candidaturas__lista" data-candidaturas-lista></div>
      <nav class="gestao-candidaturas__paginacao" aria-label="Páginas de candidaturas">
        <button type="button" data-candidaturas-anterior disabled>Anterior</button><span data-candidaturas-pagina aria-live="polite"></span><button type="button" data-candidaturas-proxima disabled>Próxima</button>
      </nav>`;
    panel.append(section);
    $(section, "[data-candidaturas-feedback]").tabIndex = -1;
    const status = $(section, "[data-candidaturas-status]");
    status.addEventListener("change", () => { section.dataset.page = "0"; void load(section); });
    const clearVacancy = $(section, "[data-candidaturas-limpar-vaga]");
    clearVacancy.hidden = !section.dataset.vagaId;
    if (section.dataset.vagaId) $(section, "[data-candidaturas-filtro-oportunidade]").textContent = `Filtradas pela oportunidade ${section.dataset.vagaId}.`;
    clearVacancy.addEventListener("click", () => {
        delete section.dataset.vagaId;
        const url = new URL(location.href); url.searchParams.delete("vagaCandidatura"); history.replaceState(history.state, "", url);
        clearVacancy.hidden = true; $(section, "[data-candidaturas-filtro-oportunidade]").textContent = "Revise inscrições nas oportunidades desta ONG.";
        section.dataset.page = "0"; void load(section);
    });
    tab.addEventListener("click", () => void load(section));
    if (tab.getAttribute("aria-selected") === "true") void load(section);
    new MutationObserver(() => {
        if (tab.getAttribute("aria-selected") === "true") void load(section);
    }).observe(tab, { attributes: true, attributeFilter: ["aria-selected"] });
    $(section, "[data-candidaturas-anterior]").addEventListener("click", () => {
        section.dataset.page = String(Math.max(0, (Number(section.dataset.page) || 0) - 1));
        void load(section);
    });
    $(section, "[data-candidaturas-proxima]").addEventListener("click", () => {
        section.dataset.page = String((Number(section.dataset.page) || 0) + 1);
        void load(section);
    });
}

function buildCard(item) {
    const article = document.createElement("article");
    article.className = "gestao-candidatura-card";
    const title = document.createElement("h3");
    title.textContent = item.tituloOportunidade || "Oportunidade de voluntariado";
    const status = document.createElement("span");
    status.className = `candidatura-status candidatura-status--${String(item.status).toLowerCase()}`;
    status.textContent = statusNames[item.status] || "Status indisponível";
    const heading = document.createElement("div");
    heading.className = "gestao-candidatura-card__titulo";
    heading.append(title, status);
    const applicant = document.createElement("p");
    applicant.className = "gestao-candidatura-card__pessoa";
    applicant.textContent = item.nomeCandidato || "Candidato";
    const email = document.createElement("a");
    if (item.emailCandidato && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(item.emailCandidato)) {
        email.href = `mailto:${item.emailCandidato}`;
        email.textContent = item.emailCandidato;
    }
    const date = document.createElement("p");
    date.textContent = `Enviada em ${new Date(`${item.dataCandidatura}T00:00:00`).toLocaleDateString("pt-BR")}`;
    article.append(heading, applicant);
    if (email.textContent) article.append(email);
    if (item.mensagem) {
        const message = document.createElement("p");
        message.className = "gestao-candidatura-card__mensagem";
        message.textContent = item.mensagem;
        article.append(message);
    }
    article.append(date);
    if (item.status === "CONCLUIDA") {
        const completion = document.createElement("p");
        const parts = [];
        if (item.dataConclusao) parts.push(`Concluída em ${new Date(`${item.dataConclusao}T00:00:00`).toLocaleDateString("pt-BR")}`);
        if (item.horasRealizadas != null) parts.push(`${item.horasRealizadas} hora(s) realizadas`);
        completion.textContent = parts.join(" · "); article.append(completion);
        if (item.certificadoDisponivel) {
            const certificate = document.createElement("a"); certificate.href = `${API}/usuario/candidaturas/${item.id}/certificado`;
            certificate.textContent = "Baixar certificado"; certificate.rel = "noopener"; article.append(certificate);
        }
    }
    if (item.status === "PENDENTE" && item.podeDecidir) {
        const actions = document.createElement("div");
        actions.className = "gestao-candidatura-card__acoes";
        const approve = document.createElement("button");
        approve.type = "button";
        approve.textContent = "Aprovar";
        approve.addEventListener("click", () => void decide(item, "APROVADA", approve));
        const reject = document.createElement("button");
        reject.type = "button";
        reject.textContent = "Rejeitar";
        reject.addEventListener("click", () => void decide(item, "RECUSADA", reject));
        actions.append(approve, reject);
        article.append(actions);
    }
    return article;
}

/** Open the existing NGO candidate workflow in-place for one opportunity. */
export function openOpportunityCandidates({ oscId, opportunity, trigger, onFinalize, onChanged } = {}) {
    if (!Number.isSafeInteger(Number(oscId)) || !opportunity?.vaga?.id) return;
    let dialog = document.getElementById("dialog-candidatos-oportunidade");
    if (!dialog) {
        dialog = document.createElement("dialog");
        dialog.id = "dialog-candidatos-oportunidade";
        dialog.className = "dialogo-conteudo-osc dialogo-candidatos-oportunidade";
        dialog.setAttribute("aria-labelledby", "candidatos-oportunidade-titulo");
        dialog.innerHTML = `<header class="candidatos-modal-cabecalho"><h2 id="candidatos-oportunidade-titulo"></h2><button type="button" class="btn btn-secundario" data-candidatos-fechar aria-label="Fechar candidatos">Fechar</button></header>
          <dl class="candidatos-modal-resumo" aria-live="polite"></dl>
          <section class="gestao-candidaturas" data-candidaturas-ong><div class="gestao-candidaturas__cabecalho"><p data-candidaturas-feedback role="status" aria-live="polite">Carregando candidaturas…</p><label>Filtrar por status <select data-candidaturas-status aria-label="Filtrar candidaturas por status"><option value="">Todos os status</option><option value="PENDENTE">Pendentes</option><option value="APROVADA">Aprovadas</option><option value="RECUSADA">Recusadas</option><option value="DESISTIU">Canceladas pela pessoa</option><option value="CONCLUIDA">Concluídas</option><option value="CANCELADA_PELA_ONG">Canceladas ao finalizar</option></select></label></div><div class="gestao-candidaturas__lista" data-candidaturas-lista></div><nav class="gestao-candidaturas__paginacao" aria-label="Páginas de candidaturas"><button type="button" data-candidaturas-anterior disabled>Anterior</button><span data-candidaturas-pagina aria-live="polite"></span><button type="button" data-candidaturas-proxima disabled>Próxima</button></nav><div data-candidaturas-finalizar></div></section>`;
        document.body.append(dialog);
        dialog.querySelector("[data-candidaturas-fechar]").addEventListener("click", () => dialog.close());
        dialog.addEventListener("click", event => { if (event.target === dialog) dialog.close(); });
    }
    const section = dialog.querySelector("[data-candidaturas-ong]");
    section.dataset.vagaId = String(opportunity.vaga.id);
    section.dataset.requestGeneration = String((Number(section.dataset.requestGeneration) || 0) + 1);
    section.dataset.page = "0";
    section.dataset.oscId = String(oscId);
    section.dataset.opportunityTitle = opportunity.titulo || "Oportunidade de voluntariado";
    dialog.querySelector("#candidatos-oportunidade-titulo").textContent = section.dataset.opportunityTitle;
    dialog.querySelector("[data-candidaturas-status]").value = "";
    const resumo = dialog.querySelector(".candidatos-modal-resumo");
    const capacity = Number(opportunity.vaga.quantidadeVagas) || 0;
    resumo.innerHTML = `<div><dt>Capacidade</dt><dd>${capacity}</dd></div><div><dt>Participantes aceitos</dt><dd data-resumo-aceitos>…</dd></div><div><dt>Vagas disponíveis</dt><dd data-resumo-disponiveis>…</dd></div><div><dt>Candidaturas pendentes</dt><dd data-resumo-pendentes>…</dd></div>`;
    const finalizeHost = dialog.querySelector("[data-candidaturas-finalizar]");
    finalizeHost.replaceChildren();
    if (typeof onFinalize === "function" && opportunity.vaga.status === "ABERTA") {
        const finish = document.createElement("button"); finish.type = "button"; finish.className = "btn btn-secundario"; finish.textContent = "Finalizar voluntariado";
        finish.addEventListener("click", () => { dialog.close(); onFinalize(); }); finalizeHost.append(finish);
    }
    const refreshCounts = async () => {
        const fetchCount = async status => {
            const q = new URLSearchParams({ page: "0", size: "1", vagaId: String(opportunity.vaga.id) });
            if (status) q.set("status", status);
            const response = await fetch(`${API}/osc/${oscId}/candidaturas?${q}`, { credentials: "include", cache: "no-store", headers: { Accept: "application/json" } });
            if (!response.ok) throw new Error("Não foi possível atualizar os totais.");
            return Number((await response.json()).totalElements) || 0;
        };
        try {
            const [accepted, pending] = await Promise.all([fetchCount("APROVADA"), fetchCount("PENDENTE")]);
            resumo.querySelector("[data-resumo-aceitos]").textContent = String(accepted);
            resumo.querySelector("[data-resumo-disponiveis]").textContent = String(Math.max(0, capacity - accepted));
            resumo.querySelector("[data-resumo-pendentes]").textContent = String(pending);
            if (typeof onChanged === "function") onChanged({ accepted, pending, available: Math.max(0, capacity - accepted) });
            window.dispatchEvent(new CustomEvent("porto:volunteer-capacity-changed", { detail: { oscId: Number(oscId), vacancyId: Number(opportunity.vaga.id) } }));
        } catch { for (const node of resumo.querySelectorAll("dd")) if (node.textContent === "…") node.textContent = "Indisponível"; }
    };
    section.dataset.afterLoad = "refresh";
    section._refreshCounts = refreshCounts;
    const selected = section.querySelector("[data-candidaturas-status]");
    selected.onchange = () => { section.dataset.page = "0"; void load(section); };
    section.querySelector("[data-candidaturas-anterior]").onclick = () => { section.dataset.page = String(Math.max(0, (Number(section.dataset.page) || 0) - 1)); void load(section); };
    section.querySelector("[data-candidaturas-proxima]").onclick = () => { section.dataset.page = String((Number(section.dataset.page) || 0) + 1); void load(section); };
    const onClose = () => { if (trigger?.isConnected) trigger.focus(); dialog.removeEventListener("close", onClose); };
    dialog.addEventListener("close", onClose);
    dialog.showModal();
    void refreshCounts(); void load(section);
}

async function load(section) {
    if (section.dataset.loading === "true") return;
    section.dataset.loading = "true";
    const feedback = $(section, "[data-candidaturas-feedback]");
    const list = $(section, "[data-candidaturas-lista]");
    const pageNumber = Number(section.dataset.page) || 0;
    const requestedVacancy = section.dataset.vagaId;
    const requestedGeneration = section.dataset.requestGeneration;
    const status = $(section, "[data-candidaturas-status]").value;
    const query = new URLSearchParams({ page: String(pageNumber), size: String(PAGE_SIZE) });
    if (status) query.set("status", status);
    if (section.dataset.vagaId) query.set("vagaId", section.dataset.vagaId);
    feedback.textContent = "Carregando candidaturas…";
    try {
        const response = await fetch(`${API}/osc/${section.dataset.oscId || getOscId()}/candidaturas?${query}`, {
            credentials: "include", cache: "no-store", headers: { Accept: "application/json" }
        });
        if (response.status === 401 || response.status === 403) {
            feedback.textContent = "Esta conta não tem acesso à gestão de candidaturas desta ONG.";
            list.replaceChildren();
            return;
        }
        if (!response.ok) throw new Error("N?o foi poss?vel carregar as candidaturas. Tente novamente.");
        const data = await response.json();
        if (requestedVacancy !== section.dataset.vagaId || requestedGeneration !== section.dataset.requestGeneration) return;
        const entries = Array.isArray(data.content) ? data.content : [];
        list.replaceChildren(...entries.map(buildCard));
        const pages = Number(data.totalPages) || 0;
        $(section, "[data-candidaturas-pagina]").textContent = pages ? `Página ${pageNumber + 1} de ${pages}` : "";
        $(section, "[data-candidaturas-anterior]").disabled = pageNumber <= 0;
        $(section, "[data-candidaturas-proxima]").disabled = pageNumber + 1 >= pages;
        feedback.textContent = entries.length ? `${data.totalElements} candidatura(s).` : "Ainda não há candidaturas para este filtro.";
    } catch {
        feedback.textContent = "Não foi possível carregar as candidaturas. Tente novamente.";
    } finally { section.dataset.loading = "false"; if (section._refreshCounts) void section._refreshCounts(); }
}

async function decide(item, status, button) {
    if (button.disabled) return;
    const card = button.closest(".gestao-candidatura-card");
    const aprovado = status === "APROVADA";
    await confirmarAcao({
        tipo: "Decis\u00e3o de candidatura",
        titulo: aprovado ? "Aprovar candidatura?" : "Rejeitar candidatura?",
        descricao: aprovado
            ? "Esta decis\u00e3o ser\u00e1 registrada para a oportunidade."
            : "A candidatura ser\u00e1 marcada como rejeitada. Esta decis\u00e3o n\u00e3o poder\u00e1 ser alterada.",
        identificacao: item.nomeCandidato || "Candidato",
        textoConfirmar: aprovado ? "Aprovar" : "Rejeitar",
        estilo: aprovado ? "positivo" : "destrutivo",
        mensagemSucesso: aprovado ? "Candidatura aprovada." : "Candidatura rejeitada.",
        focoSucesso: $(button.closest("[data-candidaturas-ong]"), "[data-candidaturas-feedback]"),
        origem: button,
        aoConfirmar: async () => {
            card.querySelectorAll("button").forEach(actionButton => { actionButton.disabled = true; });
            try {
                const csrf = await obterTokenCSRF();
                const response = await fetch(`${API}/osc/${button.closest("[data-candidaturas-ong]").dataset.oscId || getOscId()}/candidaturas/${encodeURIComponent(item.id)}`, {
                    method: "PATCH", credentials: "include", cache: "no-store",
                    headers: { Accept: "application/json", "Content-Type": "application/json", [csrf.headerName]: csrf.token },
                    body: JSON.stringify({ status })
                });
                if (response.status === 200) {
                    await load(button.closest("[data-candidaturas-ong]"));
                    return;
                }
                if ([401, 403, 404, 409].includes(response.status)) {
                    await load(button.closest("[data-candidaturas-ong]"));
                }
                throw new Error(response.status === 403 ? "Seu cargo n\u00e3o pode decidir candidaturas."
                    : response.status === 409 ? "A vaga atingiu a capacidade ou a candidatura j\u00e1 foi decidida. A lista foi atualizada."
                        : response.status === 404 ? "A candidatura n\u00e3o est\u00e1 mais dispon\u00edvel."
                            : "N\u00e3o foi poss\u00edvel salvar a decis\u00e3o.");
            } catch (erro) {
                if (erro instanceof TypeError) throw new Error("Falha de conex\u00e3o. Atualize a lista para confirmar o estado antes de repetir.");
                throw erro;
            } finally {
                card.querySelectorAll("button").forEach(actionButton => { actionButton.disabled = false; });
            }
        }
    });
}

createPanel();

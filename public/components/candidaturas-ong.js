import { getAPI_URL } from "./modoBack.js";
import { obterTokenCSRF } from "./auth.js";
import { confirmarAcao } from "./confirm-modal.js";

const API = getAPI_URL();
const PAGE_SIZE = 30;
const $ = (parent, selector) => parent.querySelector(selector);
const statusNames = { PENDENTE: "Pendente", APROVADA: "Aprovada", RECUSADA: "Recusada", DESISTIU: "Cancelada" };

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
    section.setAttribute("aria-labelledby", "titulo-gestao-candidaturas");
    section.innerHTML = `
      <div class="gestao-candidaturas__cabecalho">
        <div><span class="candidaturas-kicker">VOLUNTARIADO</span><h2 id="titulo-gestao-candidaturas">Candidaturas recebidas</h2><p>Revise inscrições nas oportunidades desta ONG.</p></div>
        <label>Filtrar por status
          <select data-candidaturas-status aria-label="Filtrar candidaturas por status">
            <option value="">Todos os status</option><option value="PENDENTE">Pendentes</option><option value="APROVADA">Aprovadas</option><option value="RECUSADA">Recusadas</option><option value="DESISTIU">Canceladas</option>
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
    tab.addEventListener("click", () => void load(section));
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

async function load(section) {
    if (section.dataset.loading === "true") return;
    section.dataset.loading = "true";
    const feedback = $(section, "[data-candidaturas-feedback]");
    const list = $(section, "[data-candidaturas-lista]");
    const pageNumber = Number(section.dataset.page) || 0;
    const status = $(section, "[data-candidaturas-status]").value;
    const query = new URLSearchParams({ page: String(pageNumber), size: String(PAGE_SIZE) });
    if (status) query.set("status", status);
    feedback.textContent = "Carregando candidaturas…";
    try {
        const response = await fetch(`${API}/osc/${getOscId()}/candidaturas?${query}`, {
            credentials: "include", cache: "no-store", headers: { Accept: "application/json" }
        });
        if (response.status === 401 || response.status === 403) {
            feedback.textContent = "Esta conta não tem acesso à gestão de candidaturas desta ONG.";
            list.replaceChildren();
            return;
        }
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const data = await response.json();
        const entries = Array.isArray(data.content) ? data.content : [];
        list.replaceChildren(...entries.map(buildCard));
        const pages = Number(data.totalPages) || 0;
        $(section, "[data-candidaturas-pagina]").textContent = pages ? `Página ${pageNumber + 1} de ${pages}` : "";
        $(section, "[data-candidaturas-anterior]").disabled = pageNumber <= 0;
        $(section, "[data-candidaturas-proxima]").disabled = pageNumber + 1 >= pages;
        feedback.textContent = entries.length ? `${data.totalElements} candidatura(s).` : "Ainda não há candidaturas para este filtro.";
    } catch {
        feedback.textContent = "Não foi possível carregar as candidaturas. Tente novamente.";
    } finally { section.dataset.loading = "false"; }
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
                const response = await fetch(`${API}/osc/${getOscId()}/candidaturas/${encodeURIComponent(item.id)}`, {
                    method: "PATCH", credentials: "include", cache: "no-store",
                    headers: { Accept: "application/json", "Content-Type": "application/json", [csrf.headerName]: csrf.token },
                    body: JSON.stringify({ status })
                });
                if (response.status === 200) {
                    await load(button.closest("[data-candidaturas-ong]"));
                    return;
                }
                if (response.status === 409) await load(button.closest("[data-candidaturas-ong]"));
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

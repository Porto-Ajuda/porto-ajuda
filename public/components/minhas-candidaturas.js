import { fetchAutenticado } from "./api.js";
import { getAPI_URL } from "./modoBack.js";
import { obterTokenCSRF } from "./auth.js";
import { confirmarAcao } from "./confirm-modal.js";

const API = getAPI_URL();
const PAGE_SIZE = 10;
let pagina = 0;
let totalPaginas = 0;
let carregando = false;
const $ = id => document.getElementById(id);

function texto(value) { return String(value ?? "").trim(); }
function dataPt(value) {
    if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return "Data indisponível";
    const [ano, mes, dia] = value.split("-").map(Number);
    return new Date(ano, mes - 1, dia).toLocaleDateString("pt-BR", { day: "2-digit", month: "short", year: "numeric" });
}
function rotuloStatus(status) {
    return ({ PENDENTE: "Pendente", APROVADA: "Aprovada", RECUSADA: "Recusada", DESISTIU: "Cancelada" })[status] || "Status indisponível";
}
function definirEstado(mensagem, erro = false) {
    const node = $("candidaturas-usuario-status");
    node.textContent = mensagem;
    node.classList.toggle("erro", erro);
}
function linhaMeta(rotulo, valor) {
    const linha = document.createElement("p");
    linha.className = "candidatura-usuario-card__meta";
    const label = document.createElement("span");
    label.textContent = `${rotulo}:`;
    const value = document.createElement("span");
    value.textContent = valor;
    linha.append(label, value);
    return linha;
}
function cardCandidatura(item) {
    const article = document.createElement("article");
    article.className = "candidatura-usuario-card";
    const header = document.createElement("header");
    const title = document.createElement("h3");
    title.textContent = texto(item.tituloOportunidade) || "Oportunidade de voluntariado";
    const status = document.createElement("span");
    status.className = `candidatura-status candidatura-status--${String(item.status || "").toLowerCase()}`;
    status.textContent = rotuloStatus(item.status);
    header.append(title, status);
    const ngo = document.createElement("p");
    ngo.textContent = texto(item.nomeOsc) || "ONG parceira";
    ngo.className = "candidatura-usuario-card__ong";
    const details = document.createElement("div");
    details.className = "candidatura-usuario-card__metadados";
    const modalidade = texto(item.modalidade).replaceAll("_", " ");
    if (modalidade) details.append(linhaMeta("Modalidade", modalidade));
    if (texto(item.cidade)) details.append(linhaMeta("Cidade", texto(item.cidade)));
    if (texto(item.bairro)) details.append(linhaMeta("Bairro", texto(item.bairro)));
    if (item.dataInicio) details.append(linhaMeta("Início", dataPt(item.dataInicio)));
    if (item.dataFim) details.append(linhaMeta("Término", dataPt(item.dataFim)));
    const applied = document.createElement("p");
    applied.className = "candidatura-usuario-card__data";
    applied.textContent = `Candidatura enviada em ${dataPt(item.dataCandidatura)}`;
    article.append(header, ngo, details, applied);
    if (item.mensagem) {
        const message = document.createElement("p");
        message.className = "candidatura-usuario-mensagem";
        message.textContent = `Sua mensagem: ${item.mensagem}`;
        article.append(message);
    }
    const actions = document.createElement("div");
    actions.className = "candidatura-usuario-acoes";
    if (item.oportunidadePublica && item.postId && item.oscId) {
        const link = document.createElement("a");
        const params = new URLSearchParams({ candidaturaPost: item.postId, candidaturaOsc: item.oscId });
        link.href = `/?${params}`;
        link.textContent = "Ver oportunidade";
        link.className = "botao botao-secundario";
        actions.append(link);
    }
    if (item.status === "PENDENTE") {
        const cancel = document.createElement("button");
        cancel.type = "button";
        cancel.className = "botao botao-secundario candidatura-cancelar";
        cancel.textContent = "Cancelar candidatura";
        cancel.addEventListener("click", () => void cancelar(item.id, cancel));
        actions.append(cancel);
    }
    article.append(actions);
    return article;
}
async function carregar(paginaSolicitada = pagina) {
    if (carregando) return;
    carregando = true;
    definirEstado("Carregando suas candidaturas…");
    $("candidaturas-usuario-anterior").disabled = true;
    $("candidaturas-usuario-proximo").disabled = true;
    try {
        const response = await fetchAutenticado(`/usuario/candidaturas?page=${paginaSolicitada}&size=${PAGE_SIZE}`);
        if (response.status === 401 || response.status === 403) {
            definirEstado("Entre novamente para consultar suas candidaturas.", true);
            $("candidaturas-usuario-lista").replaceChildren();
            return;
        }
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const page = await response.json();
        const items = Array.isArray(page.content) ? page.content : [];
        pagina = Number(page.number) || 0;
        totalPaginas = Number(page.totalPages) || 0;
        $("candidaturas-usuario-pagina").textContent = totalPaginas ? `Página ${pagina + 1} de ${totalPaginas}` : "";
        $("candidaturas-usuario-anterior").disabled = pagina <= 0;
        $("candidaturas-usuario-proximo").disabled = pagina + 1 >= totalPaginas;
        $("candidaturas-usuario-lista").replaceChildren(...items.map(cardCandidatura));
        definirEstado(items.length ? `${page.totalElements} candidatura(s) registrada(s).` : "Você ainda não enviou candidaturas.");
    } catch {
        definirEstado("Não foi possível carregar as candidaturas. Tente novamente.", true);
        $("candidaturas-usuario-lista").replaceChildren();
    } finally { carregando = false; }
}
async function cancelar(id, button) {
    if (button.disabled) return;
    await confirmarAcao({
        tipo: "Cancelamento de candidatura",
        titulo: "Cancelar candidatura?",
        descricao: "A candidatura pendente será marcada como cancelada. Essa ação não pode ser desfeita.",
        textoCancelar: "Manter candidatura",
        textoConfirmar: "Cancelar candidatura",
        estilo: "destrutivo",
        origem: button,
        focoSucesso: $("candidaturas-usuario-status"),
        mensagemSucesso: "Candidatura cancelada.",
        aoConfirmar: async () => {
            button.disabled = true;
            definirEstado("Cancelando candidatura…");
            try {
                const csrf = await obterTokenCSRF();
                const response = await fetch(`${API}/usuario/candidaturas/${encodeURIComponent(id)}/cancelar`, {
                    method: "PATCH", credentials: "include", cache: "no-store",
                    headers: { Accept: "application/json", [csrf.headerName]: csrf.token }
                });
                if (response.ok) { await carregar(pagina); return; }
                throw new Error(response.status === 409
                    ? "A candidatura já recebeu uma decisão e não pode ser cancelada."
                    : response.status === 404 ? "A candidatura não foi encontrada."
                        : "Não foi possível cancelar agora. Tente novamente.");
            } catch (erro) {
                if (erro instanceof TypeError) throw new Error("Não foi possível conectar ao serviço. Confira o status antes de tentar novamente.");
                throw erro;
            } finally { button.disabled = false; }
        }
    });
}

$("aba-candidaturas")?.addEventListener("click", () => void carregar(0));
$("candidaturas-usuario-anterior")?.addEventListener("click", () => { if (pagina > 0) void carregar(pagina - 1); });
$("candidaturas-usuario-proximo")?.addEventListener("click", () => { if (pagina + 1 < totalPaginas) void carregar(pagina + 1); });
document.addEventListener("porto:application-created", () => {
    if ($("painel-candidaturas") && !$("painel-candidaturas").hidden) void carregar(pagina);
});
if (window.location.hash === "#candidaturas") void carregar(0);

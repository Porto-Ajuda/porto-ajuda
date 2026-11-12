import { getAPI_URL } from "./modoBack.js";
import { fetchAutenticado, sessaoInvalidaConfirmada } from "./api.js";
import { obterTokenCSRF } from "./auth.js";

const API = getAPI_URL();
const $ = id => document.getElementById(id);
let emEnvio = false;
let oportunidadeConfirmada = null;

function linkDeRetornoComOportunidade(item) {
    const destino = new URL(window.location.href);
    destino.searchParams.set("candidaturaPost", String(item.id));
    destino.searchParams.set("candidaturaOsc", String(item.oscId));
    return `${destino.pathname}${destino.search}${destino.hash}`;
}

function orientarLogin(item) {
    const retorno = linkDeRetornoComOportunidade(item);
    window.location.assign(`/login?retorno=${encodeURIComponent(retorno)}`);
}

function mostrarEstado(texto, { erro = false } = {}) {
    const status = $("estado-dialogo-candidatura");
    if (!status) return;
    status.textContent = texto;
    status.classList.toggle("erro", erro);
}

async function abrirConfirmacao() {
    const item = window.conteudoVoluntariadoAtual;
    if (!item || !item.vaga || emEnvio) return;
    const botao = $("btn-modal-candidatar");
    if (botao?.hidden || botao?.disabled) return;

    botao.disabled = true;
    botao.textContent = "Verificando acesso…";
    try {
        const sessao = await fetchAutenticado("/usuario/perfil", { method: "GET" });
        if (sessaoInvalidaConfirmada(sessao) || sessao.status === 401) {
            orientarLogin(item);
            return;
        }
        if (!sessao.ok) {
            mostrarEstado("Não foi possível validar sua sessão. Tente novamente.", { erro: true });
            return;
        }
        oportunidadeConfirmada = item;
        $("texto-dialogo-candidatura").textContent = `Você está se candidatando a “${item.titulo}”, de ${item.nomeOsc || "uma ONG parceira"}. A candidatura ficará pendente até a análise da organização.`;
        mostrarEstado("");
        const dialog = $("dialogo-candidatura");
        if (!dialog.open) dialog.showModal();
    } catch {
        mostrarEstado("Não foi possível confirmar sua sessão. Tente novamente.", { erro: true });
    } finally {
        botao.disabled = false;
        botao.textContent = "Quero participar";
    }
}

async function validarDisponibilidadeViewer(evento) {
    const item = evento.detail?.item;
    const botao = $("btn-modal-candidatar");
    const estado = $("estado-candidatura-modal");
    if (!item?.vaga?.id || !botao || !estado) return;
    botao.hidden = true;
    estado.hidden = false;
    estado.textContent = "Verificando se a oportunidade ainda recebe candidaturas…";
    try {
        const response = await fetch(`${API}/voluntariado/vagas/${encodeURIComponent(item.vaga.id)}/disponibilidade`, {
            headers: { Accept: "application/json" }, cache: "no-store"
        });
        if (!response.ok) {
            estado.textContent = response.status === 404
                ? "O backend precisa ser atualizado para consultar as candidaturas desta oportunidade."
                : "Não foi possível verificar a disponibilidade. Tente novamente mais tarde.";
            return;
        }
        const resultado = await response.json();
        if (resultado.aceitaCandidaturas === true) {
            const candidatura = await fetchAutenticado(
                `/voluntariado/vagas/${encodeURIComponent(item.vaga.id)}/minha-candidatura`,
                { method: "GET" }
            );
            if (sessaoInvalidaConfirmada(candidatura) || candidatura.status === 401) {
                estado.hidden = true;
                botao.hidden = false;
                return;
            }
            if (!candidatura.ok) {
                estado.textContent = "Não foi possível confirmar se você já se candidatou. Reabra o visualizador ou tente novamente.";
                return;
            }
            const situacao = await candidatura.json();
            if (situacao.jaSeCandidatou) {
                estado.textContent = `Você já se candidatou a esta oportunidade (${({ PENDENTE: "pendente", APROVADA: "aprovada", RECUSADA: "recusada" })[situacao.status] || "em análise"}). Consulte Minhas candidaturas para ver o histórico.`;
                return;
            }
            estado.hidden = true;
            estado.textContent = "";
            botao.hidden = false;
        } else {
            estado.textContent = "Esta oportunidade não está mais aceitando candidaturas.";
        }
    } catch {
        estado.textContent = "Não foi possível verificar a disponibilidade. Nenhuma candidatura foi enviada.";
    }
}

async function enviarCandidatura() {
    if (emEnvio || !oportunidadeConfirmada?.vaga?.id) return;
    emEnvio = true;
    const confirmar = $("confirmar-candidatura");
    const botaoAplicar = $("btn-modal-candidatar");
    confirmar.disabled = true;
    confirmar.textContent = "Enviando…";
    botaoAplicar.disabled = true;
    try {
        const csrf = await obterTokenCSRF();
        const resposta = await fetch(`${API}/voluntariado/vagas/${encodeURIComponent(oportunidadeConfirmada.vaga.id)}/candidaturas`, {
            method: "POST",
            credentials: "include",
            cache: "no-store",
            headers: {
                Accept: "application/json",
                "Content-Type": "application/json",
                [csrf.headerName]: csrf.token
            },
            body: JSON.stringify({ mensagem: null })
        });
        if (resposta.status === 201) {
            const result = await resposta.json();
            $("dialogo-candidatura").close();
            mostrarEstado("Candidatura enviada. Você pode acompanhar o andamento em Minhas candidaturas.");
            const estadoModal = $("estado-candidatura-modal");
            estadoModal.hidden = false;
            estadoModal.textContent = "Candidatura enviada. Acompanhe o status no seu perfil.";
            botaoAplicar.textContent = "Candidatura enviada";
            botaoAplicar.disabled = true;
            oportunidadeConfirmada = null;
            document.dispatchEvent(new CustomEvent("porto:application-created", { detail: result }));
            return;
        }
        const mensagem = resposta.status === 401 ? "Sua sessão expirou. Entre novamente para enviar a candidatura."
            : resposta.status === 403 ? "Sua conta não tem permissão para se candidatar."
                : resposta.status === 404 ? "A oportunidade não está mais disponível. Atualize a página."
                    : resposta.status === 409 ? "Já existe uma candidatura para esta oportunidade ou ela não está aceitando candidaturas."
                        : resposta.status === 400 ? "Confira os dados da candidatura e tente novamente."
                            : resposta.status === 429 ? "Muitas solicitações. Aguarde e tente novamente."
                                : "Não foi possível enviar a candidatura agora. Tente novamente.";
        mostrarEstado(mensagem, { erro: true });
    } catch (error) {
        mostrarEstado(error?.name === "AbortError"
            ? "A solicitação expirou. Confira Minhas candidaturas antes de tentar novamente."
            : "Não foi possível conectar ao serviço. Confira sua conexão e tente novamente.", { erro: true });
    } finally {
        emEnvio = false;
        confirmar.disabled = false;
        confirmar.textContent = "Confirmar candidatura";
        if (oportunidadeConfirmada) botaoAplicar.disabled = false;
    }
}

async function restaurarVisualizadorDaUrl() {
    const params = new URLSearchParams(window.location.search);
    const postId = params.get("candidaturaPost");
    const oscId = params.get("candidaturaOsc");
    if (!/^\d+$/.test(postId || "") || !/^\d+$/.test(oscId || "")) return;
    const url = new URL(`/post/osc/${encodeURIComponent(oscId)}/publicos/${encodeURIComponent(postId)}`, API);
    try {
        const response = await fetch(url, { headers: { Accept: "application/json" }, cache: "no-store" });
        if (response.ok) {
            const item = await response.json();
            if (item.tipo === "VOLUNTARIADO" && item.vaga) {
                window.abrirModalConteudoPublico?.(item, "voluntariado", document.activeElement);
            }
        }
    } catch { /* a listagem continua utilizável mesmo se a restauração do link falhar */ }
    params.delete("candidaturaPost");
    params.delete("candidaturaOsc");
    const busca = params.toString();
    history.replaceState(null, "", `${location.pathname}${busca ? `?${busca}` : ""}${location.hash}`);
}

$("btn-modal-candidatar")?.addEventListener("click", () => void abrirConfirmacao());
window.addEventListener("porto:volunteer-viewer-open", evento => void validarDisponibilidadeViewer(evento));
$("confirmar-candidatura")?.addEventListener("click", () => void enviarCandidatura());
$("dialogo-candidatura")?.addEventListener("close", () => {
    oportunidadeConfirmada = null;
    if (!emEnvio) {
        const status = $("estado-dialogo-candidatura");
        if (status) status.textContent = "";
    }
});
void restaurarVisualizadorDaUrl();

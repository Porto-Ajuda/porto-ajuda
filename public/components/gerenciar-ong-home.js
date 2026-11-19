import { fetchAutenticado } from "./api.js";
import { normalizarOngsMinhas, rotaGerenciamentoOng } from "./gerenciar-ong-home-logic.js";

const dialog = document.getElementById("dialogo-gerenciar-ongs-home");
const lista = document.getElementById("gerenciar-ongs-home-lista");
const estado = document.getElementById("gerenciar-ongs-home-estado");
const entrar = document.getElementById("gerenciar-ongs-home-entrar");
const cadastrar = document.getElementById("gerenciar-ongs-home-cadastrar");
const tentarNovamente = document.getElementById("gerenciar-ongs-home-tentar-novamente");
const fechar = document.getElementById("gerenciar-ongs-home-fechar");
let areaSolicitada = "conteudo";
let acionador = null;
let requisicao = 0;
let carregando = false;

function definirEstado(mensagem, { login = false, cadastro = false, erro = false } = {}) {
    estado.textContent = mensagem;
    estado.classList.toggle("erro", erro);
    entrar.hidden = !login;
    cadastrar.hidden = !cadastro;
    tentarNovamente.hidden = !erro || login;
}

function criarOpcao(ong) {
    const botao = document.createElement("button");
    botao.type = "button";
    botao.className = "gerenciar-ongs-home__item";
    const nome = document.createElement("strong");
    nome.textContent = ong.nome;
    const detalhes = document.createElement("span");
    detalhes.textContent = `${ong.cargo} · ${ong.status}`;
    botao.append(nome, detalhes);
    botao.addEventListener("click", () => {
        if (botao.disabled) return;
        const destino = rotaGerenciamentoOng(ong.id, areaSolicitada);
        if (destino) window.location.assign(destino);
    });
    return botao;
}

async function carregarOngs() {
    if (carregando) return;
    carregando = true;
    const idRequisicao = ++requisicao;
    lista.setAttribute("aria-busy", "true");
    lista.replaceChildren();
    definirEstado("Carregando as organizações vinculadas à sua conta…");
    try {
        const response = await fetchAutenticado("/osc/minhas", { headers: { Accept: "application/json" } });
        if (idRequisicao !== requisicao) return;
        if (response.status === 401) {
            definirEstado("Entre na sua conta para consultar as organizações vinculadas.", { login: true });
            return;
        }
        if (response.status === 403) {
            definirEstado("Sua conta não tem autorização para consultar esta lista.", { erro: true });
            return;
        }
        if (!response.ok) throw new Error("Não foi possível carregar as organizações. Tente novamente.");

        const organizations = normalizarOngsMinhas(await response.json());
        if (idRequisicao !== requisicao) return;
        if (!organizations.length) {
            definirEstado("Você ainda não está vinculada a uma ONG.", { cadastro: true });
            return;
        }
        lista.replaceChildren(...organizations.map(criarOpcao));
        definirEstado("Selecione uma ONG. As ações disponíveis serão conferidas pelo seu vínculo, cargo e restrições atuais.");
    } catch (error) {
        if (idRequisicao === requisicao) definirEstado(error?.message || "Não foi possível carregar as organizações. Tente novamente.", { erro: true });
    } finally {
        if (idRequisicao === requisicao) {
            lista.setAttribute("aria-busy", "false");
            carregando = false;
        }
    }
}

function abrirSeletor(event) {
    if (dialog.open) return;
    acionador = event.currentTarget;
    areaSolicitada = acionador.dataset.homeManage === "ong" ? "ong" : "conteudo";
    dialog.showModal();
    fechar.focus();
    void carregarOngs();
}

document.querySelectorAll("[data-home-manage]").forEach(button => {
    button.addEventListener("click", abrirSeletor);
});
fechar.addEventListener("click", () => dialog.close());
tentarNovamente.addEventListener("click", () => void carregarOngs());
dialog.addEventListener("close", () => {
    requisicao += 1;
    carregando = false;
    lista.setAttribute("aria-busy", "false");
    acionador?.focus();
    acionador = null;
});

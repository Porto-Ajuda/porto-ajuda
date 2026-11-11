import { getAPI_URL } from "./modoBack.js";
import { urlImagemSegura } from "./ong-content.js";
import { createShowcasePager, HOME_SHOWCASE_PAGE_SIZE } from "./showcase-pagination.js";
import { renderizarPaginaOngs } from "./carrossel-amostra.js";

const API_URL = getAPI_URL();

function criarElemento(tag, classe, texto) {
    const elemento = document.createElement(tag);
    if (classe) elemento.className = classe;
    if (texto != null) elemento.textContent = texto;
    return elemento;
}

function formatarData(valor) {
    if (!valor) return "";
    const data = new Date(valor);
    return Number.isNaN(data.getTime())
        ? ""
        : data.toLocaleDateString("pt-BR", { day: "2-digit", month: "short", year: "numeric" });
}

function criarPreview(item, tipo) {
    const preview = criarElemento("div", "showcase-card-preview");
    preview.appendChild(criarElemento("span", "showcase-card-kicker", tipo));
    preview.appendChild(criarElemento("strong", "showcase-card-preview-title", item.titulo || "Conteúdo público"));
    const trecho = (item.conteudo || "").trim();
    if (trecho) preview.appendChild(criarElemento("span", "showcase-card-preview-text", trecho.slice(0, 150)));
    return preview;
}

function preencherMidia(card, item, tipo) {
    const media = criarElemento("div", "showcase-card-media");
    if (item.imagemUrl && urlImagemSegura(item.imagemUrl)) {
        const imagem = document.createElement("img");
        imagem.src = item.imagemUrl;
        imagem.alt = "";
        imagem.loading = "lazy";
        imagem.referrerPolicy = "no-referrer";
        imagem.addEventListener("error", () => media.replaceChildren(criarPreview(item, tipo)), { once: true });
        media.appendChild(imagem);
    } else {
        media.appendChild(criarPreview(item, tipo));
    }
    card.appendChild(media);
}

function criarCardConteudo(item, tipo) {
    const oportunidade = tipo === "Voluntariado";
    const itemLista = document.createElement("div");
    itemLista.setAttribute("role", "listitem");
    const card = document.createElement("button");
    card.type = "button";
    card.className = `showcase-card ${oportunidade ? "showcase-card-voluntariado" : "showcase-card-publicacao"}`;
    card.setAttribute("aria-haspopup", "dialog");
    card.setAttribute("aria-label", `Abrir ${oportunidade ? "oportunidade" : "publicação"}: ${item.titulo || "Conteúdo público"}, ${item.nomeOsc || "organização"}`);
    preencherMidia(card, item, tipo.toLocaleUpperCase("pt-BR"));

    const informacoes = criarElemento("div", "showcase-card-informacoes");
    informacoes.appendChild(criarElemento("span", "showcase-card-kicker", tipo));
    informacoes.appendChild(criarElemento("strong", "showcase-card-title", item.titulo || "Conteúdo público"));
    informacoes.appendChild(criarElemento("span", "showcase-card-ngo", item.nomeOsc || "Organização parceira"));

    if (oportunidade && item.vaga) {
        const vaga = item.vaga;
        const modalidade = {
            PRESENCIAL: "Presencial",
            REMOTO: "Remoto",
            HIBRIDO: "Híbrido"
        }[vaga.modalidadeVoluntariado] || "Modalidade não informada";
        informacoes.appendChild(criarElemento("span", "showcase-card-detail", modalidade));
        const local = [vaga.bairro, vaga.cidade].filter(Boolean).join(", ");
        if (local) informacoes.appendChild(criarElemento("span", "showcase-card-detail", local));
        informacoes.appendChild(criarElemento(
            "span",
            "showcase-card-detail",
            `${vaga.quantidadeVagas} ${vaga.quantidadeVagas === 1 ? "vaga disponível" : "vagas disponíveis"}`
        ));
    } else {
        const data = formatarData(item.dataCriacao);
        if (data) informacoes.appendChild(criarElemento("time", "showcase-card-detail", data));
    }

    card.appendChild(informacoes);
    card.addEventListener("click", () => {
        window.abrirModalConteudoPublico?.(item, oportunidade ? "voluntariado" : "publicacao", card);
    });
    itemLista.appendChild(card);
    return itemLista;
}

function renderizarCardsConteudo(container, items, tipo) {
    container.replaceChildren(...items.map(item => criarCardConteudo(item, tipo)));
}

function mensagemEstado(rotulo, state) {
    if (state.status === "loading") return `Carregando ${rotulo.toLocaleLowerCase("pt-BR")}…`;
    if (state.status === "error") return `Não foi possível carregar ${rotulo.toLocaleLowerCase("pt-BR")}. Tente novamente.`;
    if (state.status === "empty") return `Nenhum conteúdo público disponível em ${rotulo.toLocaleLowerCase("pt-BR")} no momento.`;
    if (state.status === "ready") return `Página ${state.page + 1} de ${state.totalPages}`;
    return "";
}

function iniciarVitrine({
    containerId,
    statusId,
    anteriorId,
    proximoId,
    tentarNovamenteId,
    rotulo,
    endpoint,
    renderizar
}) {
    const container = document.getElementById(containerId);
    const status = document.getElementById(statusId);
    const anterior = document.getElementById(anteriorId);
    const proximo = document.getElementById(proximoId);
    const tentarNovamente = document.getElementById(tentarNovamenteId);
    if (!container || !status || !anterior || !proximo || !tentarNovamente) return null;

    container.dataset.homeShowcase = "true";
    anterior.setAttribute("aria-controls", containerId);
    proximo.setAttribute("aria-controls", containerId);

    const pager = createShowcasePager({
        pageSize: HOME_SHOWCASE_PAGE_SIZE,
        fetchPage: async (page, size) => {
            const resposta = await fetch(`${API_URL}${endpoint}?page=${page}&size=${size}`, {
                headers: { Accept: "application/json" },
                cache: "no-store"
            });
            if (!resposta.ok) throw new Error(`HTTP ${resposta.status}`);
            return resposta.json();
        },
        onItems: items => renderizar(container, items),
        onState: state => {
            status.textContent = mensagemEstado(rotulo, state);
            anterior.disabled = !state.canPrevious;
            proximo.disabled = !state.canNext;
            tentarNovamente.hidden = state.status !== "error";
        }
    });

    anterior.addEventListener("click", () => { void pager.previous(); });
    proximo.addEventListener("click", () => { void pager.next(); });
    tentarNovamente.addEventListener("click", () => { void pager.retry(); });
    void pager.load();
    return pager;
}

function prepararEstadoVazioDoacoes() {
    const status = document.getElementById("doacoes-status");
    const anterior = document.getElementById("doacoes-anterior");
    const proximo = document.getElementById("doacoes-proximo");
    const tentarNovamente = document.getElementById("doacoes-tentar-novamente");
    document.getElementById("cards-doacao")?.replaceChildren();
    if (status) status.textContent = "Ainda não há campanhas de arrecadação verificadas para exibir.";
    if (anterior) anterior.disabled = true;
    if (proximo) proximo.disabled = true;
    if (tentarNovamente) tentarNovamente.hidden = true;
}

iniciarVitrine({
    containerId: "cards-osc",
    statusId: "ongs-status",
    anteriorId: "ongs-anterior",
    proximoId: "ongs-proximo",
    tentarNovamenteId: "ongs-tentar-novamente",
    rotulo: "ONGs",
    endpoint: "/osc/list",
    renderizar: renderizarPaginaOngs
});

iniciarVitrine({
    containerId: "cards-publicacoes",
    statusId: "publicacoes-status",
    anteriorId: "publicacoes-anterior",
    proximoId: "publicacoes-proximo",
    tentarNovamenteId: "publicacoes-tentar-novamente",
    rotulo: "publicações",
    endpoint: "/post/publicos",
    renderizar: (container, items) => renderizarCardsConteudo(container, items, "Publicação")
});

iniciarVitrine({
    containerId: "cards-voluntariacao",
    statusId: "voluntariado-status",
    anteriorId: "voluntariado-anterior",
    proximoId: "voluntariado-proximo",
    tentarNovamenteId: "voluntariado-tentar-novamente",
    rotulo: "voluntariado",
    endpoint: "/post/voluntariados",
    renderizar: (container, items) => renderizarCardsConteudo(container, items, "Voluntariado")
});

prepararEstadoVazioDoacoes();

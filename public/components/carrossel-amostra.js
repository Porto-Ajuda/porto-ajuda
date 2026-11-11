import { getAPI_URL } from "./modoBack.js";

const API_URL = getAPI_URL();
const ROTA_PERFIL_ONG = API_URL === "http://localhost:8080"
        || API_URL === "https://api.portoajuda.com.br"
    ? "/ong-profile"
    : "ong-profile.html";

const CATEGORIAS = {
    EDUCACAO: "Educação",
    SAUDE: "Saúde",
    MEIO_AMBIENTE: "Meio Ambiente",
    ANIMAIS: "Proteção animal",
    INCLUSAO_SOCIAL: "Inclusão social",
    ESPORTE: "Esporte",
    CULTURA: "Cultura",
    COMBATE_POBREZA: "Combate à pobreza",
    ALIMENTACAO: "Alimentação",
    IDOSOS: "Pessoas idosas"
};

function criarElemento(tag, classe, texto) {
    const elemento = document.createElement(tag);
    if (classe) elemento.className = classe;
    if (texto != null) elemento.textContent = texto;
    return elemento;
}

export function criarCardOng(ong) {
    const nome = typeof ong.nome === "string" && ong.nome.trim()
        ? ong.nome.trim()
        : "Organização sem nome";
    const causa = CATEGORIAS[ong.causaPrincipal]
        || (Array.isArray(ong.causas) && CATEGORIAS[ong.causas[0]])
        || "Organização social";
    const card = criarElemento("a", "card-osc");
    card.href = `${ROTA_PERFIL_ONG}?id=${encodeURIComponent(ong.id)}`;
    card.setAttribute("role", "listitem");
    card.setAttribute("aria-label", `Conhecer ${nome}, ${causa}`);

    const imagem = document.createElement("img");
    imagem.className = "card-osc-imagem";
    imagem.src = ong.logo || "../assets/ong-padrao.png";
    imagem.alt = `Imagem de ${nome}`;
    imagem.loading = "lazy";
    imagem.decoding = "async";
    imagem.addEventListener("error", () => {
        if (imagem.dataset.fallbackAplicado === "true") return;
        imagem.dataset.fallbackAplicado = "true";
        imagem.src = "../assets/ong-padrao.png";
    }, { once: true });

    const categoria = criarElemento("span", "card-osc-categoria", causa);
    categoria.setAttribute("aria-label", `Causa principal: ${causa}`);

    const informacoes = criarElemento("div", "card-osc-informacoes");
    informacoes.appendChild(criarElemento("h3", "card-osc-nome", nome));
    informacoes.appendChild(criarElemento("span", "card-osc-area", causa));
    const local = [ong.cidade, ong.estado].filter(Boolean).join(" - ");
    if (local) informacoes.appendChild(criarElemento("p", "card-osc-localizacao", local));

    const descricao = ong.descricaoCurta || ong.descricao;
    if (typeof descricao === "string" && descricao.trim()) {
        informacoes.appendChild(criarElemento("p", "card-osc-descricao", descricao.trim()));
    }

    const chamada = criarElemento("span", "card-osc-link", "Conhecer ONG");
    const seta = document.createElement("i");
    seta.className = "fa-solid fa-arrow-right";
    seta.setAttribute("aria-hidden", "true");
    chamada.appendChild(seta);
    informacoes.appendChild(chamada);

    card.append(imagem, categoria, informacoes);
    return card;
}

export function renderizarPaginaOngs(container, ongs) {
    const fragmento = document.createDocumentFragment();
    for (let inicio = 0; inicio < ongs.length; inicio += 3) {
        const linha = criarElemento("div", "ong-showcase-row");
        linha.setAttribute("role", "group");
        linha.setAttribute("aria-label", `Fileira ${Math.floor(inicio / 3) + 1}`);
        ongs.slice(inicio, inicio + 3).forEach(ong => linha.appendChild(criarCardOng(ong)));
        fragmento.appendChild(linha);
    }
    container.replaceChildren(fragmento);
}

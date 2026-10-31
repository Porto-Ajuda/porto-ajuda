
import { getAPI_URL } from "./modoBack.js";

const API_URL = getAPI_URL();

const ROTAS = API_URL === "http://localhost:8080"
    || API_URL === "${APP_BACKEND_URL}"
    ? {
        ongProfile: "/ong-profile"
    }
    : {
        ongProfile: "ong-profile.html"
    };

const cardsOng = document.querySelector("#cards-osc");
const carrossel = document.querySelector("#carrossel-ongs");
const botaoAnterior = document.querySelector("#ongs-anterior");
const botaoProximo = document.querySelector("#ongs-proximo");

const IMAGEM_PADRAO = "../assets/ong-padrao.png";

const iconesCategorias = {
    "Assistência Social": "🤝",
    "Proteção Animal": "🐾",
    "Alimentação": "🍲",
    "Idosos": "👴",
    "Meio Ambiente": "🌱",
    "Educação": "📚",
    "Esporte": "⚽",
    "Cultura": "🎭"
};

const categorias = {
    EDUCACAO: "Educação",
    SAUDE: "Assistência Social",
    MEIO_AMBIENTE: "Meio Ambiente",
    ANIMAIS: "Proteção Animal",
    INCLUSAO_SOCIAL: "Assistência Social",
    ESPORTE: "Esporte",
    CULTURA: "Cultura",
    COMBATE_POBREZA: "Assistência Social",
    ALIMENTACAO: "Alimentação",
    IDOSOS: "Idosos"
};

function criarElemento(tag, classe, texto) {
    const elemento = document.createElement(tag);

    if (classe) {
        elemento.className = classe;
    }

    if (texto !== undefined && texto !== null) {
        elemento.textContent = texto;
    }

    return elemento;
}

function formatarCategoria(causas) {
    if (!Array.isArray(causas) || causas.length === 0) {
        return "Organização social";
    }

    return categorias[causas[0]] || "Organização social";
}

function converterOngDaAPI(ong) {
    return {
        id: ong.id,
        nome: ong.nome || "Organização sem nome",
        imagem: ong.logo || IMAGEM_PADRAO,
        categoria: formatarCategoria(ong.causas),
        descricao: ong.descricaoCurta || ong.descricao || "",
        cidade: ong.cidade || "",
        estado: ong.estado || ""
    };
}

async function buscarOngs() {
    const organizacoes = [];
    let numeroPagina = 0;
    let ultimaPagina = false;

    do {
        const resposta = await fetch(
            `${API_URL}/osc/list?page=${numeroPagina}&size=100`,
            {
                headers: {
                    Accept: "application/json"
                }
            }
        );

        if (!resposta.ok) {
            throw new Error(
                `Falha ao carregar OSCs: HTTP ${resposta.status}`
            );
        }

        const pagina = await resposta.json();

        if (
            !pagina ||
            !Array.isArray(pagina.content) ||
            !Number.isInteger(pagina.totalPages)
        ) {
            throw new Error("A API retornou uma página inválida.");
        }

        organizacoes.push(...pagina.content);

        ultimaPagina =
            pagina.last === true ||
            numeroPagina + 1 >= pagina.totalPages;

        numeroPagina++;
    } while (!ultimaPagina);

    return organizacoes
        .filter(ong => ong && ong.ativo === true)
        .map(converterOngDaAPI);
}

function criarCardOng(ong) {
    const card = criarElemento("a", "card-osc");

    card.href =
        `${ROTAS.ongProfile}?id=${encodeURIComponent(ong.id)}`;

    card.setAttribute(
        "aria-label",
        `Conhecer ${ong.nome}, categoria ${ong.categoria}`
    );

    // Fotografia da organização
    const imagem = document.createElement("img");

    imagem.className = "card-osc-imagem";
    imagem.src = ong.imagem;
    imagem.alt = `Imagem de ${ong.nome}`;
    imagem.loading = "lazy";
    imagem.decoding = "async";

    imagem.addEventListener("error", () => {
        if (imagem.dataset.fallbackAplicado === "true") {
            return;
        }

        imagem.dataset.fallbackAplicado = "true";
        imagem.src = IMAGEM_PADRAO;
    });

    // Ícone da categoria principal
    const categoria = criarElemento(
        "span",
        "card-osc-categoria",
        iconesCategorias[ong.categoria] || "📍"
    );

    categoria.setAttribute(
        "aria-label",
        `Categoria: ${ong.categoria}`
    );

    categoria.title = ong.categoria;

    // Camada de informações
    const informacoes = criarElemento(
        "div",
        "card-osc-informacoes"
    );

    const nome = criarElemento(
        "h3",
        "card-osc-nome",
        ong.nome
    );

    const categoriaTexto = criarElemento(
        "span",
        "card-osc-area",
        ong.categoria
    );

    informacoes.append(nome, categoriaTexto);

    if (ong.cidade || ong.estado) {
        const localizacao = [ong.cidade, ong.estado]
            .filter(Boolean)
            .join(" - ");

        informacoes.appendChild(
            criarElemento(
                "p",
                "card-osc-localizacao",
                localizacao
            )
        );
    }

    if (ong.descricao.trim()) {
        informacoes.appendChild(
            criarElemento(
                "p",
                "card-osc-descricao",
                ong.descricao.trim()
            )
        );
    }

    const chamada = criarElemento(
        "span",
        "card-osc-link",
        "Conhecer ONG"
    );

    const seta = document.createElement("i");
    seta.className = "fa-solid fa-arrow-right";
    seta.setAttribute("aria-hidden", "true");

    chamada.appendChild(seta);
    informacoes.appendChild(chamada);

    card.append(imagem, categoria, informacoes);

    return card;
}

function atualizarControles() {
    if (!carrossel || !botaoAnterior || !botaoProximo) {
        return;
    }

    const limite =
        carrossel.scrollWidth - carrossel.clientWidth;

    botaoAnterior.disabled = carrossel.scrollLeft <= 1;
    botaoProximo.disabled =
        carrossel.scrollLeft >= limite - 1;
}

function moverCarrossel(direcao) {
    if (!carrossel) {
        return;
    }

    const primeiroCard = cardsOng?.querySelector(".card-osc");

    if (!primeiroCard) {
        return;
    }

    const estilos = getComputedStyle(cardsOng);
    const espacamento = parseFloat(estilos.columnGap) || 0;

    // Avança aproximadamente a largura de um card.
    const distancia =
        primeiroCard.getBoundingClientRect().width + espacamento;

    carrossel.scrollBy({
        left: distancia * direcao,
        behavior: "smooth"
    });
}

async function carregarCarrosselOngs() {
    if (!cardsOng || !carrossel) {
        console.error(
            "Carrossel de OSCs: não foram encontrados os contêineres necessários."
        );
        return;
    }

    cardsOng.setAttribute("aria-live", "polite");
    cardsOng.replaceChildren(
        criarElemento(
            "p",
            "mensagem-carrossel-ongs",
            "Carregando organizações..."
        )
    );

    try {
        const ongs = await buscarOngs();

        cardsOng.replaceChildren();

        if (ongs.length === 0) {
            cardsOng.appendChild(
                criarElemento(
                    "p",
                    "mensagem-carrossel-ongs",
                    "Nenhuma organização ativa foi encontrada."
                )
            );

            atualizarControles();
            return;
        }

        const fragmento = document.createDocumentFragment();

        ongs.forEach(ong => {
            fragmento.appendChild(criarCardOng(ong));
        });

        cardsOng.appendChild(fragmento);

        // Recalcula os limites depois que os cards são renderizados.
        requestAnimationFrame(atualizarControles);

    } catch (erro) {
        console.error(
            "Erro ao carregar organizações para o carrossel:",
            erro
        );

        cardsOng.replaceChildren(
            criarElemento(
                "p",
                "mensagem-carrossel-ongs",
                "Não foi possível carregar as organizações. Tente novamente mais tarde."
            )
        );

        atualizarControles();
    }
}

botaoAnterior?.addEventListener("click", () => {
    moverCarrossel(-1);
});

botaoProximo?.addEventListener("click", () => {
    moverCarrossel(1);
});

carrossel?.addEventListener(
    "scroll",
    atualizarControles,
    { passive: true }
);

window.addEventListener("resize", atualizarControles);

carregarCarrosselOngs();

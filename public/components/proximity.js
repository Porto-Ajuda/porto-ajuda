
import { getAPI_URL } from "./modoBack.js";
import {
    CAUSE_TAXONOMY,
    causeCategoriesForNgo,
    causeLabel
} from "./cause-taxonomy.js";

import {
    categoriasParaFiltro,
    filtrarOngs,
    sincronizarVisibilidadeOngs
} from "./proximity-filter.js";

import { abrirDetalhe } from "./ongs-painel.js";

const API_URL = getAPI_URL();

// ======================================================
// PAINEL DE DETALHES DA OSC
// ======================================================

function abrirDetalheSeguro(ong) {
    const painel = document.querySelector("#painel-detalhe");

    if (!painel || typeof abrirDetalhe !== "function") {
        return false;
    }

    try {
        abrirDetalhe(ong);
        return true;
    } catch (erro) {
        console.error("Falha ao abrir o painel da OSC:", erro);
        return false;
    }
}

// ======================================================
// ROTAS
// ======================================================

const ROTAS = API_URL === "http://localhost:8080"
    || API_URL === "https://api.portoajuda.com.br"
    ? {
        ongProfile: "/ong-profile"
    }
    : {
        ongProfile: "ong-profile.html"
    };

// ======================================================
// CONFIGURAÇÕES DO MAPA
// ======================================================

const posicaoInicial = [-24.090, -46.500];
const zoomInicial = 13;

let posicaoUsuario = null;

var map = L.map("map").setView(posicaoInicial, zoomInicial);

const camadaMarcadoresOngs = L.layerGroup().addTo(map);

let ongsCarregadas = [];

const marcadoresPorOngId = new Map();
const cartoesPorOngId = new Map();

let categoriaSelecionada = "";
let erroAoCarregarOngs = false;

L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    attribution:
        '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
}).addTo(map);

// ======================================================
// BOTÃO VOLTAR AO INÍCIO
// ======================================================

document.querySelector("#botao-inicio")?.addEventListener("click", () => {
    if (posicaoUsuario) {
        map.setView(posicaoUsuario, zoomInicial);
    } else {
        map.setView(posicaoInicial, zoomInicial);
    }
});

// ======================================================
// ÍCONE DA CATEGORIA
// ======================================================

function escolherIcone(categoriaId) {
    const classeIcone =
        CAUSE_TAXONOMY.find(categoria => categoria.id === categoriaId)?.icon
        || "fa-location-dot";

    return `
        <i
            class="fa-solid ${classeIcone} icone-categoria"
            aria-hidden="true"
        ></i>
    `;
}

// ======================================================
// SEDE PORTO AJUDA
// ======================================================

const iconeSede = L.icon({
    iconUrl: "../assets/logo-night.png",
    iconSize: [50, 50],
    iconAnchor: [25, 50],
    popupAnchor: [0, -50]
});

L.marker(
    [-24.00839596435377, -46.43535299604137],
    { icon: iconeSede }
)
    .addTo(map)
    .bindPopup("Porto Ajuda.<br>Sede.")
    .openPopup();

// ======================================================
// CALCULAR DISTÂNCIA
// ======================================================

function calcularDistancia(lat1, lon1, lat2, lon2) {
    const R = 6371;

    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;

    const a =
        Math.sin(dLat / 2) ** 2 +
        Math.cos(lat1 * Math.PI / 180) *
        Math.cos(lat2 * Math.PI / 180) *
        Math.sin(dLon / 2) ** 2;

    const c = 2 * Math.atan2(
        Math.sqrt(a),
        Math.sqrt(1 - a)
    );

    return R * c;
}

// ======================================================
// LOCALIZAR USUÁRIO
// ======================================================

function localizarUsuario() {
    return new Promise(resolve => {
        if (!navigator.geolocation) {
            resolve(null);
            return;
        }

        navigator.geolocation.getCurrentPosition(
            function (position) {
                const latitude = position.coords.latitude;
                const longitude = position.coords.longitude;

                posicaoUsuario = [latitude, longitude];

                map.setView(posicaoUsuario, zoomInicial);

                console.log("Latitude:", latitude);
                console.log("Longitude:", longitude);

                L.circleMarker([latitude, longitude], {
                    radius: 10,
                    fillColor: "red",
                    color: "black",
                    weight: 2,
                    fillOpacity: 1
                })
                    .addTo(map)
                    .bindPopup("Você está aqui!");

                resolve({
                    latitude,
                    longitude
                });
            },

            function (error) {
                console.log(
                    "Não foi possível obter a localização.",
                    error
                );

                resolve(null);
            }
        );
    });
}

// ======================================================
// DESTACAR OSC NA LISTA
// ======================================================

function destacarOng(ongId) {
    const cardOng = document.getElementById(`ong-${ongId}`);

    if (!cardOng) return;

    document
        .querySelectorAll(".ong.ong-selecionada")
        .forEach(card => {
            card.classList.remove("ong-selecionada");
        });

    cardOng.classList.add("ong-selecionada");

    cardOng.scrollIntoView({
        behavior: "smooth",
        block: "center"
    });

    setTimeout(() => {
        cardOng.classList.remove("ong-selecionada");
    }, 2500);
}

// ======================================================
// FORMATAR DADOS DA OSC
// ======================================================

function formatarCategoria(categorias) {
    const category = categorias[0];

    return category
        ? causeLabel(category)
        : "Organização social";
}

function formatarEndereco(ong) {
    return [
        ong.rua,
        ong.numero,
        ong.bairro,
        ong.cidade,
        ong.estado,
        ong.pais
    ]
        .filter(valor =>
            valor != null &&
            String(valor).trim() !== ""
        )
        .join(", ");
}

// ======================================================
// CALCULAR ANOS DE ATIVIDADE
// ======================================================

function calcularAnosAtividade(ong) {
    const anoAtual = new Date().getFullYear();

    let ano = Number(ong.anoFundacao);

    if (!Number.isFinite(ano) || ano <= 0) {
        ano = new Date(ong.dataFundacao).getFullYear();
    }

    return Number.isFinite(ano)
        && ano > 1800
        && ano <= anoAtual
        ? Math.max(anoAtual - ano, 0)
        : null;
}

// ======================================================
// NORMALIZAR TEXTO
// ======================================================

function normalizar(texto) {
    return String(texto ?? "")
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .toLowerCase()
        .trim();
}

// ======================================================
// CACHE DE PESQUISA
// ======================================================

const cacheBusca = new WeakMap();

function textoBuscaDaOng(ong) {
    if (!cacheBusca.has(ong)) {
        cacheBusca.set(
            ong,
            normalizar([
                ong.nome,
                ong.categoria,
                ong.categoriasBusca,
                ong.descricao,
                ong.endereco,
                (ong.ods || []).join(" ")
            ].join(" "))
        );
    }

    return cacheBusca.get(ong);
}

// ======================================================
// CONVERTER OSC DA API
// ======================================================

function converterOngDaAPI(ong) {
    const categorias = causeCategoriesForNgo(ong);

    return {
        id: ong.id,
        nome: ong.nome,
        imagem: ong.logo || "../assets/ong-padrao.png",

        categorias,

        categoria: formatarCategoria(categorias),

        categoriasBusca: categorias
            .map(causeLabel)
            .join(" "),

        descricao: ong.descricaoCurta || ong.descricao || "",

        endereco: formatarEndereco(ong),

        latitude: Number(ong.latitude),
        longitude: Number(ong.longitude),

        ods: Array.isArray(ong.ods)
            ? ong.ods
            : [],

        anos: calcularAnosAtividade(ong),

        urlPerfil:
            `${ROTAS.ongProfile}?id=${encodeURIComponent(ong.id)}`
    };
}

// ======================================================
// BUSCAR OSC NA API
// ======================================================

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
                "Não foi possível carregar as organizações próximas. Tente novamente."
            );
        }

        const pagina = await resposta.json();

        if (
            !pagina ||
            !Array.isArray(pagina.content) ||
            !Number.isInteger(pagina.totalPages)
        ) {
            throw new Error(
                "A API retornou uma página de OSCs inválida."
            );
        }

        organizacoes.push(...pagina.content);

        ultimaPagina =
            pagina.last === true ||
            numeroPagina + 1 >= pagina.totalPages;

        numeroPagina += 1;

    } while (!ultimaPagina);

    return organizacoes
        .filter(ong => ong && ong.status === "ATIVA")
        .map(converterOngDaAPI);
}

// ======================================================
// VALIDAR COORDENADAS
// ======================================================

function temCoordenadasValidas(ong) {
    return Number.isFinite(ong.latitude)
        && Number.isFinite(ong.longitude)
        && ong.latitude >= -90
        && ong.latitude <= 90
        && ong.longitude >= -180
        && ong.longitude <= 180
        && (ong.latitude !== 0 || ong.longitude !== 0);
}

// ======================================================
// CRIAR ELEMENTOS HTML
// ======================================================

function elemento(tag, texto, classe) {
    const node = document.createElement(tag);

    if (texto) {
        node.textContent = texto;
    }

    if (classe) {
        node.className = classe;
    }

    return node;
}

// ======================================================
// RENDERIZAR FILTROS DE CATEGORIA
// ======================================================

function renderizarFiltrosCategoria() {
    const filtros = document.querySelector(".filtros");

    if (!filtros) return;

    const resultado = categoriasParaFiltro(
        ongsCarregadas,
        CAUSE_TAXONOMY,
        categoriaSelecionada
    );

    categoriaSelecionada = resultado.categoriaSelecionada;

    filtros.replaceChildren();

    resultado.categorias.forEach(categoria => {
        const botao = document.createElement("button");

        botao.type = "button";
        botao.dataset.categoriaId = categoria.id;

        if (categoria.icon) {
            const icone = document.createElement("i");

            icone.className = `fa-solid ${categoria.icon}`;
            icone.setAttribute("aria-hidden", "true");

            botao.append(
                icone,
                document.createTextNode(` ${categoria.label}`)
            );
        } else {
            botao.textContent = categoria.label;
        }

        const ativa = categoria.id === categoriaSelecionada;

        botao.classList.toggle("ativo", ativa);
        botao.setAttribute("aria-pressed", String(ativa));

        filtros.appendChild(botao);
    });

    filtros
        .querySelector('button[data-categoria-id=""]')
        ?.setAttribute(
            "aria-label",
            "Mostrar todas as organizações"
        );
}

// ======================================================
// PESQUISA POR TEXTO
// ======================================================

function correspondeAoTexto(ong, termo) {
    const palavras = normalizar(termo)
        .split(/\s+/)
        .filter(Boolean);

    if (palavras.length === 0) {
        return true;
    }

    const texto = textoBuscaDaOng(ong);

    return palavras.every(palavra =>
        texto.includes(palavra)
    );
}

// ======================================================
// OSCs DA CATEGORIA SELECIONADA
// ======================================================

function ongsDaCategoria() {
    if (!categoriaSelecionada) {
        return ongsCarregadas;
    }

    try {
        const resultado = Array.from(
            filtrarOngs(
                ongsCarregadas,
                categoriaSelecionada,
                ""
            )
        );

        const ids = new Set(
            resultado.map(item => String(item?.id ?? item))
        );

        return ongsCarregadas.filter(ong =>
            ids.has(String(ong.id))
        );

    } catch (erro) {
        console.error(
            "Falha em filtrarOngs; usando filtro de categoria simples:",
            erro
        );

        return ongsCarregadas.filter(ong =>
            ong.categorias.includes(categoriaSelecionada)
        );
    }
}

// ======================================================
// MOSTRAR RESULTADOS NO MAPA
// ======================================================

function mostrarResultadosNoMapa() {
    const termo =
        document.querySelector(".barra-pesquisa input")?.value || "";

    const comCoordenadas = ongsDaCategoria()
        .filter(ong => correspondeAoTexto(ong, termo))
        .filter(temCoordenadasValidas);

    document
        .querySelector(".lista-ongs")
        ?.scrollTo?.({
            top: 0,
            behavior: "smooth"
        });

    if (comCoordenadas.length === 0) {
        return;
    }

    if (comCoordenadas.length === 1) {
        map.setView(
            [
                comCoordenadas[0].latitude,
                comCoordenadas[0].longitude
            ],
            17
        );

        return;
    }

    const painelAberto =
        !painelOngs.classList.contains("fechado")
        && window.innerWidth > 768;

    map.fitBounds(
        L.latLngBounds(
            comCoordenadas.map(ong => [
                ong.latitude,
                ong.longitude
            ])
        ),
        {
            paddingTopLeft: [
                painelAberto
                    ? painelOngs.offsetWidth + 30
                    : 30,
                30
            ],

            paddingBottomRight: [30, 30],

            maxZoom: 16
        }
    );
}

// ======================================================
// APLICAR FILTROS
// ======================================================

function aplicarFiltros() {
    const termo =
        document.querySelector(".barra-pesquisa input")?.value || "";

    const correspondentes = ongsDaCategoria()
        .filter(ong => correspondeAoTexto(ong, termo));

    const idsVisiveis = sincronizarVisibilidadeOngs(
        correspondentes,
        cartoesPorOngId,
        marcadoresPorOngId,
        camadaMarcadoresOngs
    );

    cartoesPorOngId.forEach(cartao => {
        cartao.style.display = cartao.hidden
            ? "none"
            : "";
    });

    const listaOngs = document.querySelector(".lista-ongs");

    if (!listaOngs) {
        return idsVisiveis;
    }

    let mensagemVazia = listaOngs.querySelector(
        ".mensagem-lista-ongs"
    );

    if (correspondentes.length === 0) {
        if (!mensagemVazia) {
            mensagemVazia = elemento(
                "p",
                "Nenhuma organização corresponde aos filtros.",
                "mensagem-lista-ongs"
            );

            listaOngs.appendChild(mensagemVazia);
        }

        mensagemVazia.hidden = false;

        mensagemVazia.textContent = erroAoCarregarOngs
            ? "Não foi possível carregar as organizações. Tente novamente mais tarde."
            : ongsCarregadas.length === 0
                ? "Nenhuma organização ativa foi encontrada."
                : "Nenhuma organização corresponde aos filtros.";

    } else if (mensagemVazia) {
        mensagemVazia.hidden = true;
    }

    document
        .querySelectorAll(".filtros button[data-categoria-id]")
        .forEach(botao => {
            const ativa =
                botao.dataset.categoriaId === categoriaSelecionada;

            botao.classList.toggle("ativo", ativa);
            botao.setAttribute("aria-pressed", String(ativa));
        });

    return idsVisiveis;
}

// ======================================================
// CONTEÚDO DO POPUP
// ======================================================

function criarConteudoPopup(ong) {
    const conteudo = document.createElement("div");

    conteudo.append(
        elemento("strong", ong.nome),
        document.createElement("br"),
        elemento("span", ong.categoria)
    );

    if (ong.endereco) {
        conteudo.append(
            document.createElement("br"),
            elemento("span", ong.endereco)
        );
    }

    return conteudo;
}

// ======================================================
// CRIAR CARTÃO DA OSC
// ======================================================

function criarCartao(ong, localizacao) {
    const card = elemento("article", null, "ong");

    card.id = `ong-${ong.id}`;
    card.tabIndex = 0;
    card.setAttribute("role", "button");

    // Imagem
    const molduraImagem = elemento(
        "div",
        null,
        "ong-imagem"
    );

    const imagem = document.createElement("img");

    imagem.src = ong.imagem;
    imagem.alt = `Logo de ${ong.nome}`;

    imagem.onerror = () => {
        imagem.src = "../assets/ong-padrao.png";
    };

    molduraImagem.appendChild(imagem);

    // Nome e anos de atividade
    const topo = elemento(
        "div",
        null,
        "ong-topo"
    );

    topo.appendChild(
        elemento("h3", ong.nome)
    );

    if (ong.anos != null) {
        topo.appendChild(
            elemento(
                "span",
                `Ativo a ${ong.anos} anos`,
                "ong-anos"
            )
        );
    }

    // Distância
    const distancia =
        localizacao && temCoordenadasValidas(ong)
            ? calcularDistancia(
                localizacao.latitude,
                localizacao.longitude,
                ong.latitude,
                ong.longitude
            )
            : null;

    const rodape = elemento(
        "div",
        null,
        "ong-rodape"
    );

    rodape.appendChild(
        elemento("span", ong.endereco)
    );

    rodape.appendChild(
        elemento(
            "span",
            distancia === null
                ? "Distância indisponível"
                : `a ${distancia.toFixed(1)} km`,
            "ong-distancia"
        )
    );

    // Informações
    const info = elemento(
        "div",
        null,
        "ong-info"
    );

    info.appendChild(topo);

    info.appendChild(
        elemento(
            "p",
            ong.descricao || ong.categoria
        )
    );

    info.appendChild(rodape);

    card.append(
        molduraImagem,
        info
    );

    // Link de perfil quando o painel não existe
    if (!document.querySelector("#painel-detalhe")) {
        const link = elemento(
            "a",
            "Ver OSC",
            "botao-ong"
        );

        link.href = ong.urlPerfil;

        card.appendChild(link);
    }

    // Clique no cartão
    const abrirCartao = () => {
        if (temCoordenadasValidas(ong)) {
            map.setView(
                [ong.latitude, ong.longitude],
                19
            );
        }

        if (window.innerWidth <= 768) {
            fecharPainelOngs();
        }

        abrirDetalheSeguro(ong);
    };

    card.addEventListener("click", evento => {
        if (evento.target.closest(".botao-ong")) {
            return;
        }

        abrirCartao();
    });

    card.addEventListener("keydown", evento => {
        if (
            evento.key === "Enter" ||
            evento.key === " "
        ) {
            evento.preventDefault();
            abrirCartao();
        }
    });

    // Marcador no mapa
    if (temCoordenadasValidas(ong)) {
        const marcador = L.marker(
            [ong.latitude, ong.longitude],
            {
                icon: L.divIcon({
                    html: escolherIcone(ong.categorias[0]),
                    className: "icone-ong",
                    iconSize: [45, 45],
                    iconAnchor: [22, 45]
                })
            }
        );

        marcador.on("click", () => {
            if (!painelOngs.classList.contains("fechado")) {
                destacarOng(ong.id);
            }

            if (!abrirDetalheSeguro(ong)) {
                L.popup({ offset: [0, -45] })
                    .setLatLng([
                        ong.latitude,
                        ong.longitude
                    ])
                    .setContent(
                        criarConteudoPopup(ong)
                    )
                    .openOn(map);
            }
        });

        marcadoresPorOngId.set(
            String(ong.id),
            marcador
        );
    }

    cartoesPorOngId.set(
        String(ong.id),
        card
    );

    return card;
}

// ======================================================
// CARREGAR OSCs
// ======================================================

async function carregarOngs() {
    const telaCarregamento =
        document.querySelector("#tela-carregamento");

    const listaOngs =
        document.querySelector(".lista-ongs");

    try {
        ongsCarregadas = await buscarOngs();

        renderizarFiltrosCategoria();

        const localizacao = ongsCarregadas.length > 0
            ? await Promise.race([
                localizarUsuario(),

                new Promise(resolve => {
                    setTimeout(
                        () => resolve(null),
                        10000
                    );
                })
            ])
            : null;

        listaOngs.replaceChildren();

        camadaMarcadoresOngs.clearLayers();

        cartoesPorOngId.clear();
        marcadoresPorOngId.clear();

        if (ongsCarregadas.length === 0) {
            aplicarFiltros();
            return;
        }

        ongsCarregadas.forEach(ong => {
            try {
                listaOngs.appendChild(
                    criarCartao(ong, localizacao)
                );
            } catch (erro) {
                console.error(
                    "Não foi possível montar a ONG",
                    ong?.id,
                    erro
                );
            }
        });

        aplicarFiltros();

    } catch (erro) {
        erroAoCarregarOngs = true;

        console.error(
            "Falha ao carregar OSCs de /osc/list:",
            erro
        );

        listaOngs.replaceChildren(
            elemento(
                "p",
                "Não foi possível carregar as organizações. Tente novamente mais tarde.",
                "mensagem-lista-ongs"
            )
        );

    } finally {
        telaCarregamento?.classList.add("oculto");
    }
}

// ======================================================
// EVENTOS DOS FILTROS
// ======================================================

document
    .querySelector(".filtros")
    ?.addEventListener("click", evento => {
        const filtros = evento.currentTarget;

        const botao = evento.target.closest(
            "button[data-categoria-id]"
        );

        if (!botao || !filtros.contains(botao)) {
            return;
        }

        categoriaSelecionada =
            botao.dataset.categoriaId;

        aplicarFiltros();
    });
// ======================================================
// SETAS DOS FILTROS DE CATEGORIA
// ======================================================

const containerFiltros = document.querySelector(".filtros");

const botoesSetas = document.querySelectorAll(
    ".seta-filtro"
);

botoesSetas.forEach(botao => {

    botao.addEventListener("click", () => {

        if (!containerFiltros) return;

        const direcao = botao.dataset.direcao;

        // Distância que os filtros irão percorrer
        const distancia = containerFiltros.clientWidth * 0.7;

        // Esquerda = valor negativo
        // Direita = valor positivo
        const deslocamento = direcao === "esquerda"
            ? -distancia
            : distancia;

        containerFiltros.scrollBy({
            left: deslocamento,
            behavior: "smooth"
        });

    });

});
// ======================================================
// INICIALIZAÇÃO
// ======================================================

renderizarFiltrosCategoria();

carregarOngs();

// ======================================================
// PAINEL DE OSCs
// ======================================================

const painelOngs =
    document.querySelector("#painel-ongs");

const botaoFecharOngs =
    document.querySelector("#botao-fechar-ongs");

const botaoAbrirOngs =
    document.querySelector("#botao-abrir-ongs");

function fecharPainelOngs() {
    painelOngs?.classList.add("fechado");
}

function abrirPainelOngs() {
    painelOngs?.classList.remove("fechado");

    setTimeout(() => {
        map.invalidateSize();
    }, 350);
}

// ======================================================
// PESQUISA
// ======================================================

const campoPesquisa =
    document.querySelector(".barra-pesquisa input");

campoPesquisa?.addEventListener(
    "input",
    aplicarFiltros
);

campoPesquisa?.addEventListener(
    "search",
    aplicarFiltros
);

campoPesquisa?.addEventListener(
    "keydown",
    evento => {
        if (evento.key === "Enter") {
            evento.preventDefault();

            aplicarFiltros();
            mostrarResultadosNoMapa();
        }
    }
);

document
    .querySelector(".barra-pesquisa button")
    ?.addEventListener("click", () => {
        aplicarFiltros();
        mostrarResultadosNoMapa();

        campoPesquisa?.focus();
    });

// ======================================================
// BOTÕES DO PAINEL
// ======================================================

botaoFecharOngs?.addEventListener(
    "click",
    fecharPainelOngs
);

botaoAbrirOngs?.addEventListener(
    "click",
    abrirPainelOngs
);

// ======================================================
// RESPONSIVIDADE
// ======================================================

// No celular, o painel começa fechado
if (window.innerWidth <= 768) {
    fecharPainelOngs();
}

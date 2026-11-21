import { getAPI_URL } from "./modoBack.js";
import { CAUSE_TAXONOMY, causeCategoriesForNgo, causeLabel } from "./cause-taxonomy.js";
import { categoriasParaFiltro, filtrarOngs, sincronizarVisibilidadeOngs } from "./proximity-filter.js";

const API_URL = getAPI_URL();

const ROTAS = API_URL === "http://localhost:8080"
    || API_URL === "https://api.portoajuda.com.br"
    ? {
        ongProfile: "/ong-profile",
    }
    : {
        ongProfile: "ong-profile.html",
    };

const posicaoInicial = [-24.090, -46.500];
const zoomInicial = 13;

let posicaoUsuario = null;

var map = L.map('map').setView(posicaoInicial, zoomInicial);
const camadaMarcadoresOngs = L.layerGroup().addTo(map);
let ongsCarregadas = [];
const marcadoresPorOngId = new Map();
const cartoesPorOngId = new Map();
let categoriaSelecionada = "";
let erroAoCarregarOngs = false;

L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
}).addTo(map);

// Voltar a posição original
document.querySelector("#botao-inicio").addEventListener("click", () => {

    if (posicaoUsuario) {

        map.setView(posicaoUsuario, zoomInicial);

    } else {

        map.setView(posicaoInicial, zoomInicial);

    }

});



function escolherIcone(categoriaId) {
    const classeIcone = CAUSE_TAXONOMY.find(categoria => categoria.id === categoriaId)?.icon || "fa-location-dot";
    return `<i class="fa-solid ${classeIcone} icone-categoria" aria-hidden="true"></i>`;
}

// Cadastrar sede Porto Ajuda
const iconeSede = L.icon({
    iconUrl: '../assets/logo-night.png',
    iconSize: [50, 50],
    iconAnchor: [25, 50],
    popupAnchor: [0, -50]
});

L.marker(
    [-24.00839596435377, -46.43535299604137],
    { icon: iconeSede }
)
    .addTo(map)
    .bindPopup('Porto Ajuda.<br>Sede.')
    .openPopup();

// Calculo distancia usuario e ongs
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

// Localizar usuario
function localizarUsuario() {
    return new Promise((resolve) => {

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
                    latitude: latitude,
                    longitude: longitude
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

function destacarOng(ongId) {

    const cardOng = document.getElementById(`ong-${ongId}`);

    if (!cardOng) return;

    // Remove destaque dos outros cards
    document
        .querySelectorAll(".ong.ong-selecionada")
        .forEach(card => {
            card.classList.remove("ong-selecionada");
        });

    // Destaca o card correto
    cardOng.classList.add("ong-selecionada");

    // Rola a lista até o card
    cardOng.scrollIntoView({
        behavior: "smooth",
        block: "center"
    });

    // Remove o destaque depois de 2,5 segundos
    setTimeout(() => {
        cardOng.classList.remove("ong-selecionada");
    }, 2500);
}

// Criar ongs

function formatarCategoria(categorias) {
    const category = categorias[0];
    return category ? causeLabel(category) : "Organização social";
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
        .filter(valor => valor != null && String(valor).trim() !== "")
        .join(", ");
}

function converterOngDaAPI(ong) {
    const categorias = causeCategoriesForNgo(ong);
    return {
        id: ong.id,
        nome: ong.nome,
        imagem: ong.logo || "../assets/ong-padrao.png",
        categorias,
        categoria: formatarCategoria(categorias),
        categoriasBusca: categorias.map(causeLabel).join(" "),
        descricao: ong.descricaoCurta || ong.descricao || "",
        endereco: formatarEndereco(ong),
        latitude: Number(ong.latitude),
        longitude: Number(ong.longitude),
        ods: Array.isArray(ong.ods) ? ong.ods : []
    };
}

async function buscarOngs() {
    const organizacoes = [];
    let numeroPagina = 0;
    let ultimaPagina = false;

    do {
        const resposta = await fetch(`${API_URL}/osc/list?page=${numeroPagina}&size=100`, {
            headers: { Accept: "application/json" }
        });
        if (!resposta.ok) {
            throw new Error("Não foi possível carregar as organizações próximas. Tente novamente.");
        }

        const pagina = await resposta.json();
        if (!pagina || !Array.isArray(pagina.content) || !Number.isInteger(pagina.totalPages)) {
            throw new Error("A API retornou uma página de OSCs inválida.");
        }
        organizacoes.push(...pagina.content);
        ultimaPagina = pagina.last === true || numeroPagina + 1 >= pagina.totalPages;
        numeroPagina += 1;
    } while (!ultimaPagina);

    return organizacoes
        .filter(ong => ong && ong.status === "ATIVA")
        .map(converterOngDaAPI);
}
function temCoordenadasValidas(ong) {
    return Number.isFinite(ong.latitude) && Number.isFinite(ong.longitude)
        && ong.latitude >= -90 && ong.latitude <= 90
        && ong.longitude >= -180 && ong.longitude <= 180
        && (ong.latitude !== 0 || ong.longitude !== 0);
}

function elemento(tag, texto, classe) {
    const node = document.createElement(tag);
    if (texto) node.textContent = texto;
    if (classe) node.className = classe;
    return node;
}

function renderizarFiltrosCategoria() {
    const filtros = document.querySelector(".filtros");
    const resultado = categoriasParaFiltro(ongsCarregadas, CAUSE_TAXONOMY, categoriaSelecionada);
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
            botao.append(icone, document.createTextNode(` ${categoria.label}`));
        } else {
            botao.textContent = categoria.label;
        }
        const ativa = categoria.id === categoriaSelecionada;
        botao.classList.toggle("ativo", ativa);
        botao.setAttribute("aria-pressed", String(ativa));
        filtros.appendChild(botao);
    });

    filtros.querySelector('button[data-categoria-id=""]')
        ?.setAttribute("aria-label", "Mostrar todas as organizações");
}

function aplicarFiltros() {
    const termo = document.querySelector(".barra-pesquisa input")?.value || "";
    const correspondentes = filtrarOngs(ongsCarregadas, categoriaSelecionada, termo);
    const idsVisiveis = sincronizarVisibilidadeOngs(
        correspondentes,
        cartoesPorOngId,
        marcadoresPorOngId,
        camadaMarcadoresOngs
    );
    const listaOngs = document.querySelector(".lista-ongs");
    let mensagemVazia = listaOngs.querySelector(".mensagem-lista-ongs");
    if (correspondentes.length === 0) {
        if (!mensagemVazia) {
            mensagemVazia = elemento("p", "Nenhuma organização corresponde aos filtros.", "mensagem-lista-ongs");
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

    document.querySelectorAll(".filtros button[data-categoria-id]").forEach(botao => {
        const ativa = botao.dataset.categoriaId === categoriaSelecionada;
        botao.classList.toggle("ativo", ativa);
        botao.setAttribute("aria-pressed", String(ativa));
    });
    return idsVisiveis;
}

function criarCartao(ong, localizacao) {
    const card = elemento("article", null, "ong");
    card.id = `ong-${ong.id}`;

    const molduraImagem = elemento("div", null, "ong-imagem");
    const imagem = document.createElement("img");
    imagem.src = ong.imagem;
    imagem.alt = `Logo de ${ong.nome}`;
    imagem.onerror = () => { imagem.src = "../assets/ong-padrao.png"; };
    molduraImagem.appendChild(imagem);

    const info = elemento("div", null, "ong-info");
    info.append(elemento("h3", ong.nome), elemento("p", ong.categoria));
    if (ong.descricao) info.appendChild(elemento("p", ong.descricao));
    if (ong.endereco) info.appendChild(elemento("p", ong.endereco));
    if (ong.ods.length) info.appendChild(elemento("span", `ODS: ${ong.ods.join(", ")}`));

    const distancia = localizacao && temCoordenadasValidas(ong)
        ? calcularDistancia(localizacao.latitude, localizacao.longitude, ong.latitude, ong.longitude)
        : null;
    info.appendChild(elemento("span", distancia === null ? "Distância indisponível" : `${distancia.toFixed(1)} km`));

    const link = elemento("a", "Ver OSC", "botao-ong");
    link.href = `${ROTAS.ongProfile}?id=${encodeURIComponent(ong.id)}`;
    card.append(molduraImagem, info, link);

    if (temCoordenadasValidas(ong)) {
        card.addEventListener("click", evento => {
            if (evento.target.closest(".botao-ong")) return;
            map.setView([ong.latitude, ong.longitude], 19);
            if (window.innerWidth <= 768) fecharPainelOngs();
        });

        const conteudoPopup = document.createElement("div");
        conteudoPopup.append(elemento("strong", ong.nome), document.createElement("br"), elemento("span", ong.categoria));
        if (ong.endereco) conteudoPopup.append(document.createElement("br"), elemento("span", ong.endereco));

        const marcador = L.marker([ong.latitude, ong.longitude], {
            icon: L.divIcon({
                html: escolherIcone(ong.categorias[0]),
                className: "icone-ong",
                iconSize: [45, 45],
                iconAnchor: [22, 45],
                popupAnchor: [0, -45]
            })
        }).bindPopup(conteudoPopup);
        marcador.on("click", () => {
            if (painelOngs.classList.contains("fechado")) {
                abrirPainelOngs();
                setTimeout(() => destacarOng(ong.id), 350);
            } else destacarOng(ong.id);
        });
        marcadoresPorOngId.set(String(ong.id), marcador);
    }
    cartoesPorOngId.set(String(ong.id), card);
    return card;
}
async function carregarOngs() {
    const telaCarregamento = document.querySelector("#tela-carregamento");
    const listaOngs = document.querySelector(".lista-ongs");
    try {
        ongsCarregadas = await buscarOngs();
        renderizarFiltrosCategoria();
        const localizacao = ongsCarregadas.length > 0
            ? await Promise.race([
                localizarUsuario(),
                new Promise(resolve => setTimeout(() => resolve(null), 10000))
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
        ongsCarregadas.forEach(ong => listaOngs.appendChild(criarCartao(ong, localizacao)));
        aplicarFiltros();
    } catch (erro) {
        erroAoCarregarOngs = true;
        console.error("Falha ao carregar OSCs de /osc/list:", erro);
        listaOngs.replaceChildren(elemento("p", "Não foi possível carregar as organizações. Tente novamente mais tarde.", "mensagem-lista-ongs"));
    } finally {
        telaCarregamento?.classList.add("oculto");
    }
}
document.querySelector(".filtros").addEventListener("click", evento => {
    const filtros = evento.currentTarget;
    const botao = evento.target.closest("button[data-categoria-id]");
    if (!botao || !filtros.contains(botao)) return;
    categoriaSelecionada = botao.dataset.categoriaId;
    aplicarFiltros();
});
renderizarFiltrosCategoria();
carregarOngs();

const painelOngs = document.querySelector("#painel-ongs");
const botaoFecharOngs = document.querySelector("#botao-fechar-ongs");
const botaoAbrirOngs = document.querySelector("#botao-abrir-ongs");

function fecharPainelOngs() {
    painelOngs.classList.add("fechado");
}

function abrirPainelOngs() {
    painelOngs.classList.remove("fechado");

    // Leaflet precisa recalcular o tamanho
    setTimeout(() => {
        map.invalidateSize();
    }, 350);
}

document.querySelector(".barra-pesquisa input")?.addEventListener("input", aplicarFiltros);
document.querySelector(".barra-pesquisa button")?.addEventListener("click", aplicarFiltros);
botaoFecharOngs.addEventListener("click", fecharPainelOngs);
botaoAbrirOngs.addEventListener("click", abrirPainelOngs);


// Celular começa fechado
if (window.innerWidth <= 768) {
    fecharPainelOngs();
}

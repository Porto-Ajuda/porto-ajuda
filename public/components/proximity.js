import { getAPI_URL } from "./modoBack.js";

const API_URL = getAPI_URL();

const ROTAS = API_URL === "http://localhost:8080"
    || API_URL === "${APP_BACKEND_URL}"
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

const iconesCategorias = {

    "Assistência Social": "🤝",
    "Proteção Animal": "🐾",
    "Alimentação": "🍲",
    "Idosos": "👴",
    "Meio Ambiente": "🌱",
    "Educação": "📚"

};

function escolherIcone(categoria) {

    return iconesCategorias[categoria] || "📍";

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


function formatarCategoria(causas) {
    const categorias = {
        EDUCACAO: "Educação",
        SAUDE: "Assistência Social",
        MEIO_AMBIENTE: "Meio Ambiente",
        ANIMAIS: "Proteção Animal",
        INCLUSAO_SOCIAL: "Assistência Social",
        ESPORTE: "Esporte",
        CULTURA: "Cultura",
        COMBATE_POBREZA: "Assistência Social"
    };

    if (!Array.isArray(causas) || causas.length === 0) {
        return "Organização social";
    }

    return categorias[causas[0]] || "Organização social";
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
    return {
        id: ong.id,
        nome: ong.nome,
        imagem: ong.logo || "../assets/ong-padrao.png",
        categoria: formatarCategoria(ong.causas),
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
            throw new Error(`Falha ao carregar OSCs: HTTP ${resposta.status}`);
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
        .filter(ong => ong && ong.ativo === true)
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

function aplicarBusca() {
    const termo = document.querySelector(".barra-pesquisa input")?.value.trim().toLocaleLowerCase("pt-BR") || "";
    document.querySelectorAll(".lista-ongs .ong").forEach(card => {
        card.hidden = !card.dataset.busca.includes(termo);
    });
}

function criarCartao(ong, localizacao) {
    const card = elemento("article", null, "ong");
    card.id = `ong-${ong.id}`;
    card.dataset.busca = `${ong.nome} ${ong.categoria} ${ong.endereco}`.toLocaleLowerCase("pt-BR");

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
            icon: L.divIcon({ html: escolherIcone(ong.categoria), className: "icone-ong", iconSize: [45, 45], iconAnchor: [22, 45] })
        }).bindPopup(conteudoPopup);
        marcador.on("click", () => {
            if (painelOngs.classList.contains("fechado")) {
                abrirPainelOngs();
                setTimeout(() => destacarOng(ong.id), 350);
            } else destacarOng(ong.id);
        });
        camadaMarcadoresOngs.addLayer(marcador);
    }
    return card;
}
async function carregarOngs() {
    const telaCarregamento = document.querySelector("#tela-carregamento");
    const listaOngs = document.querySelector(".lista-ongs");
    try {
        ongsCarregadas = await buscarOngs();
        const localizacao = ongsCarregadas.length > 0
            ? await Promise.race([
                localizarUsuario(),
                new Promise(resolve => setTimeout(() => resolve(null), 10000))
            ])
            : null;

        listaOngs.replaceChildren();
        camadaMarcadoresOngs.clearLayers();
        if (ongsCarregadas.length === 0) {
            listaOngs.appendChild(elemento("p", "Nenhuma organização ativa foi encontrada.", "mensagem-lista-ongs"));
            return;
        }
        ongsCarregadas.forEach(ong => listaOngs.appendChild(criarCartao(ong, localizacao)));
        aplicarBusca();
    } catch (erro) {
        console.error("Falha ao carregar OSCs de /osc/list:", erro);
        listaOngs.replaceChildren(elemento("p", "Não foi possível carregar as organizações. Tente novamente mais tarde.", "mensagem-lista-ongs"));
    } finally {
        telaCarregamento?.classList.add("oculto");
    }
}
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

document.querySelector(".barra-pesquisa input")?.addEventListener("input", aplicarBusca);
botaoFecharOngs.addEventListener("click", fecharPainelOngs);
botaoAbrirOngs.addEventListener("click", abrirPainelOngs);


// Celular começa fechado
if (window.innerWidth <= 768) {
    fecharPainelOngs();
}

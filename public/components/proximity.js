const posicaoInicial = [-24.090, -46.500];
const zoomInicial = 13;

let posicaoUsuario = null;

var map = L.map('map').setView(posicaoInicial, zoomInicial);

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
L.marker([-24.00839596435377, -46.43535299604137]).addTo(map)
    .bindPopup('Porto Ajuda.<br> Sede.')
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
    return new Promise((resolve, reject) => {

        navigator.geolocation.getCurrentPosition(function (position) {

            const latitude = position.coords.latitude;
            const longitude = position.coords.longitude;

            posicaoUsuario = [latitude, longitude];

            map.setView([latitude - 0.08, longitude - 0.05], zoomInicial);

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
                console.log("Não foi possível obter a localização.");
                reject(error);
            }
        );
    })

}



// Criar ongs

async function carregarOngs() {

    let localizacao = null;

    // Tenta localizar o usuário
    try {

        localizacao = await localizarUsuario();

    } catch (erro) {

        console.log("Localização do usuário não disponível.");

    }

    ongs.forEach(ong => {

        let distancia = null;

        // Só calcula distância se:
        // 1. O usuário foi localizado
        // 2. A ONG possui coordenadas
        if (
            localizacao &&
            ong.latitude != null &&
            ong.longitude != null
        ) {

            distancia = calcularDistancia(
                localizacao.latitude,
                localizacao.longitude,
                ong.latitude,
                ong.longitude
            );

        }

        const card = document.createElement("div");

        card.classList.add("ong");

        card.innerHTML = `
    <div class="ong-imagem">
        <img 
            src="${ong.imagem || '../assets/ong-padrao.png'}"
            alt="Imagem de ${ong.nome}"
        >
    </div>

    <div class="ong-info">

        <h3>${ong.nome}</h3>

        <p>${ong.categoria}</p>

        ${distancia !== null
                ? `<span>${distancia.toFixed(1)} km</span>`
                : `<span>Distância indisponível</span>`
            }

    </div>

    <a href="ong.html?id=${ong.id}" class="botao-ong">
        Ver ONG
    </a>
`;

        // Só permite clicar no mapa se houver coordenadas
        if (
            ong.latitude != null &&
            ong.longitude != null
        ) {

            card.addEventListener("click", () => {

                map.setView(
                    [
                        ong.latitude - 0.0020,
                        ong.longitude - 0.0020
                    ],
                    19
                );

            });

            const icone = L.divIcon({

                html: escolherIcone(ong.categoria),

                className: "icone-ong",

                iconSize: [45, 45],

                iconAnchor: [22, 45]

            });

            L.marker(
                [ong.latitude, ong.longitude],
                { icon: icone }
            )
                .addTo(map)
                .bindPopup(`
                    <strong>${ong.nome}</strong><br>
                    ${ong.categoria}<br>
                    ${ong.endereco}
                `);

        }

        document
            .querySelector(".lista-ongs")
            .appendChild(card);

    });
}

carregarOngs();
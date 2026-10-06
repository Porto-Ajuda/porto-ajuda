// ==========================
// ABRIR E FECHAR
// ==========================

const botao = document.querySelector("#botao-acessibilidade");
const painel = document.querySelector("#painel-acessibilidade");
const fechar = document.querySelector("#fechar-acessibilidade");

botao.addEventListener("click", () => {
    painel.classList.toggle("aberto");
});

fechar.addEventListener("click", () => {
    painel.classList.remove("aberto");
});

// ==========================
// PREFERÊNCIAS
// ==========================

const API_URL = "https://porto-ajuda.up.railway.app";

const CHAVE_PREFERENCIAS = "portoAjudaPreferencias";

let preferencias = {
    tamanhoFonte: 100,
    tema: "escuro",
    altoContraste: false,
    libras: false
};

function carregarPreferenciasLocais() {

    const dados = localStorage.getItem(CHAVE_PREFERENCIAS);

    if (!dados) {
        return;
    }

    try {

        const preferenciasSalvas = JSON.parse(dados);

        preferencias = {
            ...preferencias,
            ...preferenciasSalvas
        };

    } catch (erro) {

        console.error(
            "Erro ao carregar preferências locais:",
            erro
        );

    }
}


function salvarPreferenciasLocais() {

    localStorage.setItem(
        CHAVE_PREFERENCIAS,
        JSON.stringify(preferencias)
    );

}

// ==========================
// TAMANHO DA FONTE
// ==========================

const diminuirFonte = document.querySelector("#diminuir-fonte");
const aumentarFonte = document.querySelector("#aumentar-fonte");
const resetarFonte = document.querySelector("#resetar-fonte");
const textoTamanho = document.querySelector("#tamanho-fonte");

function atualizarFonte() {

    document.documentElement.style.fontSize =
        tamanhoFonte + "%";

    textoTamanho.textContent =
        tamanhoFonte + "%";

    preferencias.tamanhoFonte = tamanhoFonte;

    salvarPreferenciasLocais();
}


aumentarFonte.addEventListener("click", () => {

    if (tamanhoFonte < 150) {
        tamanhoFonte += 10;
        atualizarFonte();
    }

});


diminuirFonte.addEventListener("click", () => {

    if (tamanhoFonte > 80) {
        tamanhoFonte -= 10;
        atualizarFonte();
    }

});


resetarFonte.addEventListener("click", () => {

    tamanhoFonte = 100;
    atualizarFonte();

});


// ==========================
// MODO CLARO
// ==========================

const alternarTema =
    document.querySelector("#alternar-tema");

alternarTema.addEventListener("click", () => {

    document.body.classList.toggle("modo-claro");

    if (document.body.classList.contains("modo-claro")) {

        preferencias.tema = "claro";

        alternarTema.textContent =
            "🌙 Modo Escuro";

    } else {

        preferencias.tema = "escuro";

        alternarTema.textContent =
            "☀️ Modo Claro";
    }

    salvarPreferenciasLocais();
});


// ==========================
// ALTO CONTRASTE
// ==========================

const altoContraste =
    document.querySelector("#alto-contraste");

altoContraste.addEventListener("click", () => {

    document.body.classList.toggle("alto-contraste");

    preferencias.altoContraste =
        document.body.classList.contains("alto-contraste");

    salvarPreferenciasLocais();
});

const botaoLibras =
    document.querySelector("#ativar-libras");

botaoLibras.addEventListener("click", async () => {

    try {

        const botaoVLibras =
            await window.carregarVLibras();

        // Abre o VLibras
        botaoVLibras.click();

        vlibrasInicializado = true;

        preferencias.libras = true;

        salvarPreferenciasLocais();

        // Fecha o painel de acessibilidade
        painel.classList.remove("aberto");

    } catch (erro) {

        console.error(
            "Erro ao ativar o VLibras:",
            erro
        );

    }

});


async function aplicarPreferencias() {

    // ==========================
    // TAMANHO DA FONTE
    // ==========================

    tamanhoFonte = preferencias.tamanhoFonte;

    document.documentElement.style.fontSize =
        tamanhoFonte + "%";

    textoTamanho.textContent =
        tamanhoFonte + "%";


    // ==========================
    // TEMA
    // ==========================

    if (preferencias.tema === "claro") {

        document.body.classList.add("modo-claro");

        alternarTema.textContent =
            "🌙 Modo Escuro";

    } else {

        document.body.classList.remove("modo-claro");

        alternarTema.textContent =
            "☀️ Modo Claro";
    }


    // ==========================
    // ALTO CONTRASTE
    // ==========================

    document.body.classList.toggle(
        "alto-contraste",
        preferencias.altoContraste
    );

    // ==========================
    // LIBRAS
    // ==========================

    if (preferencias.libras) {

        try {

            const botaoVLibras =
                await window.carregarVLibras();

            botaoVLibras.click();

        } catch (erro) {

            console.error(
                "Erro ao restaurar o VLibras:",
                erro
            );

        }

    }
}

// ==========================
// MONITORAR ESTADO DO VLIBRAS
// ==========================

let vlibrasInicializado = false;

function verificarEstadoVLibras() {

    const dadosVLibras =
        localStorage.getItem("@vlibras-widget");

    if (!dadosVLibras) {
        return;
    }

    try {

        const dados = JSON.parse(dadosVLibras);

        const aberto =
            dados?.state?.isOpen;

        // Ignora o estado inicial
        if (!vlibrasInicializado) {

            if (aberto === true) {
                vlibrasInicializado = true;
            }

            return;
        }

        // ==========================
        // ABRIU
        // ==========================

        if (aberto === true && preferencias.libras !== true) {

            preferencias.libras = true;

            salvarPreferenciasLocais();

            console.log("VLibras aberto → libras = true");
        }

        // ==========================
        // FECHOU
        // ==========================

        if (aberto === false && preferencias.libras === true) {

            preferencias.libras = false;

            salvarPreferenciasLocais();

            console.log("VLibras fechado → libras = false");
        }

    } catch (erro) {

        console.error(
            "Erro ao verificar estado do VLibras:",
            erro
        );

    }
}

setInterval(verificarEstadoVLibras, 300);

async function carregarPreferenciasBackend() {

    try {

        const resposta = await fetch(
            `${API_URL}/usuario/preferencias`,
            {
                method: "GET",
                credentials: "include"
            }
        );

        if (!resposta.ok) {
            throw new Error(
                "Backend não respondeu corretamente."
            );
        }

        const dados = await resposta.json();

        // O backend respondeu
        preferencias = {
            ...preferencias,
            ...dados
        };

        // Atualiza o cache local
        salvarPreferenciasLocais();

        // Aplica o que veio do backend
        aplicarPreferencias();

        return true;

    } catch (erro) {

        console.warn(
            "Backend indisponível. Usando preferências locais."
        );

        // Mantém o que já estava salvo localmente
        aplicarPreferencias();

        return false;
    }
}

carregarPreferenciasLocais();

aplicarPreferencias();

carregarPreferenciasBackend();
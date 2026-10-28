import { getAPI_URL } from "./modoBack.js";

// ==========================
// CONFIGURAÇÃO
// ==========================

const API_URL = await getAPI_URL();

const CHAVE_PREFERENCIAS = "portoAjudaPreferencias";

// ==========================
// ELEMENTOS DO DOM
// ==========================

// CONTROLES DO PAINEL DE CONFIGURAÇÕES

const configLeituraTela =
    document.querySelector("#acss-leitura-tela");

const configLibras =
    document.querySelector("#acss-libras");

const configTamanhoLetra =
    document.querySelector("#acss-tamanho-letra");

const configModoExibicao =
    document.querySelector("#acss-modo-exibicao");

const botao = document.querySelector("#botao-acessibilidade");
const painel = document.querySelector("#painel-acessibilidade");
const fechar = document.querySelector("#fechar-acessibilidade");

const diminuirFonte = document.querySelector("#diminuir-fonte");
const aumentarFonte = document.querySelector("#aumentar-fonte");
const resetarFonte = document.querySelector("#resetar-fonte");
const textoTamanho = document.querySelector("#tamanho-fonte");

const alternarTema = document.querySelector("#alternar-tema");
const altoContraste = document.querySelector("#alto-contraste");
const botaoLibras = document.querySelector("#ativar-libras");

// ==========================
// ESTADO
// ==========================

let preferencias = {
    tamanhoFonte: 100,
    tema: "escuro",
    altoContraste: false,
    libras: false
};

let tamanhoFonte = preferencias.tamanhoFonte;
let vlibrasInicializado = false;

// ==========================
// ABRIR E FECHAR
// ==========================

botao?.addEventListener("click", () => {
    painel?.classList.toggle("aberto");
});

fechar?.addEventListener("click", () => {
    painel?.classList.remove("aberto");
});

// ==========================
// NORMALIZAR PREFERÊNCIAS
// ==========================

function normalizarTamanhoFonte(valor) {
    const numero = Number(valor);

    if (!Number.isFinite(numero)) {
        return 100;
    }

    return Math.min(150, Math.max(80, numero));
}

function normalizarPreferencias(dados = {}) {
    return {
        tamanhoFonte: normalizarTamanhoFonte(
            dados.tamanhoFonte ?? 100
        ),

        tema: dados.tema === "claro" ? "claro" : "escuro",

        altoContraste: dados.altoContraste === true,

        libras: dados.libras === true
    };
}


function sincronizarControlesConfiguracao() {
    // Tamanho da letra
    if (configTamanhoLetra) {
        const tamanho = preferencias.tamanhoFonte;

        if (tamanho <= 95) {
            configTamanhoLetra.value = "pequeno";
        } else if (tamanho <= 110) {
            configTamanhoLetra.value = "medio";
        } else if (tamanho <= 130) {
            configTamanhoLetra.value = "grande";
        } else {
            configTamanhoLetra.value = "extra-grande";
        }
    }

    // Modo de exibição
    if (configModoExibicao) {
        configModoExibicao.value =
            preferencias.altoContraste
                ? "alto-contraste"
                : preferencias.tema;
    }

    // VLibras
    if (configLibras) {
        configLibras.checked = preferencias.libras;
    }
}


// ==========================
// PREFERÊNCIAS LOCAIS
// ==========================

function carregarPreferenciasLocais() {
    const dados = localStorage.getItem(CHAVE_PREFERENCIAS);

    if (!dados) {
        return;
    }

    try {
        const preferenciasSalvas = JSON.parse(dados);

        if (
            !preferenciasSalvas ||
            typeof preferenciasSalvas !== "object" ||
            Array.isArray(preferenciasSalvas)
        ) {
            throw new Error("Formato de preferências inválido.");
        }

        preferencias = normalizarPreferencias({
            ...preferencias,
            ...preferenciasSalvas
        });

    } catch (erro) {
        console.error(
            "Erro ao carregar preferências locais:",
            erro
        );
    }
}

function salvarPreferenciasLocais() {
    try {
        localStorage.setItem(
            CHAVE_PREFERENCIAS,
            JSON.stringify(preferencias)
        );
    } catch (erro) {
        console.error(
            "Erro ao salvar preferências locais:",
            erro
        );
    }
}

// ==========================
// TAMANHO DA FONTE
// ==========================

function atualizarFonte() {
    tamanhoFonte = normalizarTamanhoFonte(tamanhoFonte);

    document.documentElement.style.fontSize =
        `${tamanhoFonte}% `;

    if (textoTamanho) {
        textoTamanho.textContent = `${tamanhoFonte}% `;
    }

    preferencias.tamanhoFonte = tamanhoFonte;

    salvarPreferenciasLocais();
}

const tamanhosFonte = {
    "pequeno": 90,
    "medio": 100,
    "grande": 120,
    "extra-grande": 140
};

configTamanhoLetra?.addEventListener("change", () => {
    const novoTamanho = tamanhosFonte[configTamanhoLetra.value];

    if (novoTamanho === undefined) {
        return;
    }

    tamanhoFonte = novoTamanho;
    atualizarFonte();
});


aumentarFonte?.addEventListener("click", () => {
    if (tamanhoFonte < 150) {
        tamanhoFonte = Math.min(150, tamanhoFonte + 10);
        atualizarFonte();
    }
});

diminuirFonte?.addEventListener("click", () => {
    if (tamanhoFonte > 80) {
        tamanhoFonte = Math.max(80, tamanhoFonte - 10);
        atualizarFonte();
    }
});

resetarFonte?.addEventListener("click", () => {
    tamanhoFonte = 100;
    atualizarFonte();
});

// ==========================
// MODO CLARO / ESCURO
// ==========================

alternarTema?.addEventListener("click", () => {
    const modoClaroAtivo =
        document.body.classList.toggle("modo-claro");

    preferencias.tema = modoClaroAtivo ? "claro" : "escuro";

    atualizarTextoBotaoTema();
    salvarPreferenciasLocais();
});

function atualizarTextoBotaoTema() {
    if (!alternarTema) {
        return;
    }

    alternarTema.textContent =
        preferencias.tema === "claro"
            ? "🌙 Modo Escuro"
            : "☀️ Modo Claro";
}

// ==========================
// ALTO CONTRASTE
// ==========================

altoContraste?.addEventListener("click", () => {
    const ativo =
        document.body.classList.toggle("alto-contraste");

    preferencias.altoContraste = ativo;

    salvarPreferenciasLocais();
});

configModoExibicao?.addEventListener("change", () => {
    const modo = configModoExibicao.value;

    if (!["escuro", "claro", "alto-contraste"].includes(modo)) {
        return;
    }

    if (modo === "alto-contraste") {
        preferencias.altoContraste = true;
    } else {
        preferencias.tema = modo;
        preferencias.altoContraste = false;
    }

    document.body.classList.toggle(
        "modo-claro",
        preferencias.tema === "claro"
    );

    document.body.classList.toggle(
        "alto-contraste",
        preferencias.altoContraste
    );

    atualizarTextoBotaoTema();
    salvarPreferenciasLocais();
    sincronizarControlesConfiguracao();
});


// ==========================
// VLIBRAS
// ==========================

botaoLibras?.addEventListener("click", async () => {
    try {
        if (typeof window.carregarVLibras !== "function") {
            throw new Error(
                "A função carregarVLibras não está disponível."
            );
        }

        const botaoVLibras = await window.carregarVLibras();

        if (!botaoVLibras) {
            throw new Error(
                "Não foi possível obter o botão do VLibras."
            );
        }

        botaoVLibras.click();

        vlibrasInicializado = true;
        preferencias.libras = true;

        salvarPreferenciasLocais();

        painel?.classList.remove("aberto");

    } catch (erro) {
        console.error("Erro ao ativar o VLibras:", erro);
    }
});


function obterEstadoAtualVLibras() {
    try {
        const dados = JSON.parse(
            localStorage.getItem("@vlibras-widget") || "null"
        );

        return typeof dados?.state?.isOpen === "boolean"
            ? dados.state.isOpen
            : null;
    } catch {
        return null;
    }
}

async function definirVLibras(ativar) {
    if (ativar === preferencias.libras) {
        return;
    }

    if (ativar) {
        if (typeof window.carregarVLibras !== "function") {
            throw new Error("VLibras não está disponível.");
        }

        const botaoVLibras = await window.carregarVLibras();

        if (!botaoVLibras) {
            throw new Error("Não foi possível carregar o VLibras.");
        }

        if (obterEstadoAtualVLibras() !== true) {
            botaoVLibras.click();
        }
    } else {
        const estadoAtual = obterEstadoAtualVLibras();

        if (estadoAtual === true) {
            const botaoVLibras = await window.carregarVLibras();

            if (!botaoVLibras) {
                throw new Error("Não foi possível acessar o VLibras.");
            }

            botaoVLibras.click();
        }
    }

    preferencias.libras = ativar;
    vlibrasInicializado = true;

    salvarPreferenciasLocais();
    sincronizarControlesConfiguracao();
}

configLibras?.addEventListener("change", async () => {
    configLibras.disabled = true;

    try {
        await definirVLibras(configLibras.checked);
    } catch (erro) {
        console.error("Erro ao alterar o VLibras:", erro);

        configLibras.checked = preferencias.libras;
    } finally {
        configLibras.disabled = false;
    }
});

// ==========================
// APLICAR PREFERÊNCIAS
// ==========================

async function aplicarPreferencias() {
    // Tamanho da fonte

    tamanhoFonte = normalizarTamanhoFonte(
        preferencias.tamanhoFonte
    );

    preferencias.tamanhoFonte = tamanhoFonte;

    document.documentElement.style.fontSize =
        `${tamanhoFonte}% `;

    if (textoTamanho) {
        textoTamanho.textContent = `${tamanhoFonte}% `;
    }

    // Tema

    document.body.classList.toggle(
        "modo-claro",
        preferencias.tema === "claro"
    );

    atualizarTextoBotaoTema();

    // Alto contraste

    document.body.classList.toggle(
        "alto-contraste",
        preferencias.altoContraste
    );

    // VLibras

    if (preferencias.libras) {
        try {
            if (typeof window.carregarVLibras !== "function") {
                throw new Error(
                    "A função carregarVLibras não está disponível."
                );
            }

            const botaoVLibras =
                await window.carregarVLibras();

            if (botaoVLibras) {
                botaoVLibras.click();
                vlibrasInicializado = true;
            }

        } catch (erro) {
            console.error(
                "Erro ao restaurar o VLibras:",
                erro
            );
        }
    }

    sincronizarControlesConfiguracao();
}

// ==========================
// MONITORAR ESTADO DO VLIBRAS
// ==========================

function verificarEstadoVLibras() {
    const dadosVLibras =
        localStorage.getItem("@vlibras-widget");

    if (!dadosVLibras) {
        return;
    }

    try {
        const dados = JSON.parse(dadosVLibras);

        const aberto = dados?.state?.isOpen;

        // Aguarda a identificação do estado inicial.
        if (!vlibrasInicializado) {
            if (aberto === true) {
                vlibrasInicializado = true;
            }

            return;
        }

        // VLibras aberto
        if (
            aberto === true &&
            preferencias.libras !== true
        ) {
            preferencias.libras = true;
            salvarPreferenciasLocais();
        }

        // VLibras fechado
        if (
            aberto === false &&
            preferencias.libras !== false
        ) {
            preferencias.libras = false;
            salvarPreferenciasLocais();
        }

    } catch (erro) {
        console.error(
            "Erro ao verificar estado do VLibras:",
            erro
        );
    }
}

setInterval(verificarEstadoVLibras, 300);

// ==========================
// CARREGAR PREFERÊNCIAS DO BACKEND
// ==========================

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
                `Falha ao carregar preferências: HTTP ${resposta.status} `
            );
        }

        const dados = await resposta.json();

        if (
            !dados ||
            typeof dados !== "object" ||
            Array.isArray(dados)
        ) {
            throw new Error(
                "O backend retornou preferências inválidas."
            );
        }

        // O backend passa a ser a fonte prioritária
        // quando responde corretamente.

        preferencias = normalizarPreferencias({
            ...preferencias,
            ...dados
        });

        tamanhoFonte = preferencias.tamanhoFonte;

        // Atualiza o cache local com os dados do backend.
        salvarPreferenciasLocais();

        return true;

    } catch (erro) {
        console.warn(
            "Não foi possível carregar as preferências do backend. " +
            "Serão utilizadas as preferências locais.",
            erro
        );

        return false;
    }
}

// ==========================
// INICIALIZAÇÃO
// ==========================

async function inicializarPreferencias() {
    // Aplica primeiro as preferências locais.
    carregarPreferenciasLocais();

    tamanhoFonte = preferencias.tamanhoFonte;

    await aplicarPreferencias();

    // Depois tenta obter as preferências do backend.
    const carregouBackend =
        await carregarPreferenciasBackend();

    if (carregouBackend) {
        await aplicarPreferencias();
    }
}

inicializarPreferencias();


export async function salvarPreferenciasNoBackend() {
    const dados = {
        tamanhoFonte: preferencias.tamanhoFonte,
        tema: preferencias.tema,
        altoContraste: preferencias.altoContraste,
        libras: preferencias.libras
    };

    const resposta = await fetch(
        `${API_URL}/usuario/preferencias`,
        {
            method: "PUT",
            credentials: "include",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(dados)
        }
    );

    if (!resposta.ok) {
        throw new Error(
            `Não foi possível salvar as preferências (HTTP ${resposta.status}).`
        );
    }

    return await resposta.json();
}

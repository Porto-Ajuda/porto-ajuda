
/*
 * ongs-painel.js
 *
 * Responsável por:
 * - Abrir o painel de detalhes da OSC
 * - Exibir logo, nome, descrição e endereço
 * - Copiar o endereço
 * - Abrir o endereço no Google Maps
 * - Fechar o painel de detalhes
 */

const painelDetalhe = document.querySelector("#painel-detalhe");
const overlayDetalhe = document.querySelector("#overlay-detalhe");

const nomeDetalhe = document.querySelector("#detalhe-nome");
const descricaoDetalhe = document.querySelector("#detalhe-desc");
const enderecoDetalhe = document.querySelector("#detalhe-endereco");
const containerLogo = document.querySelector("#detalhe-logo");

const botaoCopiar = document.querySelector("#btn-copiar");
const mensagemCopiado = document.querySelector("#msg-copiado");
const botaoMaps = document.querySelector("#btn-maps");
const botaoFechar = document.querySelector("#detalhe-fechar");
const botaoPerfil = document.querySelector("#btn-perfil");

let ongAberta = null;
let timerCopiado = null;


/* =========================
   GERAR LINK DO GOOGLE MAPS
========================= */

function criarLinkGoogleMaps(endereco) {
    return (
        "https://www.google.com/maps/search/?api=1&query=" +
        encodeURIComponent(endereco)
    );
}


/* =========================
   ATUALIZAR LOGO DA OSC
========================= */

function atualizarLogo(ong) {
    if (!containerLogo) return;

    const imagemLogo = document.createElement("img");

    imagemLogo.alt = `Logo da ${ong.nome || "organização"}`;

    /*
     * O objeto da ONG pode conter a imagem
     * nas propriedades imagem ou logo.
     */
    const caminhoLogo =
        ong.imagem ||
        ong.logo ||
        "../assets/ong-padrao.png";

    imagemLogo.src = caminhoLogo;

    /*
     * Se a imagem não carregar,
     * utiliza a logo padrão.
     */
    imagemLogo.onerror = () => {
        imagemLogo.onerror = null;
        imagemLogo.src = "../assets/ong-padrao.png";
    };

    /*
     * Substitui a imagem anterior
     * pela logo da ONG selecionada.
     */
    containerLogo.replaceChildren(imagemLogo);
}


/* =========================
   ABRIR PAINEL DE DETALHES
========================= */

export function abrirDetalhe(ong) {
    if (!painelDetalhe || !ong) {
        console.warn(
            "Painel de detalhes ou dados da OSC não encontrados."
        );

        return;
    }

    ongAberta = ong;

    const endereco =
        ong.endereco || "Endereço não informado";

    const linkMaps = criarLinkGoogleMaps(endereco);


    /* Logo da OSC */

    atualizarLogo(ong);


    /* Nome da OSC */

    if (nomeDetalhe) {
        nomeDetalhe.textContent =
            ong.nome || "Organização social";
    }


    /* Descrição da OSC */

    if (descricaoDetalhe) {
        descricaoDetalhe.textContent =
            ong.descricao || "Descrição não informada.";
    }


    /* Informações complementares */

    const subtituloDetalhe =
        document.querySelector("#detalhe-sub");

    if (subtituloDetalhe) {
        subtituloDetalhe.textContent =
            ong.tipo || ong.categoria || "";
    }


    /* Endereço */

    if (enderecoDetalhe) {
        enderecoDetalhe.textContent = endereco;
        enderecoDetalhe.href = linkMaps;
    }


    /* Botão Google Maps */

    if (botaoMaps) {
        botaoMaps.href = linkMaps;
        botaoMaps.target = "_blank";
        botaoMaps.rel = "noopener noreferrer";
    }


    /* Botão perfil da OSC */

    if (botaoPerfil) {
        botaoPerfil.href =
            ong.urlPerfil ||
            ong.linkPerfil ||
            "#";
    }


    /* Limpar mensagem anterior */

    if (mensagemCopiado) {
        mensagemCopiado.textContent = "";
    }

    clearTimeout(timerCopiado);


    /* Mostrar overlay */

    if (overlayDetalhe) {
        overlayDetalhe.hidden = false;
        overlayDetalhe.classList.add("ativo");
    }


    /* Mostrar painel */

    painelDetalhe.classList.add("aberto");

    painelDetalhe.setAttribute(
        "aria-hidden",
        "false"
    );
}


/* =========================
   FECHAR PAINEL
========================= */

function fecharDetalhe() {
    if (!painelDetalhe) return;

    painelDetalhe.classList.remove("aberto");

    painelDetalhe.setAttribute(
        "aria-hidden",
        "true"
    );

    if (overlayDetalhe) {
        overlayDetalhe.classList.remove("ativo");
        overlayDetalhe.hidden = true;
    }

    ongAberta = null;

    clearTimeout(timerCopiado);

    if (mensagemCopiado) {
        mensagemCopiado.textContent = "";
    }
}


/* =========================
   EVENTOS DE FECHAMENTO
========================= */

botaoFechar?.addEventListener(
    "click",
    fecharDetalhe
);

overlayDetalhe?.addEventListener(
    "click",
    fecharDetalhe
);


/* =========================
   COPIAR ENDEREÇO
========================= */

async function copiarEndereco() {
    if (!ongAberta || !ongAberta.endereco) {
        return false;
    }

    try {
        await navigator.clipboard.writeText(
            ongAberta.endereco
        );

        return true;

    } catch (erro) {
        console.error(
            "Erro ao copiar endereço:",
            erro
        );

        return false;
    }
}


/* =========================
   MOSTRAR MENSAGEM
========================= */

function mostrarMensagemCopiado(sucesso) {
    if (!mensagemCopiado) return;

    mensagemCopiado.textContent = sucesso
        ? "Endereço copiado!"
        : "Não foi possível copiar o endereço.";

    clearTimeout(timerCopiado);

    timerCopiado = setTimeout(() => {
        mensagemCopiado.textContent = "";
    }, 3000);
}


/* =========================
   BOTÃO COPIAR ENDEREÇO
========================= */

botaoCopiar?.addEventListener(
    "click",
    async () => {
        const sucesso = await copiarEndereco();

        mostrarMensagemCopiado(sucesso);
    }
);


/* =========================
   CLIQUE NO ENDEREÇO
========================= */

// O endereço abre o Google Maps.
// Também tentamos copiar o endereço.

enderecoDetalhe?.addEventListener(
    "click",
    async () => {
        if (!ongAberta || !ongAberta.endereco) {
            return;
        }

        const sucesso = await copiarEndereco();

        mostrarMensagemCopiado(sucesso);
    }
);

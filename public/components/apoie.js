// =========================================================
// CONFIGURAÇÕES DO PIX
// =========================================================

const chavePix = "tccportoajuda@gmail.com";
const nomeRecebedor = "PORTO AJUDA";
const cidadeRecebedor = "PRAIA GRANDE";


// =========================================================
// ELEMENTOS DO HTML
// =========================================================

const botoesValor = document.querySelectorAll(".valor-pix");

const botaoOutro = document.getElementById("botao-outro");

const campoOutroValor = document.querySelector(".outro-valor");

const inputValor = document.getElementById("valor");

const botaoGerar = document.getElementById("gerar-pix");

const resultadoPix = document.getElementById("resultado-pix");

const qrCode = document.getElementById("qrcode");

const valorExibido = document.getElementById("valor-exibido");

const pixCopiaCola = document.getElementById("pix-copia-cola");

const botaoCopiar = document.getElementById("copiar-pix");

const mensagemCopia = document.getElementById("mensagem-copia");


// =========================================================
// LIMPAR TEXTO
// =========================================================

function limparTexto(texto) {

    return texto
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/[^a-zA-Z0-9 ]/g, "")
        .toUpperCase();

}


// =========================================================
// CRIAR CAMPO PIX
// =========================================================

function campoPix(id, valor) {

    const tamanho = valor.length
        .toString()
        .padStart(2, "0");

    return id + tamanho + valor;

}


// =========================================================
// CALCULAR CRC16-CCITT
// =========================================================

function calcularCRC16(payload) {

    let crc = 0xFFFF;

    for (let i = 0; i < payload.length; i++) {

        crc ^= payload.charCodeAt(i) << 8;

        for (let j = 0; j < 8; j++) {

            if ((crc & 0x8000) !== 0) {

                crc = (crc << 1) ^ 0x1021;

            } else {

                crc <<= 1;

            }

            crc &= 0xFFFF;

        }

    }

    return crc
        .toString(16)
        .toUpperCase()
        .padStart(4, "0");

}


// =========================================================
// GERAR PIX COPIA E COLA
// =========================================================

function gerarPix(valor) {

    // -----------------------------------------
    // VALIDAÇÃO
    // -----------------------------------------

    valor = Number(valor);

    if (!Number.isFinite(valor) || valor <= 0) {

        alert("Digite um valor válido para a doação.");

        return null;

    }


    // -----------------------------------------
    // DADOS DO RECEBEDOR
    // -----------------------------------------

    const nome = limparTexto(nomeRecebedor)
        .substring(0, 25);

    const cidade = limparTexto(cidadeRecebedor)
        .substring(0, 15);


    // -----------------------------------------
    // 00 - PAYLOAD FORMAT INDICATOR
    // -----------------------------------------

    const payloadFormat = campoPix(
        "00",
        "01"
    );


    // -----------------------------------------
    // 26 - MERCHANT ACCOUNT INFORMATION
    // -----------------------------------------

    const gui = campoPix(
        "00",
        "BR.GOV.BCB.PIX"
    );

    const chave = campoPix(
        "01",
        chavePix
    );

    const merchantAccountInformation =
        campoPix(
            "26",
            gui + chave
        );


    // -----------------------------------------
    // 52 - MERCHANT CATEGORY CODE
    // -----------------------------------------

    const merchantCategoryCode =
        campoPix(
            "52",
            "0000"
        );


    // -----------------------------------------
    // 53 - MOEDA
    // -----------------------------------------

    const moeda =
        campoPix(
            "53",
            "986"
        );


    // -----------------------------------------
    // 54 - VALOR
    // -----------------------------------------

    const valorFormatado =
        valor.toFixed(2);

    const valorPix =
        campoPix(
            "54",
            valorFormatado
        );


    // -----------------------------------------
    // 58 - PAÍS
    // -----------------------------------------

    const pais =
        campoPix(
            "58",
            "BR"
        );


    // -----------------------------------------
    // 59 - NOME DO RECEBEDOR
    // -----------------------------------------

    const nomePix =
        campoPix(
            "59",
            nome
        );


    // -----------------------------------------
    // 60 - CIDADE
    // -----------------------------------------

    const cidadePix =
        campoPix(
            "60",
            cidade
        );


    // -----------------------------------------
    // 62 - DADOS ADICIONAIS
    // -----------------------------------------

    const txid =
        campoPix(
            "05",
            "***"
        );

    const dadosAdicionais =
        campoPix(
            "62",
            txid
        );


    // -----------------------------------------
    // MONTAR PAYLOAD
    // -----------------------------------------

    let payload =
        payloadFormat +
        merchantAccountInformation +
        merchantCategoryCode +
        moeda +
        valorPix +
        pais +
        nomePix +
        cidadePix +
        dadosAdicionais;


    // -----------------------------------------
    // CRC16
    // -----------------------------------------

    payload += "6304";

    const crc =
        calcularCRC16(payload);

    payload += crc;


    return payload;

}


// =========================================================
// MOSTRAR PIX
// =========================================================

function mostrarPix(valor) {

    const pix = gerarPix(valor);

    if (!pix) {
        return;
    }


    // -----------------------------------------
    // VERIFICAR QR CODE
    // -----------------------------------------

    if (typeof QRCode === "undefined") {

        console.error(
            "A biblioteca QRCode não foi carregada."
        );

        alert(
            "Não foi possível carregar o gerador de QR Code."
        );

        return;

    }


    // -----------------------------------------
    // LIMPAR QR CODE ANTERIOR
    // -----------------------------------------

    qrCode.innerHTML = "";


    // -----------------------------------------
    // GERAR NOVO QR CODE
    // -----------------------------------------

    new QRCode(qrCode, {

        text: pix,

        width: 220,

        height: 220,

        correctLevel: QRCode.CorrectLevel.M

    });


    // -----------------------------------------
    // MOSTRAR VALOR
    // -----------------------------------------

    valorExibido.textContent =
        Number(valor).toLocaleString(
            "pt-BR",
            {
                style: "currency",
                currency: "BRL"
            }
        );


    // -----------------------------------------
    // MOSTRAR PIX COPIA E COLA
    // -----------------------------------------

    pixCopiaCola.value = pix;


    // -----------------------------------------
    // MOSTRAR ÁREA DO PIX
    // -----------------------------------------

    resultadoPix.style.display = "block";


    // -----------------------------------------
    // LIMPAR MENSAGEM
    // -----------------------------------------

    mensagemCopia.textContent = "";


    // -----------------------------------------
    // ROLAR ATÉ O QR CODE
    // -----------------------------------------

    resultadoPix.scrollIntoView({
        behavior: "smooth",
        block: "center"
    });

}


// =========================================================
// BOTÕES DE VALOR
// =========================================================

botoesValor.forEach(botao => {

    botao.addEventListener("click", () => {

        const valor =
            Number(botao.dataset.valor);


        // Coloca o valor no campo

        if (inputValor) {
            inputValor.value = valor;
        }


        // Remove seleção anterior

        botoesValor.forEach(botaoAtual => {

            botaoAtual.classList.remove("ativo");

        });


        // Marca botão selecionado

        botao.classList.add("ativo");


        // Gera PIX

        mostrarPix(valor);

    });

});


// =========================================================
// BOTÃO "OUTRO"
// =========================================================

if (botaoOutro) {

    botaoOutro.addEventListener("click", () => {

        if (!campoOutroValor) {
            return;
        }


        // Mostra campo

        campoOutroValor.style.display = "flex";


        // Remove seleção dos valores prontos

        botoesValor.forEach(botao => {

            botao.classList.remove("ativo");

        });


        // Coloca foco no campo

        if (inputValor) {

            inputValor.focus();

        }

    });

}


// =========================================================
// BOTÃO GERAR PIX
// =========================================================

if (botaoGerar) {

    botaoGerar.addEventListener("click", () => {

        if (!inputValor) {
            return;
        }


        const valor =
            Number(inputValor.value);


        mostrarPix(valor);

    });

}


// =========================================================
// ENTER NO CAMPO DE VALOR
// =========================================================

if (inputValor) {

    inputValor.addEventListener("keydown", (evento) => {

        if (evento.key === "Enter") {

            evento.preventDefault();

            const valor =
                Number(inputValor.value);

            mostrarPix(valor);

        }

    });

}


// =========================================================
// COPIAR PIX
// =========================================================

if (botaoCopiar) {

    botaoCopiar.addEventListener("click", async () => {

        const pix =
            pixCopiaCola.value;


        if (!pix) {
            return;
        }


        try {

            await navigator.clipboard.writeText(pix);

            mensagemCopia.textContent =
                "PIX Copia e Cola copiado!";


        } catch (erro) {

            // Fallback para navegadores
            // que bloqueiam clipboard API

            pixCopiaCola.select();

            pixCopiaCola.setSelectionRange(
                0,
                99999
            );

            document.execCommand("copy");

            mensagemCopia.textContent =
                "PIX Copia e Cola copiado!";

        }

    });

}
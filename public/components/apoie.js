// =========================================================
// CONFIGURAÇÕES DO PIX
// =========================================================

const chavePix = "tccportoajuda@gmail.com";
const nomeRecebedor = "PORTO AJUDA";
const cidadeRecebedor = "PRAIA GRANDE";


// =========================================================
// ELEMENTOS
// =========================================================

const botoesValor =
    document.querySelectorAll(".valor-pix");

const botaoOutro =
    document.getElementById("botao-outro");

const modalValor =
    document.getElementById("modal-valor");

const fecharModal =
    document.getElementById("fechar-modal");

const inputValor =
    document.getElementById("valor");

const botaoGerar =
    document.getElementById("gerar-pix");

const resultadoPix =
    document.getElementById("resultado-pix");

const qrcode =
    document.getElementById("qrcode");

const valorExibido =
    document.getElementById("valor-exibido");

const chavePixInput =
    document.getElementById("chave-pix");

const copiarChave =
    document.getElementById("copiar-chave");

const pixCopiaCola =
    document.getElementById("pix-copia-cola");

const botaoCopiar =
    document.getElementById("copiar-pix");

const mensagemCopia =
    document.getElementById("mensagem-copia");


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
// CAMPO TLV
// =========================================================

function campoPix(id, valor) {

    const tamanho =
        valor.length
            .toString()
            .padStart(2, "0");

    return id + tamanho + valor;

}


// =========================================================
// CRC16-CCITT
// =========================================================

function calcularCRC16(payload) {

    let crc = 0xFFFF;

    for (let i = 0; i < payload.length; i++) {

        crc ^= payload.charCodeAt(i) << 8;

        for (let j = 0; j < 8; j++) {

            if ((crc & 0x8000) !== 0) {

                crc =
                    (crc << 1) ^ 0x1021;

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
// GERAR PIX
// =========================================================

function gerarPix(valor) {

    valor = Number(valor);

    if (!Number.isFinite(valor) || valor <= 0) {

        alert("Digite um valor válido.");

        return null;

    }


    const nome =
        limparTexto(nomeRecebedor)
            .substring(0, 25);

    const cidade =
        limparTexto(cidadeRecebedor)
            .substring(0, 15);


    // 00 - Payload Format Indicator

    const payloadFormat =
        campoPix("00", "01");


    // 26 - Merchant Account Information

    const gui =
        campoPix(
            "00",
            "BR.GOV.BCB.PIX"
        );

    const chave =
        campoPix(
            "01",
            chavePix
        );

    const merchantAccountInformation =
        campoPix(
            "26",
            gui + chave
        );


    // 52 - Merchant Category Code

    const merchantCategoryCode =
        campoPix(
            "52",
            "0000"
        );


    // 53 - Moeda

    const moeda =
        campoPix(
            "53",
            "986"
        );


    // 54 - Valor

    const valorFormatado =
        valor.toFixed(2);

    const valorPix =
        campoPix(
            "54",
            valorFormatado
        );


    // 58 - País

    const pais =
        campoPix(
            "58",
            "BR"
        );


    // 59 - Nome

    const nomePix =
        campoPix(
            "59",
            nome
        );


    // 60 - Cidade

    const cidadePix =
        campoPix(
            "60",
            cidade
        );


    // 62 - TXID

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


    // Montar payload

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


    // CRC

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

    const pix =
        gerarPix(valor);

    if (!pix) {
        return;
    }


    // Verifica biblioteca

    if (typeof QRCode === "undefined") {

        console.error(
            "QRCode não foi carregado."
        );

        alert(
            "A biblioteca do QR Code não foi carregada."
        );

        return;

    }


    // Limpar QR anterior

    qrcode.innerHTML = "";


    // Gerar QR

    new QRCode(qrcode, {

        text: pix,

        width: 275,

        height: 275,

        correctLevel:
            QRCode.CorrectLevel.M

    });


    // Mostrar valor

    valorExibido.textContent =
        Number(valor).toLocaleString(
            "pt-BR",
            {
                style: "currency",
                currency: "BRL"
            }
        );


    // Colocar PIX Copia e Cola

    pixCopiaCola.value =
        pix;


    // Mostrar resultado

    resultadoPix.style.display =
        "block";


    // Limpar mensagem

    mensagemCopia.textContent = "";


    // Rolar até o resultado

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


        // Remover seleção anterior

        botoesValor.forEach(botaoAtual => {

            botaoAtual.classList.remove("ativo");

        });


        // Selecionar botão

        botao.classList.add("ativo");


        // Gerar PIX

        mostrarPix(valor);

    });

});


// =========================================================
// ABRIR MODAL "OUTRO"
// =========================================================

botaoOutro.addEventListener("click", () => {

    modalValor.style.display =
        "flex";

    inputValor.value = "";

    setTimeout(() => {

        inputValor.focus();

    }, 100);

});


// =========================================================
// FECHAR MODAL
// =========================================================

fecharModal.addEventListener("click", () => {

    modalValor.style.display =
        "none";

});


// =========================================================
// CLICAR FORA DA CAIXA
// =========================================================

modalValor.addEventListener("click", evento => {

    if (evento.target === modalValor) {

        modalValor.style.display =
            "none";

    }

});


// =========================================================
// CONFIRMAR OUTRO VALOR
// =========================================================

botaoGerar.addEventListener("click", () => {

    const valor =
        Number(inputValor.value);


    if (!Number.isFinite(valor) || valor <= 0) {

        alert(
            "Digite um valor maior que R$ 0,00."
        );

        inputValor.focus();

        return;

    }


    // Fechar modal

    modalValor.style.display =
        "none";


    // Tirar seleção dos botões

    botoesValor.forEach(botao => {

        botao.classList.remove("ativo");

    });


    // Gerar PIX

    mostrarPix(valor);

});


// =========================================================
// ENTER NO MODAL
// =========================================================

inputValor.addEventListener("keydown", evento => {

    if (evento.key === "Enter") {

        evento.preventDefault();

        botaoGerar.click();

    }

});


// =========================================================
// COPIAR CHAVE PIX
// =========================================================

copiarChave.addEventListener("click", async () => {

    try {

        await navigator.clipboard.writeText(
            chavePix
        );

        mensagemCopia.textContent =
            "Chave PIX copiada!";

    } catch (erro) {

        chavePixInput.select();

        document.execCommand("copy");

        mensagemCopia.textContent =
            "Chave PIX copiada!";

    }

});


// =========================================================
// COPIAR PIX COPIA E COLA
// =========================================================

botaoCopiar.addEventListener("click", async () => {

    const pix =
        pixCopiaCola.value;


    if (!pix) {
        return;
    }


    try {

        await navigator.clipboard.writeText(
            pix
        );

        mensagemCopia.textContent =
            "PIX Copia e Cola copiado!";

    } catch (erro) {

        pixCopiaCola.select();

        document.execCommand("copy");

        mensagemCopia.textContent =
            "PIX Copia e Cola copiado!";

    }

});
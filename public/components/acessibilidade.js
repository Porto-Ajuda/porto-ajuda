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
// TAMANHO DA FONTE
// ==========================

let tamanhoFonte = 100;

const diminuirFonte = document.querySelector("#diminuir-fonte");
const aumentarFonte = document.querySelector("#aumentar-fonte");
const resetarFonte = document.querySelector("#resetar-fonte");
const textoTamanho = document.querySelector("#tamanho-fonte");

function atualizarFonte() {

    document.documentElement.style.fontSize = tamanhoFonte + "%";

    textoTamanho.textContent = tamanhoFonte + "%";
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

        alternarTema.textContent = "🌙 Modo Escuro";

    } else {

        alternarTema.textContent = "☀️ Modo Claro";

    }

});


// ==========================
// ALTO CONTRASTE
// ==========================

const altoContraste =
    document.querySelector("#alto-contraste");

altoContraste.addEventListener("click", () => {

    document.body.classList.toggle("alto-contraste");

});

const botaoLibras =
    document.querySelector("#ativar-libras");

botaoLibras.addEventListener("click", async () => {

    try {

        const botaoVLibras =
            await window.carregarVLibras();

        // Abre o VLibras
        botaoVLibras.click();

        // Fecha o painel de acessibilidade
        painel.classList.remove("aberto");

    } catch (erro) {

        console.error(
            "Erro ao ativar o VLibras:",
            erro
        );

    }

});
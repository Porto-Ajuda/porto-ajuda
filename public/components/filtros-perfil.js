/* =========================================================
   FILTROS DO REGISTRO — independente do login e do perfil.js
   (tipo: tudo/doações/voluntariado + ano / mês / dia)
   ========================================================= */

const NOMES_MESES = [
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
];

const MESES_ABREVIADOS = {
    JAN: "01", FEV: "02", MAR: "03", ABR: "04", MAI: "05", JUN: "06",
    JUL: "07", AGO: "08", SET: "09", OUT: "10", NOV: "11", DEZ: "12"
};

function iniciarFiltrosRegistro() {
    const filtros = Array.from(document.querySelectorAll(".filtro"));
    const registros = Array.from(document.querySelectorAll(".registro"));
    const registroVazio = document.getElementById("registro-vazio");
    const rotuloAno = document.querySelector(".ano-registro");

    const botaoFiltroData = document.getElementById("botao-filtro-data");
    const painelFiltroData = document.getElementById("painel-filtro-data");
    const selecaoAno = document.getElementById("filtro-ano");
    const selecaoMes = document.getElementById("filtro-mes");
    const selecaoDia = document.getElementById("filtro-dia");
    const botaoLimparData = document.getElementById("limpar-filtro-data");
    const selo = document.getElementById("filtro-badge");
    const contador = document.getElementById("contador-registros");

    if (!registros.length) {
        console.warn("[filtros] nenhum .registro encontrado no HTML.");
        return;
    }

    console.log("[filtros] iniciado", registros.length, "registros");

    // Ano usado quando o <li> só tem "15 SET" (sem data completa)
    const anoPadrao = rotuloAno?.textContent.trim() ?? "";

    function lerDataDoRegistro(registro) {
        // Preferência: <li data-data="2026-09-15">
        if (registro.dataset.data) {
            const [ano, mes, dia] = registro.dataset.data.split("-");
            return { ano, mes, dia };
        }

        // Alternativa: lê o texto "15 SET"
        const texto = registro.querySelector(".data-registro")?.textContent ?? "";
        const [dia = "", mes = ""] = texto.trim().split(/\s+/);

        return {
            ano: anoPadrao,
            mes: MESES_ABREVIADOS[mes.toUpperCase()] ?? "",
            dia: dia.padStart(2, "0")
        };
    }

    const itens = registros.map(elemento => ({
        elemento,
        tipo: elemento.dataset.tipo,
        ...lerDataDoRegistro(elemento)
    }));

    /* ---------- preenche os selects ---------- */

    Array.from(new Set(itens.map(item => item.ano).filter(Boolean)))
        .sort((a, b) => b - a)
        .forEach(ano => selecaoAno?.append(new Option(ano, ano)));

    NOMES_MESES.forEach((nome, indice) => {
        selecaoMes?.append(new Option(nome, String(indice + 1).padStart(2, "0")));
    });

    for (let dia = 1; dia <= 31; dia++) {
        selecaoDia?.append(new Option(dia, String(dia).padStart(2, "0")));
    }

    /* ---------- estado vazio ---------- */

    const botaoVazio = registroVazio?.querySelector("button");

    if (registroVazio) {
        registroVazio.querySelector("h3").textContent = "Nenhum registro encontrado";
        registroVazio.querySelector("p").textContent =
            "Não há registros para os filtros escolhidos.";

        if (botaoVazio) botaoVazio.textContent = "Limpar filtros";
    }

    /* ---------- aplicar filtros ---------- */

    let tipoAtivo = "tudo";

    function aplicar() {
        const ano = selecaoAno?.value ?? "";
        const mes = selecaoMes?.value ?? "";
        const dia = selecaoDia?.value ?? "";

        let visiveis = 0;
        const anosVisiveis = new Set();

        itens.forEach(item => {
            const mostrar =
                (tipoAtivo === "tudo" || item.tipo === tipoAtivo) &&
                (!ano || item.ano === ano) &&
                (!mes || item.mes === mes) &&
                (!dia || item.dia === dia);

            item.elemento.hidden = !mostrar;

            if (mostrar) {
                visiveis++;
                anosVisiveis.add(item.ano);
            }
        });

        const ativos = [ano, mes, dia].filter(Boolean).length;

        if (selo) {
            selo.textContent = ativos;
            selo.hidden = ativos === 0;
        }

        if (contador) {
            contador.textContent =
                `Mostrando ${visiveis} de ${itens.length} registros`;
        }

        if (rotuloAno) {
            rotuloAno.hidden = visiveis === 0;
            rotuloAno.textContent = Array.from(anosVisiveis)
                .sort((a, b) => b - a)
                .join(" • ");
        }

        if (registroVazio) {
            registroVazio.hidden = visiveis !== 0;
        }
    }

    function limparDatas() {
        if (selecaoAno) selecaoAno.value = "";
        if (selecaoMes) selecaoMes.value = "";
        if (selecaoDia) selecaoDia.value = "";
    }

    function limparTudo() {
        limparDatas();

        tipoAtivo = "tudo";

        filtros.forEach(botao => {
            botao.setAttribute("aria-pressed", String(botao.dataset.filtro === "tudo"));
        });

        aplicar();
    }

    /* ---------- eventos ---------- */

    filtros.forEach(botao => {
        botao.addEventListener("click", () => {
            tipoAtivo = botao.dataset.filtro;

            filtros.forEach(item => {
                item.setAttribute("aria-pressed", String(item === botao));
            });

            aplicar();
        });
    });

    [selecaoAno, selecaoMes, selecaoDia].forEach(selecao => {
        selecao?.addEventListener("change", aplicar);
    });

    botaoLimparData?.addEventListener("click", () => {
        limparDatas();
        aplicar();
    });

    botaoVazio?.addEventListener("click", limparTudo);

    botaoFiltroData?.addEventListener("click", () => {
        const aberto = botaoFiltroData.getAttribute("aria-expanded") === "true";

        botaoFiltroData.setAttribute("aria-expanded", String(!aberto));
        painelFiltroData?.classList.toggle("aberto", !aberto);

        if (painelFiltroData) {
            painelFiltroData.inert = aberto;
        }
    });

    aplicar();
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", iniciarFiltrosRegistro);
} else {
    iniciarFiltrosRegistro();
}
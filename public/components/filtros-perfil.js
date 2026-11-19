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
    const grupoFiltrosTipo = filtros[0]?.parentElement;
    const lista = document.getElementById("linha-tempo-registros");
    const registroVazio = document.getElementById("registro-vazio");
    const rotuloAno = document.querySelector(".ano-registro");
    const avisoDoacoes = document.getElementById("registro-doacoes-indisponivel");
    const botaoFiltroData = document.getElementById("botao-filtro-data");
    const painelFiltroData = document.getElementById("painel-filtro-data");
    const selecaoAno = document.getElementById("filtro-ano");
    const selecaoMes = document.getElementById("filtro-mes");
    const selecaoDia = document.getElementById("filtro-dia");
    const botaoLimparData = document.getElementById("limpar-filtro-data");
    const selo = document.getElementById("filtro-badge");
    const contador = document.getElementById("contador-registros");
    const botaoLimparTudo = document.getElementById("registro-limpar-filtros");

    if (!lista || !registroVazio) return;

    let tipoAtivo = "tudo";

    function lerDataDoRegistro(registro) {
        if (registro.dataset.data && /^\d{4}-\d{2}-\d{2}$/.test(registro.dataset.data)) {
            const [ano, mes, dia] = registro.dataset.data.split("-");
            return { ano, mes, dia };
        }
        const texto = registro.querySelector(".data-registro")?.textContent ?? "";
        const [dia = "", mes = ""] = texto.trim().split(/\s+/);
        return { ano: "", mes: MESES_ABREVIADOS[mes.toUpperCase()] ?? "", dia: dia.padStart(2, "0") };
    }

    function atualizarOpcoesDeAno(itens) {
        if (!selecaoAno) return;
        const selecionado = selecaoAno.value;
        while (selecaoAno.options.length > 1) selecaoAno.remove(1);
        const anos = Array.from(new Set(itens.map(item => item.ano).filter(Boolean))).sort((a, b) => b - a);
        anos.forEach(ano => selecaoAno.append(new Option(ano, ano)));
        selecaoAno.value = anos.includes(selecionado) ? selecionado : "";
    }

    if (selecaoMes && selecaoMes.options.length <= 1) {
        NOMES_MESES.forEach((nome, indice) => selecaoMes.append(new Option(nome, String(indice + 1).padStart(2, "0"))));
    }
    if (selecaoDia && selecaoDia.options.length <= 1) {
        for (let dia = 1; dia <= 31; dia++) selecaoDia.append(new Option(dia, String(dia).padStart(2, "0")));
    }

    function aplicar() {
        let tiposDisponiveis = [];
        try {
            const raw = JSON.parse(lista.dataset.availableTypes || "[]");
            if (Array.isArray(raw)) tiposDisponiveis = raw.filter(tipo => ["doacao", "voluntariado"].includes(tipo));
        } catch { /* sem metadados de tipos: nenhum filtro de tipo é exibido */ }
        if (tipoAtivo !== "tudo" && !tiposDisponiveis.includes(tipoAtivo)) tipoAtivo = "tudo";
        filtros.forEach(botao => {
            const tipo = botao.dataset.filtro;
            botao.hidden = tipo === "tudo"
                ? tiposDisponiveis.length <= 1
                : !tiposDisponiveis.includes(tipo);
            botao.setAttribute("aria-pressed", String(tipo === tipoAtivo));
        });
        if (grupoFiltrosTipo) grupoFiltrosTipo.hidden = tiposDisponiveis.length <= 1;

        const itens = Array.from(lista.querySelectorAll(".registro")).map(elemento => ({
            elemento,
            tipo: elemento.dataset.tipo,
            ...lerDataDoRegistro(elemento)
        }));
        atualizarOpcoesDeAno(itens);

        const ano = selecaoAno?.value ?? "";
        const mes = selecaoMes?.value ?? "";
        const dia = selecaoDia?.value ?? "";
        let visiveis = 0;
        const anosVisiveis = new Set();

        itens.forEach(item => {
            const mostrar = (tipoAtivo === "tudo" || item.tipo === tipoAtivo)
                && (!ano || item.ano === ano)
                && (!mes || item.mes === mes)
                && (!dia || item.dia === dia);
            item.elemento.hidden = !mostrar;
            if (mostrar) {
                visiveis++;
                if (item.ano) anosVisiveis.add(item.ano);
            }
        });

        const carregado = lista.dataset.ready === "true";
        const erro = lista.dataset.error === "true";
        const carregando = lista.dataset.loading === "true";
        const temMais = lista.dataset.hasMore === "true";
        const total = Number(lista.dataset.totalRecords);
        const totalKnown = Number.isSafeInteger(total) && total >= 0;
        const indisponivelDoacao = tipoAtivo === "tudo" || tipoAtivo === "doacao";
        if (avisoDoacoes) avisoDoacoes.hidden = !indisponivelDoacao;

        if (contador) {
            if (!carregado || carregando) contador.textContent = carregando ? "Atualizando histórico…" : "";
            else if (totalKnown && total > itens.length) contador.textContent = `Mostrando ${visiveis} de ${itens.length} candidaturas carregadas (${total} no total).`;
            else contador.textContent = `Mostrando ${visiveis} de ${itens.length} candidaturas.`;
        }

        if (selo) {
            const ativos = [ano, mes, dia].filter(Boolean).length;
            selo.textContent = ativos;
            selo.hidden = ativos === 0;
        }
        if (rotuloAno) {
            rotuloAno.hidden = visiveis === 0;
            rotuloAno.textContent = Array.from(anosVisiveis).sort((a, b) => b - a).join(" • ");
        }

        const mostrarVazio = carregado && !erro && !carregando && visiveis === 0 && tipoAtivo !== "doacao";
        registroVazio.hidden = !mostrarVazio;
        const tituloVazio = registroVazio.querySelector("h3");
        const textoVazio = registroVazio.querySelector("p");
        if (tituloVazio && textoVazio && mostrarVazio) {
            tituloVazio.textContent = tipoAtivo === "voluntariado" && !itens.length
                ? "Você ainda não enviou candidaturas"
                : "Nenhuma candidatura encontrada";
            textoVazio.textContent = temMais
                ? "Não há resultados entre as candidaturas carregadas. Carregue mais registros para pesquisar o restante do histórico."
                : "Quando você se candidatar a uma oportunidade, ela aparecerá aqui.";
        }
        if (botaoLimparTudo) {
            botaoLimparTudo.hidden = tipoAtivo === "tudo" && !ano && !mes && !dia;
        }
    }

    filtros.forEach(botao => {
        botao.addEventListener("click", () => {
            tipoAtivo = botao.dataset.filtro;
            filtros.forEach(item => item.setAttribute("aria-pressed", String(item === botao)));
            aplicar();
        });
    });
    [selecaoAno, selecaoMes, selecaoDia].forEach(selecao => selecao?.addEventListener("change", aplicar));
    botaoLimparData?.addEventListener("click", () => {
        if (selecaoAno) selecaoAno.value = "";
        if (selecaoMes) selecaoMes.value = "";
        if (selecaoDia) selecaoDia.value = "";
        aplicar();
    });
    botaoLimparTudo?.addEventListener("click", () => {
        tipoAtivo = "tudo";
        filtros.forEach(item => item.setAttribute("aria-pressed", String(item.dataset.filtro === "tudo")));
        if (selecaoAno) selecaoAno.value = "";
        if (selecaoMes) selecaoMes.value = "";
        if (selecaoDia) selecaoDia.value = "";
        aplicar();
    });
    botaoFiltroData?.addEventListener("click", () => {
        const aberto = botaoFiltroData.getAttribute("aria-expanded") === "true";
        botaoFiltroData.setAttribute("aria-expanded", String(!aberto));
        painelFiltroData?.classList.toggle("aberto", !aberto);
        if (painelFiltroData) painelFiltroData.inert = aberto;
    });
    document.addEventListener("porto:historico-perfil-atualizado", aplicar);
    aplicar();
}

if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", iniciarFiltrosRegistro, { once: true });
else iniciarFiltrosRegistro();

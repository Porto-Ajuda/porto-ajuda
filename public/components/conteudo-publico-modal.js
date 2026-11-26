let origemModalConteudoPublico = null;
let overflowAnteriorModalCard = "";

function imagemPublicaValida(url) {
    if (typeof url !== "string" || !url) return false;
    if (url.startsWith("/") && !url.startsWith("//")) return true;
    try {
        const parsed = new URL(url, window.location.origin);
        return parsed.protocol === "http:" || parsed.protocol === "https:";
    } catch {
        return false;
    }
}

function adicionarDetalheVagaModal(lista, rotulo, valor) {
    if (valor == null || String(valor).trim() === "") return;
    const termo = document.createElement("dt");
    termo.textContent = rotulo;
    const detalhe = document.createElement("dd");
    detalhe.textContent = String(valor);
    lista.append(termo, detalhe);
}

function formatarDataVagaModal(valor) {
    if (typeof valor !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(valor)) return valor;
    const [ano, mes, dia] = valor.split("-").map(Number);
    return new Date(ano, mes - 1, dia).toLocaleDateString("pt-BR", {
        day: "2-digit", month: "short", year: "numeric"
    });
}

window.abrirModalConteudoPublico = (item, tipo, origem) => {
    if (!item || typeof item.titulo !== "string") return;
    const overlay = document.getElementById("modal-card-overlay");
    const imagem = document.getElementById("modal-card-imagem");
    const placeholder = document.getElementById("modal-card-imagem-placeholder");
    const data = item.dataAtualizacao || item.dataCriacao;
    const detalheData = data ? new Date(data) : null;
    origemModalConteudoPublico = origem || document.activeElement;
    overflowAnteriorModalCard = document.body.style.overflow;

    document.getElementById("modal-card-tag").textContent = tipo === "voluntariado"
        ? "Oportunidade de voluntariado" : "Publicação";
    document.getElementById("modal-card-titulo").textContent = item.titulo;
    document.getElementById("modal-card-osc").textContent = item.nomeOsc || "Organização parceira";
    document.getElementById("modal-card-conteudo").textContent = item.conteudo || "";
    document.getElementById("modal-card-usuario").hidden = true;
    document.getElementById("modal-card-data").textContent = detalheData && !Number.isNaN(detalheData.getTime())
        ? detalheData.toLocaleDateString("pt-BR", { day: "2-digit", month: "short", year: "numeric" })
        : "";

    placeholder.replaceChildren();
    if (item.imagemUrl && imagemPublicaValida(item.imagemUrl)) {
        imagem.src = item.imagemUrl;
        imagem.alt = `Imagem de ${item.titulo}`;
        imagem.style.display = "block";
        placeholder.style.display = "none";
        imagem.onerror = () => {
            imagem.style.display = "none";
            placeholder.style.display = "flex";
            placeholder.replaceChildren(criarPreviewModal(item, tipo));
        };
    } else {
        imagem.removeAttribute("src");
        imagem.alt = "";
        imagem.style.display = "none";
        placeholder.style.display = "flex";
        placeholder.appendChild(criarPreviewModal(item, tipo));
    }

    const detalhes = document.getElementById("modal-card-vaga-detalhes");
    detalhes.replaceChildren();
    detalhes.hidden = true;
    if (tipo === "voluntariado" && item.vaga) {
        const vaga = item.vaga;
        const modalidades = { PRESENCIAL: "Presencial", REMOTO: "Remoto", HIBRIDO: "Híbrido" };
        const statusVaga = { ABERTA: "Aberta", ENCERRADA: "Encerrada", CANCELADA: "Cancelada" };
        adicionarDetalheVagaModal(detalhes, "Modalidade", modalidades[vaga.modalidadeVoluntariado] || vaga.modalidadeVoluntariado);
        const local = [vaga.rua, vaga.numero, vaga.bairro, vaga.cidade].filter(Boolean).join(", ");
        adicionarDetalheVagaModal(detalhes, "Local", local);
        adicionarDetalheVagaModal(detalhes, "Requisitos", vaga.requisitos);
        adicionarDetalheVagaModal(detalhes, "Vagas disponíveis", vaga.quantidadeVagas);
        adicionarDetalheVagaModal(detalhes, "Carga horária", vaga.cargaHoraria);
        adicionarDetalheVagaModal(detalhes, "Início", formatarDataVagaModal(vaga.dataInicio));
        adicionarDetalheVagaModal(detalhes, "Encerramento", formatarDataVagaModal(vaga.dataFim));
        adicionarDetalheVagaModal(detalhes, "Status", statusVaga[vaga.status] || vaga.status);
        detalhes.hidden = detalhes.childElementCount === 0;
    }

    const botaoCandidatar = document.getElementById("btn-modal-candidatar");
    const estadoCandidatura = document.getElementById("estado-candidatura-modal");
    const hojeLocal = new Date();
    hojeLocal.setHours(0, 0, 0, 0);
    const encerramentoLocal = item.vaga?.dataFim
        ? new Date(`${item.vaga.dataFim}T00:00:00`)
        : null;
    const vagaAplicavel = item.vaga
        && item.ativo === true
        && item.tipo === "VOLUNTARIADO"
        && item.vaga.status === "ABERTA"
        && Number(item.vaga.quantidadeVagas) > 0
        && encerramentoLocal && !Number.isNaN(encerramentoLocal.getTime())
        && encerramentoLocal >= hojeLocal;
    if (botaoCandidatar) {
        botaoCandidatar.hidden = true;
        botaoCandidatar.disabled = false;
        botaoCandidatar.textContent = "Quero participar";
    }
    if (estadoCandidatura) {
        estadoCandidatura.hidden = true;
        estadoCandidatura.textContent = "";
    }
    window.conteudoVoluntariadoAtual = vagaAplicavel && tipo === "voluntariado" ? item : null;

    let botaoDenunciar = document.getElementById("btn-modal-denunciar");
    if (!botaoDenunciar) {
        botaoDenunciar = document.createElement("button");
        botaoDenunciar.id = "btn-modal-denunciar";
        botaoDenunciar.type = "button";
        botaoDenunciar.className = "botao-padrao";
        botaoDenunciar.dataset.contentReport = "";
        botaoDenunciar.textContent = "Denunciar";
        document.querySelector("#modal-card-overlay .modal-card-info")?.append(botaoDenunciar);
    }
    botaoDenunciar.dataset.reportType = tipo === "voluntariado" ? "VOLUNTARIADO" : "PUBLICACAO";
    botaoDenunciar.dataset.reportId = String(item.id);
    botaoDenunciar.hidden = !Number.isInteger(Number(item.id)) || Number(item.id) < 1;

    overlay.dataset.conteudoPublico = "true";
    overlay.setAttribute("aria-hidden", "false");
    document.getElementById("fechar-modal-card").setAttribute(
        "aria-label", tipo === "voluntariado" ? "Fechar oportunidade" : "Fechar publicação");
    overlay.classList.add("aberto");
    document.body.style.overflow = "hidden";
    document.getElementById("fechar-modal-card").focus();
    if (vagaAplicavel && tipo === "voluntariado") {
        window.dispatchEvent(new CustomEvent("porto:volunteer-viewer-open", { detail: { item } }));
    }
};

function criarPreviewModal(item, tipo) {
    const preview = document.createElement("div");
    preview.className = "modal-preview-textual";
    const categoria = document.createElement("span");
    categoria.textContent = tipo === "voluntariado" ? "VOLUNTARIADO" : "PUBLICAÇÃO";
    const titulo = document.createElement("strong");
    titulo.textContent = item.titulo || "Conteúdo público";
    preview.append(categoria, titulo);
    return preview;
}

function fecharModalCard(event) {
    if (event.target === document.getElementById("modal-card-overlay")) {
        fecharModalCardDireto();
    }
}

function fecharModalCardDireto() {
    const overlay = document.getElementById("modal-card-overlay");
    overlay.classList.remove("aberto");
    document.body.style.overflow = overlay.dataset.conteudoPublico === "true"
        ? overflowAnteriorModalCard : "";
    overlay.dataset.conteudoPublico = "false";
    overlay.setAttribute("aria-hidden", "true");
    window.conteudoVoluntariadoAtual = null;
    document.getElementById("fechar-modal-card").setAttribute("aria-label", "Fechar conteúdo");
    if (origemModalConteudoPublico?.isConnected) origemModalConteudoPublico.focus();
    origemModalConteudoPublico = null;
}

document.addEventListener("keydown", evento => {
    const overlay = document.getElementById("modal-card-overlay");
    if (!overlay?.classList.contains("aberto")) return;
    if (evento.key === "Escape") {
        evento.preventDefault();
        fecharModalCardDireto();
        return;
    }
    if (evento.key === "Tab") {
        evento.preventDefault();
        document.getElementById("fechar-modal-card").focus();
    }
});


window.fecharModalCard = fecharModalCard;
window.fecharModalCardDireto = fecharModalCardDireto;

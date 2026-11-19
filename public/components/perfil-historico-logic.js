const STATUS_CANDIDATURA = Object.freeze({
    PENDENTE: "Pendente",
    APROVADA: "Aprovada",
    CONCLUIDA: "Concluída",
    RECUSADA: "Recusada",
    REJEITADA: "Recusada",
    DESISTIU: "Cancelada"
});

const MODALIDADES = Object.freeze({
    PRESENCIAL: "Presencial",
    REMOTO: "Remoto",
    HIBRIDO: "Híbrido"
});

function texto(valor) {
    return typeof valor === "string" ? valor.trim() : "";
}

function dataIsoValida(valor) {
    if (typeof valor !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(valor)) return false;
    const [ano, mes, dia] = valor.split("-").map(Number);
    const data = new Date(ano, mes - 1, dia);
    return data.getFullYear() === ano && data.getMonth() === mes - 1 && data.getDate() === dia;
}

export function formatarDataHistorico(valor, estilo = "curto") {
    if (!dataIsoValida(valor)) return "Data indisponível";
    const [ano, mes, dia] = valor.split("-").map(Number);
    const data = new Date(ano, mes - 1, dia);
    return data.toLocaleDateString("pt-BR", estilo === "curto"
        ? { day: "2-digit", month: "short", year: "numeric" }
        : { day: "2-digit", month: "short" });
}

export function mapearCandidaturaHistorico(item) {
    const candidaturaId = Number(item?.id);
    if (!Number.isSafeInteger(candidaturaId) || candidaturaId < 1) return null;

    const modalidade = texto(item.modalidade);
    const modalidadeLabel = MODALIDADES[modalidade] || "Modalidade não informada";
    const cidade = texto(item.cidade);
    const bairro = texto(item.bairro);
    const local = [bairro, cidade].filter(Boolean).join(" · ");
    const postId = Number(item.postId);
    const oscId = Number(item.oscId);
    const linkDisponivel = item.oportunidadePublica === true
        && Number.isSafeInteger(postId) && postId > 0
        && Number.isSafeInteger(oscId) && oscId > 0;
    const parametros = new URLSearchParams({ candidaturaPost: String(postId), candidaturaOsc: String(oscId) });

    return Object.freeze({
        id: candidaturaId,
        tipo: "voluntariado",
        data: dataIsoValida(item.dataCandidatura) ? item.dataCandidatura : "",
        dataLabel: formatarDataHistorico(item.dataCandidatura),
        titulo: texto(item.tituloOportunidade) || "Oportunidade de voluntariado",
        nomeOsc: texto(item.nomeOsc) || "ONG parceira",
        status: item.status,
        statusLabel: STATUS_CANDIDATURA[item.status] || "Status indisponível",
        modalidadeLabel,
        local,
        inicio: dataIsoValida(item.dataInicio) ? formatarDataHistorico(item.dataInicio) : "",
        encerramento: dataIsoValida(item.dataFim) ? formatarDataHistorico(item.dataFim) : "",
        dataConclusao: dataIsoValida(item.dataConclusao) ? formatarDataHistorico(item.dataConclusao) : "",
        horasRealizadas: item.horasRealizadas != null && Number.isFinite(Number(item.horasRealizadas)) && Number(item.horasRealizadas) >= 0
            ? Number(item.horasRealizadas) : null,
        certificadoDisponivel: item.certificadoDisponivel === true,
        candidaturaId,
        link: linkDisponivel ? `/?${parametros.toString()}` : null
    });
}

export function criarRegistroHistorico(documento, item) {
    const registro = documento.createElement("li");
    registro.className = "registro";
    registro.dataset.tipo = item.tipo;
    if (item.data) registro.dataset.data = item.data;

    const data = documento.createElement("time");
    data.className = "data-registro";
    if (item.data) data.dateTime = item.data;
    data.textContent = item.data ? `Candidatura enviada em ${item.dataLabel}` : "Data da candidatura indisponível";

    const titulo = documento.createElement("h3");
    titulo.className = "registro-atividade-titulo";
    titulo.textContent = item.titulo;
    const status = documento.createElement("span");
    status.className = "etiqueta";
    status.textContent = item.statusLabel;
    const cabecalho = documento.createElement("div");
    cabecalho.className = "registro-atividade-cabecalho";
    cabecalho.append(titulo, status);

    const ong = documento.createElement("p");
    ong.className = "registro-voluntariado-ong";
    ong.textContent = `ONG: ${item.nomeOsc}`;

    const detalhes = documento.createElement("p");
    detalhes.className = "registro-voluntariado-detalhes";
    detalhes.textContent = [item.modalidadeLabel, item.local].filter(Boolean).join(" · ");

    const periodo = documento.createElement("p");
    periodo.className = "registro-voluntariado-periodo";
    const datas = [];
    if (item.status === "CONCLUIDA") {
        if (item.dataConclusao) datas.push(`Concluída em: ${item.dataConclusao}`);
        if (item.horasRealizadas != null) datas.push(`${item.horasRealizadas.toLocaleString("pt-BR")} hora(s) realizadas`);
    } else {
        if (item.inicio) datas.push(`Início previsto: ${item.inicio}`);
        if (item.encerramento) datas.push(`Encerramento previsto: ${item.encerramento}`);
    }
    periodo.textContent = datas.join(" · ");

    registro.append(cabecalho, ong, data);
    if (detalhes.textContent) registro.append(detalhes);
    if (periodo.textContent) registro.append(periodo);

    if (item.link) {
        const link = documento.createElement("a");
        link.className = "registro-voluntariado-link";
        link.href = item.link;
        link.textContent = "Ver oportunidade";
        registro.append(link);
    }
    if (item.status === "CONCLUIDA" && item.certificadoDisponivel) {
        const certificado = documento.createElement("a");
        certificado.className = "registro-voluntariado-link";
        certificado.href = `/usuario/candidaturas/${encodeURIComponent(item.candidaturaId)}/certificado`;
        certificado.textContent = "Baixar certificado";
        registro.append(certificado);
    }
    return registro;
}

export function normalizarPaginaCandidaturas(payload) {
    if (!payload || !Array.isArray(payload.content)) {
        throw new Error("A resposta do histórico está em um formato inválido.");
    }
    const totalElements = payload.totalElements;
    const totalPages = payload.totalPages;
    const number = payload.number;
    if (!Number.isSafeInteger(totalElements) || totalElements < 0
        || !Number.isSafeInteger(totalPages) || totalPages < 0
        || !Number.isSafeInteger(number) || number < 0) {
        throw new Error("A resposta do histórico está em um formato inválido.");
    }
    return Object.freeze({
        content: payload.content.map(mapearCandidaturaHistorico).filter(Boolean),
        totalElements,
        totalPages,
        number,
        availableTypes: totalElements > 0 ? ["voluntariado"] : []
    });
}

export function calcularVisibilidadeHistorico(totalElements, types = []) {
    const allowedTypes = new Set(["doacao", "voluntariado"]);
    const availableTypes = [...new Set(Array.isArray(types) ? types.filter(type => allowedTypes.has(type)) : [])];
    return Object.freeze({
        hasRecords: Number.isSafeInteger(totalElements) && totalElements > 0,
        availableTypes,
        showTypeFilters: availableTypes.length > 1
    });
}

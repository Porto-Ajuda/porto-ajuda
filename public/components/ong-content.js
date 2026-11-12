const POST_TYPES = new Set(["DOACAO", "VOLUNTARIADO", "NOTICIA"]);
const MODALITIES = new Set(["PRESENCIAL", "REMOTO", "HIBRIDO"]);
const OPPORTUNITY_STATUSES = new Set(["ABERTA", "ENCERRADA", "CANCELADA"]);

export function urlImagemSegura(valor) {
    if (!valor) return true;
    if (typeof valor !== "string" || valor.length > 1000) return false;
    if (valor.startsWith("/") && !valor.startsWith("//")) return true;
    try {
        const url = new URL(valor);
        return url.protocol === "https:" || url.protocol === "http:";
    } catch {
        return false;
    }
}

export function validarArquivoImagem(arquivo) {
    if (!arquivo || typeof arquivo.size !== "number" || arquivo.size <= 0) {
        return { valido: false, mensagem: "Selecione uma imagem não vazia." };
    }
    if (arquivo.size > 10 * 1024 * 1024) {
        return { valido: false, mensagem: "A imagem não pode ultrapassar 10 MB." };
    }
    if (!["image/jpeg", "image/png"].includes(arquivo.type)) {
        return { valido: false, mensagem: "Selecione um arquivo JPEG ou PNG." };
    }
    return { valido: true, mensagem: "" };
}

export function validarConteudoOsc(payload) {
    const post = payload?.post;
    if (!post || !POST_TYPES.has(post.tipo) || post.tipo === "NOTICIA") {
        return { valido: false, campo: "tipo" };
    }
    if (typeof post.titulo !== "string" || !post.titulo.trim() || post.titulo.trim().length > 255) {
        return { valido: false, campo: "titulo" };
    }
    if (typeof post.conteudo !== "string" || !post.conteudo.trim() || post.conteudo.trim().length > 10000) {
        return { valido: false, campo: "conteudo" };
    }
    if (typeof post.ativo !== "boolean" || !urlImagemSegura(post.imagemUrl)) {
        return { valido: false, campo: "post" };
    }

    if (post.tipo === "DOACAO") {
        return payload.vaga == null ? { valido: true } : { valido: false, campo: "vaga" };
    }

    const vaga = payload.vaga;
    if (!vaga || !Number.isInteger(vaga.quantidadeVagas) || vaga.quantidadeVagas < 1
            || !MODALITIES.has(vaga.modalidadeVoluntariado)
            || typeof vaga.requisitos !== "string" || !vaga.requisitos.trim()
            || typeof vaga.cargaHoraria !== "string" || !vaga.cargaHoraria.trim()
            || typeof vaga.cidade !== "string" || !vaga.cidade.trim()
            || typeof vaga.bairro !== "string" || !vaga.bairro.trim()
            || !dataValida(vaga.dataInicio) || !dataValida(vaga.dataFim)
            || vaga.dataFim < vaga.dataInicio
            || (vaga.status != null && !OPPORTUNITY_STATUSES.has(vaga.status))) {
        return { valido: false, campo: "vaga" };
    }
    for (const name of ["rua", "numero", "complemento"]) {
        if (vaga[name] != null && (typeof vaga[name] !== "string" || vaga[name].trim().length > 100)) {
            return { valido: false, campo: name };
        }
    }
    return { valido: true };
}

export function separarConteudoOsc(conteudos) {
    const seguro = Array.isArray(conteudos) ? conteudos : [];
    return {
        posts: seguro.filter(item => item?.tipo !== "VOLUNTARIADO"),
        oportunidades: seguro.filter(item => item?.tipo === "VOLUNTARIADO")
    };
}

export function separarConteudoPublicoOsc(conteudos, oscId, statusOsc, arquivada = false) {
    if (statusOsc !== "ATIVA" || arquivada) {
        return { posts: [], oportunidades: [] };
    }
    const elegiveis = (Array.isArray(conteudos) ? conteudos : [])
        .filter(item => respostaConteudoOscValida(item, oscId) && item.ativo);
    return {
        posts: elegiveis.filter(item => item.tipo !== "VOLUNTARIADO"),
        oportunidades: elegiveis.filter(item => item.tipo === "VOLUNTARIADO"
            && item.vaga != null
            && item.vaga.status === "ABERTA"
            && item.vaga.quantidadeVagas > 0
            && dataValida(item.vaga.dataFim))
    };
}

export function respostaConteudoOscValida(item, oscId) {
    if (!item || !Number.isInteger(item.id) || !Number.isInteger(item.oscId)
            || item.oscId !== Number(oscId) || !POST_TYPES.has(item.tipo)
            || typeof item.ativo !== "boolean" || typeof item.titulo !== "string"
            || typeof item.conteudo !== "string" || typeof item.podeEditar !== "boolean"
            || !urlImagemSegura(item.imagemUrl)) {
        return false;
    }
    if (item.tipo !== "VOLUNTARIADO") return item.vaga == null;
    if (item.vaga == null) return true;
    return Number.isInteger(item.vaga.id)
        && Number.isInteger(item.vaga.quantidadeVagas)
        && MODALITIES.has(item.vaga.modalidadeVoluntariado)
        && OPPORTUNITY_STATUSES.has(item.vaga.status)
        && dataValida(item.vaga.dataInicio)
        && dataValida(item.vaga.dataFim);
}

export function dataValida(valor) {
    if (typeof valor !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(valor)) return false;
    const [year, month, day] = valor.split("-").map(Number);
    const date = new Date(Date.UTC(year, month - 1, day));
    return date.getUTCFullYear() === year
        && date.getUTCMonth() === month - 1
        && date.getUTCDate() === day;
}

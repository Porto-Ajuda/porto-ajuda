export function converterValorBooleano(valor) {
    return valor === "" ? null : valor === "true";
}

export function configurarCamposVoluntariado(grupo, aceitaVoluntarios) {
    const habilitado = aceitaVoluntarios === true;
    grupo.hidden = !habilitado;
    grupo.querySelectorAll("input,select,textarea").forEach(campo => {
        campo.disabled = !habilitado;
    });
}

export function montarPayloadAtualizacaoOsc({
    cnpj,
    obterValor,
    opcional,
    numeroOpcional,
    diasHorariosFuncionamento,
    causasSecundarias,
    ods
}) {
    const booleano = id => converterValorBooleano(obterValor(id));
    return {
        cnpj,
        descricaoCurta: obterValor("edicao-descricao-curta").trim(),
        descricao: obterValor("edicao-descricao"),
        email: obterValor("edicao-email"),
        telefone: opcional("edicao-telefone"),
        whatsapp: opcional("edicao-whatsapp"),
        site: opcional("edicao-site"),
        instagram: opcional("edicao-instagram"),
        linkedin: opcional("edicao-linkedin"),
        youtube: opcional("edicao-youtube"),
        areaGeografica: obterValor("edicao-area-geografica"),
        pais: obterValor("edicao-pais").trim(),
        estado: opcional("edicao-estado"),
        cidade: obterValor("edicao-cidade").trim(),
        cep: obterValor("edicao-cep").trim(),
        bairro: obterValor("edicao-bairro").trim(),
        rua: obterValor("edicao-rua").trim(),
        numero: opcional("edicao-numero"),
        complemento: opcional("edicao-complemento"),
        diasHorariosFuncionamento,
        horarioAbertura: opcional("edicao-horario-abertura"),
        horarioFechamento: opcional("edicao-horario-fechamento"),
        atendimentoPresencial: booleano("edicao-presencial"),
        atendimentoOnline: booleano("edicao-online"),
        necessitaAgendamento: booleano("edicao-agendamento"),
        aceitaVoluntarios: booleano("edicao-aceita-voluntarios"),
        frequenciaVoluntariado: opcional("edicao-frequencia"),
        idadeMinimaVoluntario: numeroOpcional("edicao-idade-minima"),
        processoSeletivoVoluntario: booleano("edicao-processo-seletivo"),
        emiteCertificadoVoluntario: booleano("edicao-certificado"),
        atividadesVoluntariado: opcional("edicao-atividades"),
        causasSecundarias,
        ods
    };
}

export function enviarAtualizacaoOsc(requisicaoAutenticada, payload) {
    return requisicaoAutenticada("/osc/put", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    });
}

const STATUS_LABELS = Object.freeze({
    ATIVA: "Ativa", INATIVA: "Inativa", ATIVO: "Ativo", INATIVO: "Inativo",
    ACTIVE: "Ativo", INACTIVE: "Inativo", PENDING: "Pendente", OPEN: "Aberta", CLOSED: "Encerrada"
});
const ORIGIN_LABELS = Object.freeze({
    ONG: "Alterado pela ONG", PORTO_AJUDA: "Alterado pela administração da plataforma",
    ADMINISTRATIVE: "Alterado pela administração da plataforma",
    OWNER: "Alterado pela ONG",
    INDETERMINADA: "Origem não informada"
});
const SUBJECT_LABELS = Object.freeze({
    USER: "Pessoa", OSC: "ONG", PUBLICATION: "Publicação", VOLUNTEERING: "Oportunidade de voluntariado", POST: "Conteúdo"
});

export function labelModerationStatus(value) {
    return typeof value === "string" ? STATUS_LABELS[value] || "Status indisponível" : "Status indisponível";
}
export function labelModerationOrigin(value) {
    if (value == null || value === "") return "";
    return typeof value === "string" ? ORIGIN_LABELS[value] || "Origem não informada" : "Origem não informada";
}
export function labelModerationSubject(value) {
    return typeof value === "string" ? SUBJECT_LABELS[value] || "Registro" : "Registro";
}

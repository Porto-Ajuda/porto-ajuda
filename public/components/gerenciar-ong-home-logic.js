const CARGOS = Object.freeze({
    CRIADOR: "Criador",
    ADMINISTRADOR: "Administrador",
    EDITOR: "Editor",
    MEMBRO: "Membro"
});

const STATUS = Object.freeze({ ATIVA: "Ativa", INATIVA: "Inativa" });

export function normalizarOngsMinhas(payload) {
    if (!Array.isArray(payload)) throw new Error("Não foi possível ler as organizações vinculadas à sua conta.");
    return payload.filter(item => Number.isSafeInteger(Number(item?.id)) && Number(item.id) > 0)
        .map(item => ({
            id: Number(item.id),
            nome: typeof item.nome === "string" && item.nome.trim() ? item.nome.trim() : "Organização sem nome",
            cargo: CARGOS[item.cargo] || "Membro",
            status: item.arquivada ? "Arquivada" : STATUS[item.status] || "Status indisponível",
            arquivada: item.arquivada === true
        }));
}

export function rotaGerenciamentoOng(id, area = "conteudo") {
    const oscId = Number(id);
    if (!Number.isSafeInteger(oscId) || oscId < 1) return null;
    const secao = area === "ong" ? "informacoes" : "gestao-conteudo";
    return `/ong-profile?id=${encodeURIComponent(oscId)}#${secao}`;
}

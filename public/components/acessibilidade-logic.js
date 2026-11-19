export const TAMANHOS_FONTE = Object.freeze({
    pequeno: 90,
    medio: 100,
    grande: 120,
    "extra-grande": 140
});

export function normalizarTamanhoFonte(valor) {
    const numero = Number(valor);
    if (!Number.isFinite(numero)) return 100;
    return Math.min(150, Math.max(80, numero));
}

export function nivelTamanhoFonte(valor) {
    const tamanho = normalizarTamanhoFonte(valor);
    return Object.keys(TAMANHOS_FONTE).find(nome => TAMANHOS_FONTE[nome] === tamanho) || "medio";
}

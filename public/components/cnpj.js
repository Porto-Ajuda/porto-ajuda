const CANONICO = /^[A-Z0-9]{12}[0-9]{2}$/;
const FORMATADO = /^[A-Z0-9]{2}\.[A-Z0-9]{3}\.[A-Z0-9]{3}\/[A-Z0-9]{4}-[0-9]{2}$/;
const PESOS_DV1 = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
const PESOS_DV2 = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];

export function normalizarCnpj(valor) {
    if (typeof valor !== "string") return null;
    if (CANONICO.test(valor)) return valor;
    if (FORMATADO.test(valor)) return valor.replace(/[./-]/g, "");
    return null;
}

export function validarCnpj(valor) {
    const cnpj = normalizarCnpj(valor);
    if (!cnpj || /^([0-9])\1{13}$/.test(cnpj)) return false;

    const valores = [...cnpj].map(caractere => caractere.charCodeAt(0) - 48);
    const calcularDigito = soma => {
        const resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    };

    const somaDv1 = PESOS_DV1.reduce((soma, peso, indice) =>
        soma + valores[indice] * peso, 0);
    const dv1 = calcularDigito(somaDv1);
    if (dv1 !== valores[12]) return false;

    const somaDv2 = PESOS_DV2.reduce((soma, peso, indice) =>
        soma + valores[indice] * peso, 0);
    return calcularDigito(somaDv2) === valores[13];
}

/** Aplica a máscara SS.SSS.SSS/SSSS-NN durante a digitação. */
export function formatarCnpj(valor) {
    const caracteres = String(valor ?? "")
        .toUpperCase()
        .replace(/[^A-Z0-9]/g, "")
        .slice(0, 14);
    let formatado = "";

    for (let i = 0; i < caracteres.length; i++) {
        if (i === 2 || i === 5) formatado += ".";
        if (i === 8) formatado += "/";
        if (i === 12) formatado += "-";
        formatado += caracteres[i];
    }
    return formatado;
}

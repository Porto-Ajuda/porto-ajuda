const REQUISITOS_SENHA = [
    { chave: "tamanho", atende: senha => senha.length >= 8, mensagem: "A senha precisa ter pelo menos 8 caracteres." },
    { chave: "maiuscula", atende: senha => /[A-Z]/.test(senha), mensagem: "A senha precisa conter uma letra maiúscula." },
    { chave: "minuscula", atende: senha => /[a-z]/.test(senha), mensagem: "A senha precisa conter uma letra minúscula." },
    { chave: "numero", atende: senha => /[0-9]/.test(senha), mensagem: "A senha precisa conter um número." },
    { chave: "especial", atende: senha => /[^A-Za-z0-9]/.test(senha), mensagem: "A senha precisa conter um caractere especial." }
];

export function verificarSenhaCadastro(senha) {
    return Object.fromEntries(REQUISITOS_SENHA.map(requisito => [requisito.chave, requisito.atende(senha)]));
}

export function validarSenhasCadastro(senha, confirmacao) {
    const requisitoPendente = REQUISITOS_SENHA.find(requisito => !requisito.atende(senha));
    return {
        erroSenha: requisitoPendente?.mensagem ?? "",
        erroConfirmacao: confirmacao && senha !== confirmacao ? "As senhas não coincidem." : ""
    };
}

export function criarTravaEnvioCadastro() {
    let emAndamento = false;
    return {
        tentarIniciar() {
            if (emAndamento) return false;
            emAndamento = true;
            return true;
        },
        finalizar() {
            emAndamento = false;
        }
    };
}

export function formatarCpfCadastro(valor, inicioSelecao, fimSelecao = inicioSelecao) {
    const texto = String(valor ?? "");
    if (/[^0-9.-]/.test(texto)) {
        return { valido: false, valor: texto, inicioSelecao, fimSelecao, erro: "Use apenas números ou a pontuação padrão do CPF." };
    }

    const digitos = texto.replace(/\D/g, "");
    if (digitos.length > 11) {
        return { valido: false, valor: texto, inicioSelecao, fimSelecao, erro: "O CPF deve ter exatamente 11 dígitos." };
    }

    let formatado = digitos;
    if (digitos.length > 9) {
        formatado = `${digitos.slice(0, 3)}.${digitos.slice(3, 6)}.${digitos.slice(6, 9)}-${digitos.slice(9)}`;
    } else if (digitos.length > 6) {
        formatado = `${digitos.slice(0, 3)}.${digitos.slice(3, 6)}.${digitos.slice(6)}`;
    } else if (digitos.length > 3) {
        formatado = `${digitos.slice(0, 3)}.${digitos.slice(3)}`;
    }

    const digitosAntesDoInicio = (texto.slice(0, inicioSelecao).match(/\d/g) ?? []).length;
    const digitosAntesDoFim = (texto.slice(0, fimSelecao).match(/\d/g) ?? []).length;
    const posicaoAposDigitos = quantidade => {
        if (quantidade === 0) return 0;
        let vistos = 0;
        for (let indice = 0; indice < formatado.length; indice++) {
            if (/\d/.test(formatado[indice])) vistos++;
            if (vistos === quantidade) return indice + 1;
        }
        return formatado.length;
    };

    return {
        valido: true,
        valor: formatado,
        inicioSelecao: posicaoAposDigitos(Math.min(digitosAntesDoInicio, digitos.length)),
        fimSelecao: posicaoAposDigitos(Math.min(digitosAntesDoFim, digitos.length)),
        erro: ""
    };
}

const LOCAL_EMAIL_VALIDO = /^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*$/;
const EMAIL_COMPLETO_VALIDO = /^[^\s@]+@[^\s@]+$/;

export function normalizarEmailCadastro(valor) {
    const email = String(valor ?? "").trim();
    if (!email) return { valor: "", valido: true, erro: "", completado: false };

    if (!email.includes("@")) {
        if (!LOCAL_EMAIL_VALIDO.test(email)) {
            return { valor: email, valido: false, erro: "Digite uma parte local válida para completar o e-mail.", completado: false };
        }
        return { valor: `${email}@gmail.com`, valido: true, erro: "", completado: true };
    }

    if (!EMAIL_COMPLETO_VALIDO.test(email)) {
        return { valor: email, valido: false, erro: "Informe um e-mail completo e válido.", completado: false };
    }
    return { valor: email, valido: true, erro: "", completado: false };
}

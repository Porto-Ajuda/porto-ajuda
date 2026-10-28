import { getAPI_URL } from "./modoBack.js";
import {
    iniciarRenovacaoPeriodica,
    marcarSessaoAutenticada,
    obterTokenCSRF,
    renovarAccessToken
} from "./auth.js";

const API_URL = getAPI_URL();
const respostasComSessaoInvalida = new WeakSet();
const METODOS_SEGUROS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);

function obterCabecalhos(opcoes = {}, csrf = null) {
    const headers = new Headers(opcoes.headers);
    headers.set("Accept", "application/json");

    const metodo = (opcoes.method || "GET").toUpperCase();

    if (!METODOS_SEGUROS.has(metodo)) {
        if (!csrf?.headerName || !csrf?.token) {
            throw new Error("Não foi possível preparar o token CSRF da requisição.");
        }

        headers.set(csrf.headerName, csrf.token);
    }

    return headers;
}

export function sessaoInvalidaConfirmada(resposta) {
    return respostasComSessaoInvalida.has(resposta);
}

function marcarSessaoInvalida(resposta) {
    respostasComSessaoInvalida.add(resposta);
    return resposta;
}

function respostaDeFalhaRenovacao(resultado) {
    if (resultado.response) {
        return resultado.response;
    }

    if (resultado.error) {
        throw resultado.error;
    }

    throw new Error("Não foi possível confirmar a sessão no servidor.");
}

export async function fetchAutenticado(caminho, opcoes = {}) {
    const metodo = (opcoes.method || "GET").toUpperCase();
    const metodoSeguro = METODOS_SEGUROS.has(metodo);

    const fazerRequisicao = async (csrf = null) => {
        const tokenCsrf = !metodoSeguro && !csrf
            ? await obterTokenCSRF()
            : csrf;

        return fetch(`${API_URL}${caminho}`, {
            ...opcoes,
            credentials: "include",
            cache: "no-store",
            headers: obterCabecalhos(opcoes, tokenCsrf)
        });
    };

    let resposta = await fazerRequisicao();
    let refreshValidouSessao = false;

    if (resposta.status === 401) {
        const renovacao = await renovarAccessToken();

        if (!renovacao.ok) {
            if (renovacao.sessaoInvalida) {
                return marcarSessaoInvalida(resposta);
            }

            return respostaDeFalhaRenovacao(renovacao);
        }

        refreshValidouSessao = true;
        // Reutiliza o token obtido pelo refresh. Se a renovação foi
        // compartilhada por outra aba, busca o valor atual antes do retry.
        resposta = await fazerRequisicao(renovacao.csrf);

        // Se reaproveitamos um refresh recente e o retry ainda foi negado,
        // valide o refresh token uma vez sem repetir a requisição protegida.
        if (resposta.status === 401 && renovacao.compartilhada) {
            const confirmacao = await renovarAccessToken({ forcar: true });

            if (!confirmacao.ok) {
                if (confirmacao.sessaoInvalida) {
                    return marcarSessaoInvalida(resposta);
                }

                return respostaDeFalhaRenovacao(confirmacao);
            }
        }
    }

    if (resposta.ok || refreshValidouSessao) {
        marcarSessaoAutenticada();
        iniciarRenovacaoPeriodica();
    }

    return resposta;
}

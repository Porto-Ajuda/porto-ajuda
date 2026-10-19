import { getAPI_URL } from "./modoBack.js";
import {
    iniciarRenovacaoPeriodica,
    marcarSessaoAutenticada,
    renovarAccessToken
} from "./auth.js";

const API_URL = getAPI_URL();
const respostasComSessaoInvalida = new WeakSet();

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
    const fazerRequisicao = () => fetch(`${API_URL}${caminho}`, {
        ...opcoes,
        credentials: "include",
        cache: "no-store",
        headers: {
            Accept: "application/json",
            ...opcoes.headers
        }
    });

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
        resposta = await fazerRequisicao();

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

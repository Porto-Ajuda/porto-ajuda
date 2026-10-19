
import { getAPI_URL } from "./modoBack.js";
import { renovarAccessToken } from "./auth.js";

const API_URL = getAPI_URL();

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

    // Se o acesso expirou, tenta renovar uma única vez.
    if (resposta.status === 401) {
        const renovado = await renovarAccessToken();

        if (!renovado) {
            return resposta;
        }

        // Repete a requisição original com o novo cookie.
        resposta = await fazerRequisicao();
    }

    return resposta;
}

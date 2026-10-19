
import { getAPI_URL } from "./modoBack.js";

const API_URL = getAPI_URL();

/**
 * Tenta renovar os tokens usando o cookie PA_REFRESH.
 * Retorna true quando o backend confirma a renovação.
 */
export async function renovarAccessToken() {
    try {
        const csrfResponse = await fetch(`${API_URL}/usuario/csrf`, {
            method: "GET",
            credentials: "include",
            cache: "no-store"
        });

        if (!csrfResponse.ok) {
            return false;
        }

        const csrf = await csrfResponse.json();

        const refreshResponse = await fetch(`${API_URL}/usuario/refresh`, {
            method: "POST",
            credentials: "include",
            headers: {
                [csrf.headerName]: csrf.token
            },
            cache: "no-store"
        });

        return refreshResponse.ok;
    } catch (erro) {
        console.error("Erro ao renovar a sessão:", erro);
        return false;
    }
}

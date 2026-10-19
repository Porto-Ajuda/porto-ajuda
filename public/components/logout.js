
import { getAPI_URL } from "./modoBack.js";

const API_URL = await getAPI_URL();

document.addEventListener("DOMContentLoaded", () => {
    const botaoLogout = document.getElementById("botao-logout");
    const estadoLogout = document.getElementById("estado-logout");

    if (!botaoLogout || !estadoLogout) {
        return;
    }

    function mostrarEstado(mensagem) {
        estadoLogout.textContent = mensagem;
        estadoLogout.hidden = false;
    }

    async function obterTokenCSRF() {
        const resposta = await fetch(`${API_URL}/usuario/csrf`, {
            method: "GET",
            credentials: "include",
            headers: {
                "Accept": "application/json"
            },
            cache: "no-store"
        });

        if (!resposta.ok) {
            throw new Error("Não foi possível obter o token de segurança.");
        }

        return await resposta.json();
    }

    botaoLogout.addEventListener("click", async () => {
        botaoLogout.disabled = true;
        mostrarEstado("Encerrando sua sessão...");

        try {
            const csrf = await obterTokenCSRF();

            const resposta = await fetch(`${API_URL}/usuario/logout`, {
                method: "POST",
                credentials: "include",
                headers: {
                    "Accept": "application/json",
                    [csrf.headerName]: csrf.token
                },
                cache: "no-store"
            });

            if (!resposta.ok) {
                throw new Error(
                    `Não foi possível encerrar a sessão (HTTP ${resposta.status}).`
                );
            }

            mostrarEstado("Sessão encerrada com sucesso.");

            // Redireciona para a página inicial após o logout.
            window.location.href = "/";
        } catch (erro) {
            console.error("Erro ao sair da conta:", erro);
            mostrarEstado(
                erro.message || "Erro inesperado ao encerrar a sessão."
            );
        } finally {
            botaoLogout.disabled = false;
        }
    });
});

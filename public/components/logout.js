
import { getAPI_URL } from "./modoBack.js";
import { marcarSessaoAnonima } from "./auth.js";
import { notificarToast } from "./notificacoes.js";

const API_URL = await getAPI_URL();

document.addEventListener("DOMContentLoaded", () => {
    const botaoLogout = document.getElementById("botao-logout");

    if (!botaoLogout) {
        return;
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
        botaoLogout.textContent = "Saindo…";
        botaoLogout.setAttribute("aria-busy", "true");

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
                    "Não foi possível confirmar o encerramento da sessão. Tente novamente."
                );
            }

            marcarSessaoAnonima();

            // Redireciona para a página inicial após o logout.
            window.location.href = "/";
        } catch (erro) {
            notificarToast(erro.message || "Não foi possível encerrar a sessão. Tente novamente.", { tipo: "erro" });
        } finally {
            botaoLogout.disabled = false;
            botaoLogout.textContent = "Sair da conta";
            botaoLogout.removeAttribute("aria-busy");
        }
    });
});

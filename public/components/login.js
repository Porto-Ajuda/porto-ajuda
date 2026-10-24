
/* =====================================================
 * login.js
 * Formulário de LOGIN, animação de virar o cartão e
 * botão de mostrar/ocultar senha.
 * Máscaras, etapas e validações do cadastro ficam no
 * cadastro.js.
 * ===================================================== */

import { getAPI_URL } from "./modoBack.js";
import {
    marcarSessaoAnonima,
    marcarSessaoAutenticada,
    obterTokenCSRF
} from "./auth.js";

(function () {
    "use strict";

    const frame = document.getElementById("frame");
    const goCad = document.getElementById("goCad");
    const goLogin = document.getElementById("goLogin");
    const formLogin = document.getElementById("form-login");
    const painelLogin = document.querySelector(".panel-login");
    const painelCadastro = document.querySelector(".panel-cadastro");
    const introLogin = document.getElementById("login-intro");
    const lembrarDeMim = document.getElementById("lembrarDeMim");
    const formCancelarExclusao = document.getElementById("form-cancelar-exclusao");
    const linkCancelarExclusao = document.getElementById("link-cancelar-exclusao");
    const linkCadastro = document.getElementById("link-cadastro");
    const mostrarFormularioCancelamento = document.getElementById("mostrar-cancelamento-exclusao");
    const voltarLoginCancelamento = document.getElementById("voltar-login-cancelamento");
    const estadoCancelarExclusao = document.getElementById("estado-cancelar-exclusao");

    const avisoExclusao = new URLSearchParams(window.location.search)
        .get("exclusao");
    const alertaLogin = document.getElementById("alerta-login");

    if (avisoExclusao === "solicitada" && alertaLogin) {
        alertaLogin.textContent =
            "A conta foi desativada. Você pode cancelar a exclusão dentro do prazo de seis meses usando o acesso de recuperação abaixo.";
        alertaLogin.hidden = false;
    }

    // ---------- ANIMAÇÃO LOGIN <-> CADASTRO ----------

    function sincronizarPainelComHash() {
        const hash = window.location.hash;
        const cadastroAtivo = hash === "#cadastro";
        const recuperacaoAtiva = hash === "#cancelar-exclusao" || hash === "#recuperacao";
        frame?.classList.toggle("flipped", cadastroAtivo);
        painelLogin?.classList.toggle("recuperacao-ativa", recuperacaoAtiva);
        if (painelLogin) {
            painelLogin.inert = cadastroAtivo;
            painelLogin.setAttribute("aria-hidden", String(cadastroAtivo));
        }
        if (painelCadastro) {
            painelCadastro.inert = !cadastroAtivo;
            painelCadastro.setAttribute("aria-hidden", String(!cadastroAtivo));
        }
        if (introLogin) introLogin.hidden = recuperacaoAtiva;
        if (formLogin) formLogin.hidden = recuperacaoAtiva;
        if (linkCancelarExclusao) linkCancelarExclusao.hidden = recuperacaoAtiva;
        if (linkCadastro) linkCadastro.hidden = recuperacaoAtiva;
        if (formCancelarExclusao) formCancelarExclusao.hidden = !recuperacaoAtiva;
        if (avisoExclusao === "solicitada" && alertaLogin) alertaLogin.hidden = recuperacaoAtiva;
        painelLogin?.setAttribute("aria-labelledby", recuperacaoAtiva ? "titulo-recuperacao" : "titulo-login");
    }

    sincronizarPainelComHash();
    window.addEventListener("hashchange", sincronizarPainelComHash);

    if (goCad) {
        goCad.addEventListener("click", () => {
            frame?.classList.add("flipped");
        });
    }

    if (goLogin) {
        goLogin.addEventListener("click", () => {
            frame?.classList.remove("flipped");
        });
    }

    mostrarFormularioCancelamento?.addEventListener("click", (event) => {
        event.preventDefault();
        history.pushState(null, "", "#cancelar-exclusao");
        sincronizarPainelComHash();

        const emailLogin = document.getElementById("email-login");
        const emailRecuperacao = document.getElementById("email-cancelar-exclusao");
        if (emailRecuperacao && emailLogin?.value) {
            emailRecuperacao.value = emailLogin.value.trim();
        }
        document.getElementById("email-cancelar-exclusao")?.focus();
    });

    voltarLoginCancelamento?.addEventListener("click", (event) => {
        event.preventDefault();
        history.pushState(null, "", "#login");
        sincronizarPainelComHash();
        document.getElementById("senha-login")?.focus();
    });

    window.addEventListener("popstate", sincronizarPainelComHash);

    formCancelarExclusao?.addEventListener("submit", async (event) => {
        event.preventDefault();

        const email = document.getElementById("email-cancelar-exclusao")?.value.trim();
        const senha = document.getElementById("senha-cancelar-exclusao")?.value;
        const botao = document.getElementById("confirmar-cancelamento-exclusao");

        if (!email || !senha || !estadoCancelarExclusao || !botao) return;

        botao.disabled = true;
        estadoCancelarExclusao.hidden = false;
        estadoCancelarExclusao.textContent = "Validando e cancelando a solicitação...";

        try {
            const csrf = await obterTokenCSRF();
            const apiUrl = (await getAPI_URL()).replace(/\/+$/, "");
            const response = await fetch(`${apiUrl}/usuario/cancelar-exclusao`, {
                method: "POST",
                credentials: "include",
                cache: "no-store",
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/json",
                    [csrf.headerName]: csrf.token
                },
                body: JSON.stringify({ email, senha })
            });

            if (!response.ok) {
                estadoCancelarExclusao.textContent = response.status === 401
                    ? "Não foi possível validar as credenciais ou a solicitação já venceu."
                    : response.status === 403
                        ? "A proteção da requisição expirou. Atualize a página e tente novamente."
                        : `Não foi possível cancelar a solicitação (HTTP ${response.status}).`;
                return;
            }

            marcarSessaoAnonima();
            const emailLogin = document.getElementById("email-login");
            if (emailLogin) emailLogin.value = email;
            const senhaLogin = document.getElementById("senha-login");
            if (senhaLogin) senhaLogin.value = "";
            const senhaRecuperacao = document.getElementById("senha-cancelar-exclusao");
            if (senhaRecuperacao) senhaRecuperacao.value = "";

            estadoCancelarExclusao.textContent =
                "Solicitação cancelada. Sua conta foi reativada; entre novamente para criar uma nova sessão.";
            botao.disabled = true;
            if (voltarLoginCancelamento) {
                voltarLoginCancelamento.textContent = "Ir para o login";
                voltarLoginCancelamento.focus();
            }
        } catch (error) {
            console.error("Falha no fluxo de recuperação da conta:", error);
            estadoCancelarExclusao.textContent =
                "Não foi possível concluir a recuperação. Tente novamente mais tarde.";
        } finally {
            if (!botao.disabled || !estadoCancelarExclusao.textContent.startsWith("Solicitação cancelada.")) {
                botao.disabled = false;
            }
        }
    });

    // ---------- MOSTRAR / OCULTAR SENHA ----------

    document.querySelectorAll(".mostrar-senha").forEach((botao) => {
        botao.addEventListener("click", () => {
            // Usa aria-controls quando disponível e mantém
            // compatibilidade com os botões antigos.
            const idCampo = botao.getAttribute("aria-controls");

            const campoSenha = idCampo
                ? document.getElementById(idCampo)
                : botao.parentElement?.querySelector("input");

            if (!campoSenha) return;

            const mostrar = campoSenha.type === "password";

            campoSenha.type = mostrar ? "text" : "password";

            botao.setAttribute("aria-pressed", String(mostrar));
            botao.setAttribute(
                "aria-label",
                mostrar ? "Ocultar senha" : "Mostrar senha"
            );

            const icone = botao.querySelector("i");

            if (icone) {
                icone.classList.toggle("fa-eye", !mostrar);
                icone.classList.toggle("fa-eye-slash", mostrar);
            }
        });
    });

    // ---------- LOGIN ----------

    if (formLogin) {
        formLogin.addEventListener("submit", async function (event) {
            event.preventDefault();

            const usuarioLogin = {
                email: document.getElementById("email-login").value.trim(),
                senha: document.getElementById("senha-login").value,
                lembrarDeMim: lembrarDeMim?.checked ?? false
            };

            const botao = formLogin.querySelector("button[type='submit']");

            if (botao) {
                botao.disabled = true;
            }

            try {
                await postLogin(usuarioLogin);
            } finally {
                if (botao) {
                    botao.disabled = false;
                }
            }
        });
    }

    // ---------- ENVIO DO LOGIN ----------

    async function postLogin(usuarioLogin) {
        try {
            // Remove barras finais da URL da API.
            const API_URL = (await getAPI_URL()).replace(/\/+$/, "");

            console.log("================================");
            console.log("API ESCOLHIDA:", API_URL);
            console.log("================================");

            // O helper compartilhado usa a URL de API configurada para o ambiente.
            const csrf = await obterTokenCSRF();

            // Depois, envia as credenciais e o token CSRF.
            const response = await fetch(
                `${API_URL}/usuario/login`,
                {
                    method: "POST",
                    credentials: "include",
                    headers: {
                        "Content-Type": "application/json",
                        "Accept": "application/json",
                        [csrf.headerName]: csrf.token
                    },
                    body: JSON.stringify(usuarioLogin),
                    cache: "no-store"
                }
            );

            const texto = await response.text();

            let data = {};

            try {
                data = texto ? JSON.parse(texto) : {};
            } catch {
                data = {};
            }

            console.log("Resposta do login:", response.status);

            if (response.ok) {
                // Os tokens ficam nos cookies HttpOnly.
                // Não armazenamos JWTs ou dados da sessão no localStorage.
                // O redirecionamento é para o frontend, não para a API.
                marcarSessaoAutenticada({ novaSessao: true });
                window.location.href = "/";
                return;
            }

            if (response.status === 400) {
                mostrarAlerta(
                    "erro",
                    "Dados inválidos",
                    data.message || "Verifique os dados informados."
                );
                return;
            }

            if (response.status === 401) {
                mostrarAlerta(
                    "erro",
                    "Login inválido",
                    data.message || "E-mail ou senha incorretos."
                );
                return;
            }

            if (response.status === 403) {
                mostrarAlerta(
                    "erro",
                    "Acesso recusado",
                    data.message ||
                    "O servidor recusou a requisição. Verifique a proteção CSRF e as configurações de segurança."
                );
                return;
            }

            if (response.status >= 500) {
                mostrarAlerta(
                    "erro",
                    "Erro no servidor",
                    "Ocorreu um problema no servidor. Tente novamente mais tarde."
                );
                return;
            }

            mostrarAlerta(
                "erro",
                "Não foi possível efetuar o login",
                data.message ||
                `O servidor retornou o código ${response.status}.`
            );

        } catch (error) {
            console.error("Erro ao realizar login:", error);

            mostrarAlerta(
                "erro",
                "Erro de conexão",
                error.message ||
                "Não foi possível conectar ao servidor."
            );
        }
    }

})();

const polygon = document.querySelector(".shape");

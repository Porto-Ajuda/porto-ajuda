
/* =====================================================
 * login.js
 * Formulário de LOGIN, animação de virar o cartão e
 * botão de mostrar/ocultar senha.
 * Máscaras, etapas e validações do cadastro ficam no
 * cadastro.js.
 * ===================================================== */

import { getAPI_URL } from "./modoBack.js";

(function () {
    "use strict";

    const frame = document.getElementById("frame");
    const goCad = document.getElementById("goCad");
    const goLogin = document.getElementById("goLogin");
    const formLogin = document.getElementById("form-login");
    const lembrarDeMim = document.getElementById("lembrarDeMim");

    // ---------- ANIMAÇÃO LOGIN <-> CADASTRO ----------

    function moveFrame(side) {
        if (!frame) return;

        if (side === "left") {
            frame.style.left = "15%";
            frame.style.right = "auto";
        } else {
            frame.style.right = "50%";
            frame.style.left = "auto";
        }
    }

    if (goCad) {
        goCad.addEventListener("click", () => {
            if (frame) {
                frame.classList.add("flipped");
                moveFrame("right");
            }
        });
    }

    if (goLogin) {
        goLogin.addEventListener("click", () => {
            if (frame) {
                frame.classList.remove("flipped");
                moveFrame("left");
            }
        });
    }

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
            } else {
                botao.textContent = mostrar ? "🙈" : "👁️";
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

    // ---------- OBTENÇÃO DO TOKEN CSRF ----------

    async function obterTokenCSRF(API_URL) {
        const response = await fetch(
            `${API_URL}/usuario/csrf`,
            {
                method: "GET",
                credentials: "include",
                headers: {
                    "Accept": "application/json"
                },
                cache: "no-store"
            }
        );

        if (!response.ok) {
            throw new Error(
                `Não foi possível obter o token CSRF. HTTP ${response.status}`
            );
        }

        const dados = await response.json();

        if (!dados.token) {
            throw new Error("O servidor não retornou o token CSRF.");
        }

        return dados.token;
    }

    // ---------- ENVIO DO LOGIN ----------

    async function postLogin(usuarioLogin) {
        try {
            // Remove barras finais da URL da API.
            const API_URL = (await getAPI_URL()).replace(/\/+$/, "");

            console.log("================================");
            console.log("API ESCOLHIDA:", API_URL);
            console.log("================================");

            // Primeiro, obtém o token CSRF.
            const tokenCSRF = await obterTokenCSRF(API_URL);

            // Depois, envia as credenciais e o token CSRF.
            const response = await fetch(
                `${API_URL}/usuario/login`,
                {
                    method: "POST",
                    credentials: "include",
                    headers: {
                        "Content-Type": "application/json",
                        "Accept": "application/json",
                        "X-XSRF-TOKEN": tokenCSRF
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

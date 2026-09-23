/* =====================================================
 * login.js
 * Formulário de LOGIN, animação de virar o cartão e
 * botão de mostrar/ocultar senha.
 * Máscaras, etapas e validações do cadastro ficam no
 * cadastro.js.
 * ===================================================== */
(function () {
    "use strict";

    const API_URL = "https://porto-ajuda.up.railway.app";

    const frame = document.getElementById("frame");
    const goCad = document.getElementById("goCad");
    const goLogin = document.getElementById("goLogin");
    const formLogin = document.getElementById("form-login");

    // ---------- ANIMAÇÃO LOGIN <-> CADASTRO ----------
    function moveFrame(side) {
        if (side === "left") {
            frame.style.left = "15%";
            frame.style.right = "auto";
        } else {
            frame.style.right = "50%";
            frame.style.left = "auto";
        }
    }

    goCad.addEventListener("click", () => {
        frame.classList.add("flipped");
        moveFrame("right");
    });

    goLogin.addEventListener("click", () => {
        frame.classList.remove("flipped");
        moveFrame("left");
    });

    // ---------- MOSTRAR / OCULTAR SENHA ----------
    document.querySelectorAll(".mostrar-senha").forEach((botao) => {

        botao.addEventListener("click", () => {

            const campoSenha = botao.previousElementSibling;

            if (campoSenha.type === "password") {
                campoSenha.type = "text";
                botao.textContent = "🙈";
            } else {
                campoSenha.type = "password";
                botao.textContent = "👁️";
            }
        });
    });

    // ---------- LOGIN ----------
    formLogin.addEventListener("submit", async function (event) {

        event.preventDefault();

        const usuarioLogin = {
            email: document.getElementById("email-login").value.trim(),
            senha: document.getElementById("senha-login").value
        };

        const botao = formLogin.querySelector("button[type='submit']");
        botao.disabled = true;

        try {
            await postLogin(usuarioLogin);
        } finally {
            botao.disabled = false;
        }
    });

    async function postLogin(usuarioLogin) {

        try {

            const response = await fetch(`${API_URL}/usuario/login`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(usuarioLogin)
            });

            const texto = await response.text();
            let data = {};

            try {
                data = texto ? JSON.parse(texto) : {};
            } catch (e) {
                data = {};
            }

            console.log("Resposta do login:", response.status);

            if (response.ok) {
                localStorage.setItem("dadosUsuario", JSON.stringify(data));
                window.location.href = "../pages/Porto-Ajuda.html";
                return;
            }

            if (response.status === 400) {
                mostrarAlerta("erro", "Dados inválidos", data.message || "Verifique os dados informados.");
                return;
            }

            if (response.status === 401 || response.status === 403) {
                mostrarAlerta("erro", "Login inválido", data.message || "E-mail ou senha incorretos.");
                return;
            }

            if (response.status >= 500) {
                mostrarAlerta("erro", "Erro no servidor", "Ocorreu um problema no servidor. Tente novamente mais tarde.");
                return;
            }

            mostrarAlerta(
                "erro",
                "Não foi possível efetuar o login",
                data.message || `O servidor retornou o código ${response.status}.`
            );

        } catch (error) {

            console.error("Erro ao realizar login:", error);

            mostrarAlerta(
                "erro",
                "Erro de conexão",
                "Não foi possível conectar ao servidor."
            );
        }
    }

})();

const polygon = document.querySelector('.shape');
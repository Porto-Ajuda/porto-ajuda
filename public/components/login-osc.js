import { formatarCnpj, validarCnpj } from "./cnpj.js";

document.addEventListener("DOMContentLoaded", () => {

    const form = document.getElementById("form-login-osc");
    if (!form) return;

    const campoCnpj = document.getElementById("login-cnpj");
    const campoSenha = document.getElementById("login-senha");
    const erro = document.getElementById("erro-login");
    const btnEntrar = document.getElementById("btn-entrar");

const URL_HOME = "./Porto-Ajuda.html";


    /* ---------- utilidades ---------- */

    function mostrarErro(mensagem, campo) {

        erro.textContent = mensagem;
        erro.hidden = false;

        [campoCnpj, campoSenha].forEach(c => c.removeAttribute("aria-invalid"));

        if (campo) {
            campo.setAttribute("aria-invalid", "true");
            campo.focus();
        }
    }

    function limparErro() {
        erro.hidden = true;
        erro.textContent = "";
        [campoCnpj, campoSenha].forEach(c => c.removeAttribute("aria-invalid"));
    }


    /* ---------- máscara do CNPJ ---------- */

    campoCnpj.addEventListener("input", () => {
        campoCnpj.value = formatarCnpj(campoCnpj.value);

        limparErro();
    });

    campoSenha.addEventListener("input", limparErro);


    /* ---------- mostrar/ocultar senha ---------- */

    form.querySelectorAll(".btn-olho").forEach(botao => {

        botao.addEventListener("click", () => {

            const campo = document.getElementById(botao.dataset.alvo);
            const icone = botao.querySelector("i");
            const mostrar = campo.type === "password";

            campo.type = mostrar ? "text" : "password";
            icone.className = mostrar ? "fa-solid fa-eye-slash" : "fa-solid fa-eye";
            botao.setAttribute("aria-label", mostrar ? "Ocultar senha" : "Mostrar senha");
        });
    });


    /* ---------- envio ---------- */

    form.addEventListener("submit", async (event) => {

        event.preventDefault();
        limparErro();

        if (!campoCnpj.value.trim()) {
            mostrarErro("Informe o CNPJ da ONG.", campoCnpj);
            return;
        }

        if (!validarCnpj(campoCnpj.value)) {
            mostrarErro("CNPJ inválido. Confira os caracteres e dígitos verificadores.", campoCnpj);
            return;
        }

        if (!campoSenha.value) {
            mostrarErro("Informe a sua senha.", campoSenha);
            return;
        }

        btnEntrar.disabled = true;
        btnEntrar.textContent = "Entrando...";

        try {

        

            window.location.href = URL_HOME;

        } catch (e) {

         
            mostrarErro("CNPJ ou senha incorretos.", campoSenha);
            btnEntrar.disabled = false;
            btnEntrar.textContent = "Entrar";
        }
    });

});

document.addEventListener("DOMContentLoaded", () => {

    const form = document.getElementById("form-login-osc");
    if (!form) return;

    const campoCnpj = document.getElementById("login-cnpj");
    const campoSenha = document.getElementById("login-senha");
    const erro = document.getElementById("erro-login");
    const btnEntrar = document.getElementById("btn-entrar");

const URL_HOME = "./Porto-Ajuda.html";


    /* ---------- utilidades ---------- */

    function cnpjValido(valor) {

        const n = valor.replace(/\D/g, "");

        if (n.length !== 14 || /^(\d)\1+$/.test(n)) return false;

        const digito = (tamanho) => {
            let soma = 0;
            let peso = tamanho - 7;

            for (let i = 0; i < tamanho; i++) {
                soma += Number(n[i]) * peso--;
                if (peso < 2) peso = 9;
            }

            const resto = soma % 11;
            return resto < 2 ? 0 : 11 - resto;
        };

        return digito(12) === Number(n[12]) && digito(13) === Number(n[13]);
    }

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
        campoCnpj.value = campoCnpj.value
            .replace(/\D/g, "")
            .slice(0, 14)
            .replace(/^(\d{2})(\d)/, "$1.$2")
            .replace(/^(\d{2})\.(\d{3})(\d)/, "$1.$2.$3")
            .replace(/\.(\d{3})(\d)/, ".$1/$2")
            .replace(/(\d{4})(\d)/, "$1-$2");

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
            mostrarErro("Informe o CNPJ da OSC.", campoCnpj);
            return;
        }

        if (!cnpjValido(campoCnpj.value)) {
            mostrarErro("CNPJ inválido. Confira os números digitados.", campoCnpj);
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
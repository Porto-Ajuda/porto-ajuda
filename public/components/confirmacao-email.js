"use strict";

const parametros = new URLSearchParams(window.location.search);
const status = parametros.get("status");

const icone = document.getElementById("icone");
const titulo = document.getElementById("titulo");
const mensagem = document.getElementById("mensagem");
const botao = document.getElementById("botao");

const formReenvio = document.getElementById("form-reenvio");
const emailReenvio = document.getElementById("email-reenvio");
const botaoReenvio = document.getElementById("botao-reenvio");
const mensagemReenvio = document.getElementById("mensagem-reenvio");

let autenticando = false;

async function autenticarAutomaticamente() {
    if (autenticando) return;
    autenticando = true;

    const fragmento = new URLSearchParams(
        window.location.hash.slice(1)
    );

    const codigo = fragmento.get("codigo");

    history.replaceState(
        null,
        "",
        window.location.pathname + window.location.search
    );

    if (!codigo) {
        mensagem.textContent =
            "Seu e-mail foi confirmado, mas não foi possível iniciar o login automático. Entre na sua conta.";

        // botao.href = "/login";
        botao.href = "login.html";
        botao.textContent = "Entrar na minha conta →";
        autenticando = false;
        return;
    }

    botao.textContent = "Entrando...";
    botao.removeAttribute("href");
    botao.setAttribute("aria-disabled", "true");

    try {
        const resposta = await fetch("/usuario/trocar-codigo", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ codigo })
        });

        const dados = await resposta.json();

        if (!resposta.ok) {
            throw new Error(
                dados.mensagem || "Não foi possível autenticar."
            );
        }

        localStorage.setItem(
            "dadosUsuario",
            JSON.stringify(dados)
        );

        titulo.textContent = "Login realizado!";
        mensagem.textContent =
            "Seu e-mail foi confirmado. Você será direcionado ao Porto Ajuda.";

        botao.textContent = "Acessar o Porto Ajuda →";
        botao.href = "/";

        window.location.replace("/");
    } catch (erro) {
        console.error("Erro no login automático:", erro);

        mensagem.textContent =
            "Seu e-mail foi confirmado, mas não foi possível entrar automaticamente. Faça login para continuar.";

        botao.textContent = "Voltar ao login →";
        botao.href = "/login";
        botao.removeAttribute("aria-disabled");
        autenticando = false;
    }
}

if (status === "erro") {
    document.title = "Confirmação não concluída | Porto Ajuda";

    icone.textContent = "!";
    titulo.textContent = "Link inválido ou expirado";

    mensagem.textContent =
        "Não foi possível confirmar seu e-mail. Você pode solicitar um novo link abaixo ou voltar ao login.";

    botao.href = "/login";
    botao.textContent = "Voltar ao login →";

    formReenvio.hidden = false;

} else if (status === "sucesso") {
    document.title = "E-mail confirmado | Porto Ajuda";

    icone.textContent = "✓";
    titulo.textContent = "E-mail confirmado!";

    mensagem.textContent =
        "Seu endereço de e-mail foi verificado. Estamos entrando na sua conta...";

    botao.textContent = "Entrando...";

    autenticarAutomaticamente();

} else {
    document.title = "Confirmação de e-mail | Porto Ajuda";

    titulo.textContent = "Confirmação de e-mail";

    mensagem.textContent =
        "Acesse o link recebido por e-mail para confirmar sua conta.";

    botao.href = "/login";
    botao.textContent = "Voltar ao login →";
}

// Solicitação de um novo e-mail de confirmação.
formReenvio.addEventListener("submit", async (evento) => {
    evento.preventDefault();

    const email = emailReenvio.value.trim();

    if (!email) return;

    botaoReenvio.disabled = true;
    botaoReenvio.textContent = "Enviando...";
    mensagemReenvio.textContent = "";

    try {
        const resposta = await fetch(
            "/usuario/reenviar-confirmacao",
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({ email })
            }
        );

        const dados = await resposta.json().catch(() => ({}));

        if (!resposta.ok) {
            throw new Error(
                dados.mensagem || "Não foi possível processar a solicitação."
            );
        }

        mensagemReenvio.textContent =
            dados.mensagem ||
            "Se a conta puder receber uma confirmação, enviaremos as instruções para o e-mail informado.";

    } catch (erro) {
        console.error("Erro ao solicitar novo e-mail:", erro);

        mensagemReenvio.textContent =
            "Não foi possível processar a solicitação. Tente novamente mais tarde.";
    } finally {
        botaoReenvio.disabled = false;
        botaoReenvio.textContent =
            "Reenviar e-mail de confirmação";
    }
});
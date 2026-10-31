import { marcarSessaoAutenticada, obterTokenCSRF } from "./auth.js";

const API_BASE_URL = (
    window.location.hostname === "localhost" ||
    window.location.hostname === "127.0.0.1"
)
    ? "http://localhost:8080"
    : "${APP_BACKEND_URL}";

// Configuração do favicon.
(function configurarFavicon() {
    const faviconUrl = "/assets/favicon.png";

    let favicon = document.querySelector('link[rel="icon"]');

    if (!favicon) {
        favicon = document.createElement("link");
        favicon.rel = "icon";
        document.head.appendChild(favicon);
    }

    favicon.type = "image/png";
    favicon.href = faviconUrl;
})();

// Parâmetros da URL.
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

// Lê a resposta da API e trata respostas que não sejam JSON.
async function lerRespostaJSON(resposta) {
    const texto = await resposta.text();

    if (!texto) {
        return {};
    }

    try {
        return JSON.parse(texto);
    } catch {
        throw new Error(
            `A API retornou uma resposta inválida. HTTP ${resposta.status}.`
        );
    }
}

// Login automático após a confirmação do e-mail.
async function autenticarAutomaticamente() {
    if (autenticando) return;

    autenticando = true;

    const fragmento = new URLSearchParams(
        window.location.hash.slice(1)
    );

    const codigo = fragmento.get("codigo");

    // Remove o código da barra de endereço após recuperá-lo.
    history.replaceState(
        null,
        "",
        window.location.pathname + window.location.search
    );

    if (!codigo) {
        mensagem.textContent =
            "Seu e-mail foi confirmado, mas não foi possível iniciar o login automático. Entre na sua conta.";

        botao.href = "/login";
        botao.textContent = "Entrar na minha conta →";

        autenticando = false;
        return;
    }

    botao.textContent = "Entrando...";
    botao.removeAttribute("href");
    botao.setAttribute("aria-disabled", "true");

    try {
        const respostaCSRF = await fetch(
            `${API_BASE_URL}/usuario/csrf`,
            {
                method: "GET",
                credentials: "include",
                headers: {
                    "Accept": "application/json"
                },
                cache: "no-store"
            }
        );

        if (!respostaCSRF.ok) {
            throw new Error(
                `Não foi possível obter o token CSRF. HTTP ${respostaCSRF.status}.`
            );
        }

        const csrf = await respostaCSRF.json();

        if (!csrf.headerName || !csrf.token) {
            throw new Error("O servidor não retornou um token CSRF válido.");
        }

        const resposta = await fetch(
            `${API_BASE_URL}/usuario/trocar-codigo`,
            {
                method: "POST",
                credentials: "include",
                headers: {
                    "Content-Type": "application/json",
                    [csrf.headerName]: csrf.token
                },
                body: JSON.stringify({ codigo }),
                cache: "no-store"
            }
        );

        const dados = await lerRespostaJSON(resposta);

        if (!resposta.ok) {
            throw new Error(
                dados.mensagem ||
                `Não foi possível autenticar. HTTP ${resposta.status}.`
            );
        }

        localStorage.setItem(
            "dadosUsuario",
            JSON.stringify(dados)
        );
        marcarSessaoAutenticada({ novaSessao: true });

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

// Exibição do estado da confirmação.
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
        const csrf = await obterTokenCSRF();
        const resposta = await fetch(
            `${API_BASE_URL}/usuario/reenviar-confirmacao`,
            {
                method: "POST",
                credentials: "include",
                cache: "no-store",
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/json",
                    [csrf.headerName]: csrf.token
                },
                body: JSON.stringify({ email })
            }
        );

        const dados = await lerRespostaJSON(resposta);

        if (!resposta.ok) {
            throw new Error(
                dados.mensagem ||
                `Não foi possível processar a solicitação. HTTP ${resposta.status}.`
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

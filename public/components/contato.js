/* =====================================================
 * contato.js
 * Formulário de contato — Porto Ajuda
 * ===================================================== */

import { getAPI_URL } from "./modoBack.js";

"use strict";

const formulario = document.querySelector("#form-contato");

if (formulario) {
    const botao = formulario.querySelector("button[type='submit']");

    // Elementos da notificação.
    const notificacao = document.querySelector("#notificacao-contato");
    const tituloNotificacao = document.querySelector("#notificacao-titulo");
    const textoNotificacao = document.querySelector("#notificacao-texto");
    const iconeNotificacao = notificacao?.querySelector(".notificacao-icone i");
    const botaoFechar = notificacao?.querySelector(".notificacao-fechar");

    let temporizador;
    let tempoRestante = 6000;
    let inicioContagem = 0;

    function iniciarContagem() {
        clearTimeout(temporizador);

        if (!notificacao || notificacao.hidden) return;

        notificacao.classList.remove("pausada");
        inicioContagem = Date.now();

        temporizador = setTimeout(() => {
            ocultarNotificacao();
        }, tempoRestante);
    }

    function ocultarNotificacao() {
        if (!notificacao) return;

        clearTimeout(temporizador);
        notificacao.classList.remove("visivel", "pausada");

        setTimeout(() => {
            if (!notificacao.classList.contains("visivel")) {
                notificacao.hidden = true;
            }
        }, 300);
    }

    function exibirNotificacao(tipo, titulo, mensagem) {
        if (!notificacao || !tituloNotificacao || !textoNotificacao) {
            console.error(titulo, mensagem);
            return;
        }

        clearTimeout(temporizador);
        tempoRestante = 6000;

        tituloNotificacao.textContent = titulo;
        textoNotificacao.textContent = mensagem;

        notificacao.classList.remove("sucesso", "erro", "pausada");

        if (tipo === "sucesso") {
            notificacao.classList.add("sucesso");

            if (iconeNotificacao) {
                iconeNotificacao.className = "fa-solid fa-check";
            }
        } else {
            notificacao.classList.add("erro");

            if (iconeNotificacao) {
                iconeNotificacao.className =
                    "fa-solid fa-circle-exclamation";
            }
        }

        notificacao.hidden = false;
        notificacao.classList.remove("visivel");

        const progresso = notificacao.querySelector(
            ".notificacao-progresso"
        );

        if (progresso) {
            progresso.style.animation = "none";
            void progresso.offsetWidth;
            progresso.style.animation = "";
        }

        void notificacao.offsetWidth;
        notificacao.classList.add("visivel");

        if (notificacao.matches(":hover")) {
            notificacao.classList.add("pausada");
        } else {
            iniciarContagem();
        }
    }

    // Pausa a notificação enquanto o mouse estiver sobre ela.
    notificacao?.addEventListener("mouseenter", () => {
        if (
            notificacao.hidden ||
            !notificacao.classList.contains("visivel")
        ) {
            return;
        }

        clearTimeout(temporizador);

        tempoRestante -= Date.now() - inicioContagem;
        tempoRestante = Math.max(0, tempoRestante);

        notificacao.classList.add("pausada");
    });

    // Retoma a contagem quando o mouse sair.
    notificacao?.addEventListener("mouseleave", () => {
        if (
            notificacao.hidden ||
            !notificacao.classList.contains("visivel")
        ) {
            return;
        }

        if (tempoRestante <= 0) {
            ocultarNotificacao();
            return;
        }

        iniciarContagem();
    });

    // Fechamento manual.
    botaoFechar?.addEventListener("click", ocultarNotificacao);

    formulario.addEventListener("submit", async (event) => {
        event.preventDefault();

        if (botao.disabled) return;

        const contato = {
            nome: formulario.querySelector("#nome").value.trim(),
            email: formulario.querySelector("#email").value.trim(),
            assunto: formulario.querySelector("#assunto").value.trim(),
            mensagem: formulario.querySelector("#mensagem").value.trim()
        };

        // Validação dos campos obrigatórios.
        if (
            !contato.nome ||
            !contato.email ||
            !contato.assunto ||
            !contato.mensagem
        ) {
            exibirNotificacao(
                "erro",
                "Campos obrigatórios",
                "Preencha todos os campos antes de enviar."
            );
            return;
        }

        const campoEmail = formulario.querySelector("#email");

        if (!campoEmail.validity.valid) {
            exibirNotificacao(
                "erro",
                "E-mail inválido",
                "Informe um endereço de e-mail válido."
            );

            campoEmail.focus();
            return;
        }

        const textoOriginal = botao.innerHTML;

        botao.disabled = true;
        botao.classList.add("enviando");
        botao.innerHTML = `
            <i class="fa-solid fa-spinner fa-spin"
               aria-hidden="true"></i>
            Enviando...
        `;

        try {
            const API_URL = (await getAPI_URL()).replace(/\/+$/, "");

            // 1. Obtém o token CSRF.
            const csrfResponse = await fetch(
                `${API_URL}/usuario/csrf`,
                {
                    method: "GET",
                    credentials: "include",
                    headers: {
                        Accept: "application/json"
                    },
                    cache: "no-store"
                }
            );

            if (!csrfResponse.ok) {
                throw new Error(
                    `Não foi possível obter o token CSRF: HTTP ${csrfResponse.status}`
                );
            }

            const csrf = await csrfResponse.json();

            if (!csrf.headerName || !csrf.token) {
                throw new Error(
                    "O servidor não retornou um token CSRF válido."
                );
            }

            // 2. Envia o formulário com o token CSRF.
            const response = await fetch(`${API_URL}/contato`, {
                method: "POST",
                credentials: "include",
                headers: {
                    "Content-Type": "application/json",
                    Accept: "application/json",
                    [csrf.headerName]: csrf.token
                },
                body: JSON.stringify(contato),
                cache: "no-store"
            });

            // Aceita respostas JSON e respostas sem corpo.
            const texto = await response.text();
            let data = {};

            try {
                data = texto ? JSON.parse(texto) : {};
            } catch {
                data = { message: texto };
            }

            if (!response.ok) {
                throw new Error(
                    data.mensagem ||
                    data.message ||
                    `Falha no envio do contato: HTTP ${response.status}`
                );
            }

            // 3. Informa o sucesso.
            exibirNotificacao(
                "sucesso",
                "Mensagem enviada",
                data.mensagem ||
                data.message ||
                "Sua mensagem foi enviada com sucesso."
            );

            formulario.reset();

        } catch (error) {
            console.error("Erro ao enviar contato:", error);

            let mensagem = error.message ||
                "Ocorreu um erro ao enviar sua mensagem.";

            if (error instanceof TypeError) {
                mensagem =
                    "Não foi possível conectar à API. Verifique sua conexão e tente novamente.";
            }

            exibirNotificacao(
                "erro",
                "Não foi possível enviar",
                mensagem
            );

        } finally {
            botao.disabled = false;
            botao.classList.remove("enviando");
            botao.innerHTML = textoOriginal;
        }
    });
}
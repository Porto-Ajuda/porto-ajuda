
/* =====================================================
 * contato.js
 * Formulário de contato — Porto Ajuda
 * ===================================================== */

import { getAPI_URL } from "./modoBack.js";

"use strict";

const formulario = document.querySelector("#form-contato");

if (formulario) {
    const botao = formulario.querySelector("button[type='submit']");

    // Notificação flutuante já existente no HTML.
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

        if (notificacao.hidden) return;

        notificacao.classList.remove("pausada");

        inicioContagem = Date.now();

        temporizador = setTimeout(() => {
            ocultarNotificacao();
        }, tempoRestante);
    }

    function ocultarNotificacao() {
        clearTimeout(temporizador);

        notificacao.classList.remove("visivel", "pausada");

        // Aguarda a animação de saída antes de ocultar o elemento.
        setTimeout(() => {
            if (!notificacao.classList.contains("visivel")) {
                notificacao.hidden = true;
            }
        }, 300);
    }

    function exibirNotificacao(tipo, titulo, mensagem) {
        clearTimeout(temporizador);

        tempoRestante = 6000;

        tituloNotificacao.textContent = titulo;
        textoNotificacao.textContent = mensagem;

        notificacao.classList.remove("sucesso", "erro", "pausada");

        if (tipo === "sucesso") {
            notificacao.classList.add("sucesso");
            iconeNotificacao.className = "fa-solid fa-check";
        } else {
            notificacao.classList.add("erro");
            iconeNotificacao.className = "fa-solid fa-circle-exclamation";
        }

        notificacao.hidden = false;
        notificacao.classList.remove("visivel");

        // Reinicia a barra de progresso.
        const progresso = notificacao.querySelector(".notificacao-progresso");

        if (progresso) {
            progresso.style.animation = "none";
            void progresso.offsetWidth;
            progresso.style.animation = "";
        }

        // Ativa a transição de entrada.
        void notificacao.offsetWidth;
        notificacao.classList.add("visivel");

        if (notificacao.matches(":hover")) {
            notificacao.classList.add("pausada");
        } else {
            iniciarContagem();
        }
    }

    // Pausa a contagem e a barra enquanto o mouse estiver sobre a notificação.
    notificacao?.addEventListener("mouseenter", () => {
        if (notificacao.hidden || !notificacao.classList.contains("visivel")) {
            return;
        }

        clearTimeout(temporizador);

        tempoRestante -= Date.now() - inicioContagem;
        tempoRestante = Math.max(0, tempoRestante);

        notificacao.classList.add("pausada");
    });

    // Continua de onde parou quando o mouse sair.
    notificacao?.addEventListener("mouseleave", () => {
        if (notificacao.hidden || !notificacao.classList.contains("visivel")) {
            return;
        }

        if (tempoRestante <= 0) {
            ocultarNotificacao();
            return;
        }

        iniciarContagem();
    });

    // Fechamento manual pelo botão X.
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

        // Validação dos campos.
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
            <i class="fa-solid fa-spinner fa-spin" aria-hidden="true"></i>
            Enviando...
        `;

        try {
            const API_URL = getAPI_URL();

            const response = await fetch(`${API_URL}/contato`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(contato)
            });

            const data = await response.json().catch(() => ({}));

            if (!response.ok) {
                throw new Error(
                    data.mensagem ||
                    data.message ||
                    "Não foi possível enviar sua mensagem."
                );
            }

            formulario.reset();

            exibirNotificacao(
                "sucesso",
                "Mensagem enviada!",
                "Recebemos seu contato. Obrigado por falar com o Porto Ajuda."
            );

        } catch (error) {
            console.error("Erro ao enviar contato:", error);

            const mensagemErro =
                error instanceof TypeError ||
                    error.message === "Failed to fetch"
                    ? "Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente."
                    : error.message ||
                    "Ocorreu um erro inesperado. Tente novamente.";

            exibirNotificacao(
                "erro",
                "Não foi possível enviar",
                mensagemErro
            );

        } finally {
            botao.disabled = false;
            botao.classList.remove("enviando");
            botao.innerHTML = textoOriginal;
        }
    });
}
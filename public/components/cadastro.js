/* =====================================================
 * cadastro.js
 * Tudo do formulário de CADASTRO: máscaras, etapas,
 * dropdown de gênero, senha e envio para a API.
 * Está dentro de uma função para não colidir nomes
 * com o login.js.
 * ===================================================== */

import { getAPI_URL } from "./modoBack.js";
import {
    formatarCpfCadastro,
    criarTravaEnvioCadastro,
    normalizarDataCadastro,
    calcularIdadeCadastro,
    normalizarEmailCadastro,
    validarSenhasCadastro,
    verificarSenhaCadastro
} from "./cadastro-logic.js";

(function () {
    "use strict";

    // ---------- ELEMENTOS ----------
    const formCadastro = document.getElementById("form-cadastro");

    const cpf = document.getElementById("cpf");
    const emailCadastro = formCadastro.querySelector("input[type='email']");
    const telefone = document.getElementById("telefone");
    const cep = document.getElementById("cep");
    const nascimento = document.getElementById("nascimento");
    const termos = document.getElementById("check-termos");
    const usarNomeSocial = document.getElementById("usar-nome-social");
    const campoNomeSocial = document.getElementById("campo-nome-social");
    const nomeSocial = document.getElementById("nome-social-cadastro");
    const nomeCivil = document.getElementById("nome-cadastro");

    const dropdownGenero = document.getElementById("dropdown-genero");
    const botaoGenero = document.getElementById("botao-genero");
    const textoGenero = document.getElementById("genero-selecionado");
    const inputGenero = document.getElementById("genero");
    const erroGenero = document.getElementById("erro-genero");

    const nextBtn = document.getElementById("nextBtn");
    const backBtn = document.getElementById("backBtn");
    const cad = document.getElementById("cad");

    const rows = [
        document.querySelector(".row2"),
        document.querySelector(".row3"),
        document.querySelector(".row4")
    ];

    const senhaCadastro = document.getElementById("senha-texto");
    const confirmarSenha = document.getElementById("confirmar-senha");
    const requisitosSenha = document.querySelector(".requisitos-senha");
    const nivelSenha = document.getElementById("nivel-senha");
    const textoSeguranca = document.getElementById("texto-seguranca");

    const reqTamanho = document.getElementById("req-tamanho");
    const reqMaiuscula = document.getElementById("req-maiuscula");
    const reqMinuscula = document.getElementById("req-minuscula");
    const reqNumero = document.getElementById("req-numero");
    const reqEspecial = document.getElementById("req-especial");

    let etapa = 0;

    usarNomeSocial?.addEventListener("change", () => {
        const habilitado = usarNomeSocial.checked;
        if (campoNomeSocial) campoNomeSocial.hidden = !habilitado;
        if (nomeSocial) {
            nomeSocial.disabled = !habilitado;
            nomeSocial.required = habilitado;
            if (habilitado) nomeSocial.focus();
        }
        usarNomeSocial.setAttribute("aria-expanded", String(habilitado));
    });

    // ---------- FUNÇÕES AUXILIARES ----------
    const somenteNumeros = (valor) => valor.replace(/\D/g, "");

    const normalizarData = normalizarDataCadastro;
    const calcularIdade = calcularIdadeCadastro;

    function validarCPF(valor) {
        valor = valor.trim();
        if (/^[0-9]{3}\.[0-9]{3}\.[0-9]{3}-[0-9]{2}$/.test(valor)) {
            valor = valor.replace(/[.-]/g, "");
        } else if (!/^[0-9]{11}$/.test(valor)) {
            return false;
        }

        if (valor.length !== 11) return false;
        if (/^(\d)\1{10}$/.test(valor)) return false;

        let soma = 0;
        for (let i = 0; i < 9; i++) {
            soma += Number(valor[i]) * (10 - i);
        }

        let resto = (soma * 10) % 11;
        if (resto === 10) resto = 0;
        if (resto !== Number(valor[9])) return false;

        soma = 0;
        for (let i = 0; i < 10; i++) {
            soma += Number(valor[i]) * (11 - i);
        }

        resto = (soma * 10) % 11;
        if (resto === 10) resto = 0;
        if (resto !== Number(valor[10])) return false;

        return true;
    }

    const travaEnvioCadastro = criarTravaEnvioCadastro();

    // Tenta extrair uma mensagem útil da resposta do backend
    function extrairMensagem(data) {
        if (!data) return "";

        if (typeof data === "string") return data;

        if (data.message) return data.message;

        if (Array.isArray(data.errors)) {
            return data.errors
                .map((e) => e.defaultMessage || e.message || JSON.stringify(e))
                .join(" | ");
        }

        if (data.errors && typeof data.errors === "object") {
            return Object.values(data.errors).join(" | ");
        }

        if (data.error) return data.error;

        // Mapa campo -> mensagem (ex.: {"cpf": "CPF inválido"})
        const valores = Object.values(data).filter((v) => typeof v === "string");
        return valores.join(" | ");
    }

    // ---------- MÁSCARAS ----------
    cpf.addEventListener("input", () => {
        const resultado = formatarCpfCadastro(
            cpf.value,
            cpf.selectionStart ?? cpf.value.length,
            cpf.selectionEnd ?? cpf.value.length
        );
        cpf.setCustomValidity(resultado.erro);
        if (!resultado.valido) return;

        cpf.value = resultado.valor;
        cpf.setSelectionRange(resultado.inicioSelecao, resultado.fimSelecao);
    });

    function normalizarEmailVisivel({ reportarErro = false } = {}) {
        const resultado = normalizarEmailCadastro(emailCadastro.value);
        emailCadastro.value = resultado.valor;
        emailCadastro.setCustomValidity(resultado.erro);

        if (resultado.valido && resultado.valor && !emailCadastro.checkValidity()) {
            emailCadastro.setCustomValidity("Informe um e-mail completo e válido.");
        }

        if (reportarErro && (!resultado.valido || !emailCadastro.checkValidity())) {
            emailCadastro.reportValidity();
            emailCadastro.focus();
            return false;
        }
        return resultado.valido && (!resultado.valor || emailCadastro.checkValidity());
    }

    emailCadastro.addEventListener("input", () => emailCadastro.setCustomValidity(""));
    emailCadastro.addEventListener("blur", () => normalizarEmailVisivel({ reportarErro: true }));

    telefone.addEventListener("input", () => {
        let v = somenteNumeros(telefone.value).slice(0, 11);

        if (v.length > 10) {
            v = v.replace(/^(\d{2})(\d{5})(\d{0,4})$/, "($1) $2-$3");
        } else if (v.length > 6) {
            v = v.replace(/^(\d{2})(\d{4})(\d{0,4})$/, "($1) $2-$3");
        } else if (v.length > 2) {
            v = v.replace(/^(\d{2})(\d{0,5})$/, "($1) $2");
        } else if (v.length > 0) {
            v = "(" + v;
        }

        telefone.value = v;
        telefone.setCustomValidity("");
    });

    cep.addEventListener("input", () => {
        let v = somenteNumeros(cep.value).slice(0, 8);

        if (v.length > 5) {
            v = v.replace(/^(\d{5})(\d{0,3})$/, "$1-$2");
        }

        cep.value = v;
        cep.setCustomValidity("");
    });

    nascimento.addEventListener("input", () => nascimento.setCustomValidity(""));
    nascimento.addEventListener("change", () => nascimento.setCustomValidity(""));

    // ---------- DROPDOWN DE GÊNERO ----------
    botaoGenero.addEventListener("click", () => {
        dropdownGenero.classList.toggle("aberto");
    });

    document.querySelectorAll("#opcoes-genero button").forEach((opcao) => {
        opcao.addEventListener("click", () => {
            inputGenero.value = opcao.dataset.valor;
            textoGenero.textContent = opcao.textContent.trim();
            dropdownGenero.classList.remove("aberto", "erro");
            erroGenero.style.display = "none";
        });
    });

    document.addEventListener("click", (e) => {
        if (!dropdownGenero.contains(e.target)) {
            dropdownGenero.classList.remove("aberto");
        }
    });

    function resetarGenero() {
        inputGenero.value = "";
        textoGenero.textContent = "Gênero";
        dropdownGenero.classList.remove("aberto", "erro");
        erroGenero.style.display = "none";
    }

    function resetarCadastroCompleto() {
        formCadastro.reset();

        formCadastro.querySelectorAll("input").forEach((campo) => {
            campo.setCustomValidity("");
        });

        if (usarNomeSocial) {
            usarNomeSocial.checked = false;
            usarNomeSocial.setAttribute("aria-expanded", "false");
        }
        if (campoNomeSocial) campoNomeSocial.hidden = true;
        if (nomeSocial) {
            nomeSocial.disabled = true;
            nomeSocial.required = false;
            nomeSocial.value = "";
        }

        nascimento.type = "text";
        resetarGenero();

        atualizarValidadeSenhas();
        senhaCadastro.setCustomValidity("");
        confirmarSenha.setCustomValidity("");
        atualizarIndicadoresSenha();

        if (requisitosSenha) requisitosSenha.style.display = "none";
        nivelSenha.style.width = "0%";
        textoSeguranca.textContent = "Digite uma senha";

        reqTamanho.style.display = "block";
        reqMaiuscula.style.display = "block";
        reqMinuscula.style.display = "block";
        reqNumero.style.display = "block";
        reqEspecial.style.display = "block";

        mostrarEtapa(0);
    }

    // ---------- ETAPAS ----------
    function mostrarEtapa(n) {
        etapa = Math.max(0, Math.min(n, rows.length - 1));

        rows.forEach((row, i) => {
            row.style.display = i === etapa ? "grid" : "none";
        });

        backBtn.style.display = etapa === 0 ? "none" : "block";

        const ultima = etapa === rows.length - 1;
        nextBtn.style.display = ultima ? "none" : "block";
        cad.style.display = ultima ? "block" : "none";
    }

    function validarEtapa(n) {
        if (!Number.isInteger(n) || n < 0 || n >= rows.length) return false;
        if (!rows[n]) return false;

        if (n === 0 && usarNomeSocial?.checked && nomeSocial && !nomeSocial.value.trim()) {
            nomeSocial.setCustomValidity("Informe o nome social ou desmarque a opção.");
            nomeSocial.reportValidity();
            nomeSocial.focus();
            return false;
        }
        nomeSocial?.setCustomValidity("");

        // Campos nativos (required, type=email etc.)
        const campos = rows[n].querySelectorAll("input");

        for (const campo of campos) {
            if (campo.type === "hidden") continue;

            if (!campo.checkValidity()) {
                campo.reportValidity();
                campo.focus();
                return false;
            }
        }

        // ETAPA 1: CPF
        if (n === 0) {
            if (!validarCPF(cpf.value)) {
                cpf.setCustomValidity("Digite um CPF válido.");
                cpf.reportValidity();
                cpf.focus();
                return false;
            }
            cpf.setCustomValidity("");
        }

        // ETAPA 2: gênero, nascimento, telefone, CEP
        if (n === 1) {

            if (!inputGenero.value) {
                dropdownGenero.classList.add("erro");
                erroGenero.style.display = "block";
                return false;
            }

            const dataISO = normalizarData(nascimento.value);
            const idade = dataISO ? calcularIdade(dataISO) : null;

            if (idade === null || idade < 16 || idade > 120) {
                nascimento.setCustomValidity(
                    idade === null
                        ? "Informe uma data de nascimento válida."
                        : idade < 16
                            ? "É necessário ter pelo menos 16 anos para se cadastrar."
                            : "A idade máxima permitida é de 120 anos."
                );

                nascimento.reportValidity();
                nascimento.focus();

                return false;
            }

            nascimento.setCustomValidity("");

            const telefoneNumeros = telefone.value.replace(/\D/g, '');

            if (
                (telefoneNumeros.length !== 10 &&
                    telefoneNumeros.length !== 11) ||
                /^(\d)\1+$/.test(telefoneNumeros)
            ) {

                telefone.setCustomValidity('Digite um telefone válido.');
                telefone.reportValidity();
                telefone.focus();

                return false;
            }

            telefone.setCustomValidity('');

            const cepNumeros = somenteNumeros(cep.value);
            if (cepNumeros.length !== 8 || /^(\d)\1{7}$/.test(cepNumeros)) {
                cep.setCustomValidity("Digite um CEP válido.");
                cep.reportValidity();
                cep.focus();
                return false;
            }
            cep.setCustomValidity("");
        }

        return true;
    }

    nextBtn.addEventListener("click", () => {
        if (!validarEtapa(etapa)) return;
        if (etapa < rows.length - 1) mostrarEtapa(etapa + 1);
    });

    backBtn.addEventListener("click", () => {
        if (etapa > 0) mostrarEtapa(etapa - 1);
    });

    // Estado inicial
    mostrarEtapa(0);

    // ---------- SENHA ----------

    function atualizarPosicaoRequisitos() {

        if (!senhaCadastro || !confirmarSenha || !requisitosSenha) {
            return;
        }

        requisitosSenha.style.display = "flex";

        const senhaRect = senhaCadastro.getBoundingClientRect();
        const largura = requisitosSenha.offsetWidth;
        const altura = requisitosSenha.offsetHeight;

        // =====================================================
        // CELULAR
        // =====================================================

        if (window.innerWidth <= 768) {

            // Centraliza a caixa abaixo da senha
            let esquerda =
                senhaRect.left +
                (senhaRect.width / 2) -
                (largura / 2);

            let topo =
                senhaRect.bottom + 10;

            // Impede sair pela esquerda
            if (esquerda < 10) {
                esquerda = 10;
            }

            // Impede sair pela direita
            if (esquerda + largura > window.innerWidth - 10) {
                esquerda = window.innerWidth - largura - 10;
            }

            requisitosSenha.style.left = `${esquerda}px`;
            requisitosSenha.style.top = `${topo}px`;

        }

        // =====================================================
        // COMPUTADOR
        // =====================================================

        else {

            // Coloca a caixa à direita da senha
            let esquerda = senhaRect.right + 20;

            // Alinha no topo da senha
            let topo = senhaRect.top;

            // Se ultrapassar a tela pela direita,
            // coloca à esquerda da senha
            if (esquerda + largura > window.innerWidth - 10) {

                esquerda = senhaRect.left - largura - 20;
            }

            requisitosSenha.style.left = `${esquerda}px`;
            requisitosSenha.style.top = `${topo}px`;
        }
    }


    senhaCadastro.addEventListener("focus", () => {
        requisitosSenha.style.display = "flex";
        atualizarPosicaoRequisitos();
    });


    senhaCadastro.addEventListener("blur", () => {
        requisitosSenha.style.display = "none";
    });


    function atualizarIndicadoresSenha() {
        const senha = senhaCadastro.value;
        const regras = verificarSenhaCadastro(senha);
        const { tamanho, maiuscula, minuscula, numero, especial } = regras;

        reqTamanho.style.display = tamanho ? "none" : "block";
        reqMaiuscula.style.display = maiuscula ? "none" : "block";
        reqMinuscula.style.display = minuscula ? "none" : "block";
        reqNumero.style.display = numero ? "none" : "block";
        reqEspecial.style.display = especial ? "none" : "block";

        let pontos = 0;

        if (tamanho) pontos++;
        if (maiuscula) pontos++;
        if (minuscula) pontos++;
        if (numero) pontos++;
        if (especial) pontos++;

        if (pontos === 5) {

            requisitosSenha.style.display = "none";

        } else if (document.activeElement === senhaCadastro) {

            requisitosSenha.style.display = "flex";
            atualizarPosicaoRequisitos();
        }

        nivelSenha.style.width = `${pontos * 20}%`;

        if (senha.length === 0) {
            textoSeguranca.textContent = "Digite uma senha";
        } else if (pontos <= 2) {
            textoSeguranca.textContent = "Senha fraca";
        } else if (pontos <= 4) {
            textoSeguranca.textContent = "Senha média";
        } else {
            textoSeguranca.textContent = "Senha forte";
        }

    }

    function atualizarValidadeSenhas() {
        const erros = validarSenhasCadastro(senhaCadastro.value, confirmarSenha.value);
        senhaCadastro.setCustomValidity(erros.erroSenha);
        confirmarSenha.setCustomValidity(erros.erroConfirmacao);
    }

    senhaCadastro.addEventListener("input", () => {
        atualizarIndicadoresSenha();
        atualizarValidadeSenhas();
    });
    confirmarSenha.addEventListener("input", atualizarValidadeSenhas);

    // ---------- ENVIO ----------
    formCadastro.addEventListener("submit", async function (event) {

        event.preventDefault();
        // O formulário usa novalidate para que esta normalização também rode ao enviar com Enter.
        if (!normalizarEmailVisivel({ reportarErro: true })) return;

        // Enter numa etapa intermediária: apenas avança
        if (etapa < rows.length - 1) {
            nextBtn.click();
            return;
        }

        // Termos
        if (!termos.checked) {
            termos.focus();
            if (!document.querySelector(".alerta-personalizado")) {
                window.mostrarAlerta?.(
                    "aviso",
                    "Atenção",
                    "Para continuar seu cadastro, você precisa aceitar os Termos de Uso e a Política de Privacidade."
                );
            }
            return;
        }

        // Senha forte
        atualizarValidadeSenhas();
        if (!senhaCadastro.checkValidity()) {
            senhaCadastro.reportValidity();
            senhaCadastro.focus();
            return;
        }

        // Confirmação
        if (!confirmarSenha.checkValidity()) {
            confirmarSenha.reportValidity();
            confirmarSenha.focus();
            return;
        }

        // Revalida as etapas anteriores (garante que nada ficou vazio)
        for (let i = 0; i < rows.length - 1; i++) {
            mostrarEtapa(i);
            if (!validarEtapa(i)) {
                return;
            }
        }
        mostrarEtapa(rows.length - 1);

        const usuario = {
            cpf: somenteNumeros(cpf.value),

            cep: somenteNumeros(cep.value),

            nome: nomeCivil.value.trim(),

            nomeSocial: usarNomeSocial?.checked ? nomeSocial.value.trim() : "",

            dataNascimento: normalizarData(nascimento.value),

            email: emailCadastro.value,

            // "nao-binario" -> "NAO_BINARIO"
            genero: inputGenero.value.toUpperCase().replace(/-/g, "_"),

            telefone: somenteNumeros(telefone.value),

            senha: senhaCadastro.value
        };

        if (!travaEnvioCadastro.tentarIniciar()) return;
        cad.disabled = true;

        try {
            await postCadastro(usuario);
        } finally {
            travaEnvioCadastro.finalizar();
            cad.disabled = false;
        }
    });


    async function postCadastro(usuario) {
        try {
            const API_URL = (await getAPI_URL()).replace(/\/+$/, "");

            // 1. Obtém o token CSRF antes do cadastro.
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
                    "Não foi possível iniciar o cadastro. Atualize a página e tente novamente."
                );
            }

            const csrf = await csrfResponse.json();

            if (!csrf.headerName || !csrf.token) {
                throw new Error(
                    "O servidor não retornou os dados necessários do token CSRF."
                );
            }

            // 2. Envia o cadastro com cookies e token CSRF.
            const response = await fetch(
                `${API_URL}/usuario/register`,
                {
                    method: "POST",
                    credentials: "include",
                    headers: {
                        "Content-Type": "application/json",
                        Accept: "application/json",
                        [csrf.headerName]: csrf.token
                    },
                    body: JSON.stringify(usuario),
                    cache: "no-store"
                }
            );

            const texto = await response.text();
            let data = {};

            try {
                data = texto ? JSON.parse(texto) : {};
            } catch {
                data = { message: texto };
            }

            console.log("Resposta do cadastro:", response.status);

            const mensagem = extrairMensagem(data);

            if (response.ok) {
                mostrarAlertaCadastro(
                    mensagem || "Sua conta foi criada com sucesso."
                );
                return;
            }

            if (response.status === 400) {
                mostrarAlerta(
                    "erro",
                    "Dados inválidos",
                    mensagem || "Verifique os dados informados."
                );
                return;
            }

            if (response.status === 401 || response.status === 403) {
                mostrarAlerta(
                    "erro",
                    "Acesso negado",
                    mensagem || "O servidor recusou a requisição."
                );
                return;
            }

            if (response.status === 409) {
                mostrarAlerta(
                    "aviso",
                    "Usuário já cadastrado",
                    mensagem || "Já existe um usuário com esses dados."
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
                "Não foi possível cadastrar",
                mensagem || "Não foi possível criar a conta. Revise os dados e tente novamente."
            );
        } catch (error) {
            console.error("Erro ao realizar cadastro:", error);

            mostrarAlerta(
                "erro",
                "Erro de conexão ou segurança",
                error.message || "Não foi possível concluir o cadastro."
            );
        }
    }


    // ---------- ALERTA DE SUCESSO ----------
    function mostrarAlertaCadastro(mensagem) {
        const alerta = document.getElementById("alerta-cadastro");
        const texto = alerta.querySelector(".conteudo-cadastro p");

        texto.textContent = mensagem || "Sua conta foi criada com sucesso.";

        alerta.hidden = false;
        document.body.style.overflow = "hidden";
    }

    document.getElementById("btn-ir-login").addEventListener("click", function () {

        const alerta = document.getElementById("alerta-cadastro");
        alerta.hidden = true;
        document.body.style.overflow = "";

        // Limpa o formulário e volta para a primeira etapa
        resetarCadastroCompleto();

        // Volta para o painel de login (o login.js cuida da animação)
        document.getElementById("goLogin").click();

        const emailLogin = document.getElementById("email-login");

        if (emailLogin) {
            setTimeout(() => emailLogin.focus(), 500);
        }
    });

})();

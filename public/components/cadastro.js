/* =====================================================
 * cadastro.js
 * Tudo do formulário de CADASTRO: máscaras, etapas,
 * dropdown de gênero, senha e envio para a API.
 * Está dentro de uma função para não colidir nomes
 * com o login.js.
 * ===================================================== */
(function () {
    "use strict";

    const API_URL = "https://porto-ajuda.up.railway.app";

    // ---------- ELEMENTOS ----------
    const formCadastro = document.getElementById("form-cadastro");

    const cpf = document.getElementById("cpf");
    const telefone = document.getElementById("telefone");
    const cep = document.getElementById("cep");
    const nascimento = document.getElementById("nascimento");
    const termos = document.getElementById("check-termos");

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

    // ---------- FUNÇÕES AUXILIARES ----------
    const somenteNumeros = (valor) => valor.replace(/\D/g, "");

    // Aceita "2000-01-31" (input date) ou "31/01/2000" e devolve ISO.
    // Se o formato for inválido, devolve "".
    function normalizarData(valor) {
        valor = (valor || "").trim();

        if (/^\d{4}-\d{2}-\d{2}$/.test(valor)) {
            return valor;
        }

        if (/^\d{2}\/\d{2}\/\d{4}$/.test(valor)) {
            const [dia, mes, ano] = valor.split("/");
            return `${ano}-${mes}-${dia}`;
        }

        return "";
    }

    function calcularIdade(iso) {
        // Evita o problema de fuso horário do new Date("YYYY-MM-DD")
        const [ano, mes, dia] = iso.split("-").map(Number);
        const hoje = new Date();

        let idade = hoje.getFullYear() - ano;

        if (
            hoje.getMonth() + 1 < mes ||
            (hoje.getMonth() + 1 === mes && hoje.getDate() < dia)
        ) {
            idade--;
        }

        return idade;
    }

    function validarCPF(valor) {
        valor = somenteNumeros(valor);

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

    function verificarSenha(senha) {
        return {
            tamanho: senha.length >= 8,
            maiuscula: /[A-Z]/.test(senha),
            minuscula: /[a-z]/.test(senha),
            numero: /[0-9]/.test(senha),
            especial: /[^A-Za-z0-9]/.test(senha)
        };
    }

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
        let v = somenteNumeros(cpf.value).slice(0, 11);

        v = v
            .replace(/(\d{3})(\d)/, "$1.$2")
            .replace(/(\d{3})(\d)/, "$1.$2")
            .replace(/(\d{3})(\d{1,2})$/, "$1-$2");

        cpf.value = v;
        cpf.setCustomValidity("");
    });

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

    // ---------- ETAPAS ----------
    function mostrarEtapa(n) {
        etapa = n;

        rows.forEach((row, i) => {
            row.style.display = i === n ? "grid" : "none";
        });

        backBtn.style.display = n === 0 ? "none" : "block";

        const ultima = n === rows.length - 1;
        nextBtn.style.display = ultima ? "none" : "block";
        cad.style.display = ultima ? "block" : "none";
    }

    function validarEtapa(n) {

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

            if (!dataISO) {
                nascimento.setCustomValidity("Informe uma data de nascimento válida.");
                nascimento.reportValidity();
                nascimento.focus();
                return false;
            }

            const idade = calcularIdade(dataISO);

            if (idade < 16 || idade > 120) {
                nascimento.setCustomValidity(
                    idade < 16
                        ? "É necessário ter 16 anos ou mais."
                        : "Informe uma data de nascimento válida."
                );
                nascimento.reportValidity();
                nascimento.focus();
                return false;
            }
            nascimento.setCustomValidity("");

            const tel = somenteNumeros(telefone.value);

            if (tel.length !== 10 && tel.length !== 11) {
                telefone.setCustomValidity("Digite um telefone válido.");
                telefone.reportValidity();
                telefone.focus();
                return false;
            }
            telefone.setCustomValidity("");

            if (somenteNumeros(cep.value).length !== 8) {
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
        mostrarEtapa(etapa + 1);
    });

    backBtn.addEventListener("click", () => {
        if (etapa > 0) mostrarEtapa(etapa - 1);
    });

    // Estado inicial
    mostrarEtapa(0);

    // ---------- SENHA ----------
    senhaCadastro.addEventListener("focus", () => {
        const posicao = senhaCadastro.getBoundingClientRect();

        requisitosSenha.style.display = "flex";
        requisitosSenha.style.left = `${posicao.right + 30}px`;
        requisitosSenha.style.top = `${posicao.top}px`;
    });

    senhaCadastro.addEventListener("blur", () => {
        requisitosSenha.style.display = "none";
    });

    senhaCadastro.addEventListener("input", () => {
        const senha = senhaCadastro.value;
        const r = verificarSenha(senha);

        reqTamanho.style.display = r.tamanho ? "none" : "block";
        reqMaiuscula.style.display = r.maiuscula ? "none" : "block";
        reqMinuscula.style.display = r.minuscula ? "none" : "block";
        reqNumero.style.display = r.numero ? "none" : "block";
        reqEspecial.style.display = r.especial ? "none" : "block";

        const pontos = Object.values(r).filter(Boolean).length;

        if (pontos === 5) {
            requisitosSenha.style.display = "none";
        } else if (document.activeElement === senhaCadastro) {
            requisitosSenha.style.display = "flex";
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

        senhaCadastro.setCustomValidity("");

        // Reavalia a confirmação
        confirmarSenha.setCustomValidity(
            confirmarSenha.value && confirmarSenha.value !== senha
                ? "As senhas não são iguais."
                : ""
        );
    });

    confirmarSenha.addEventListener("input", () => {
        confirmarSenha.setCustomValidity(
            confirmarSenha.value !== senhaCadastro.value
                ? "As senhas não são iguais."
                : ""
        );
    });

    // ---------- ENVIO ----------
    formCadastro.addEventListener("submit", async function (event) {

        event.preventDefault();

        // Enter numa etapa intermediária: apenas avança
        if (etapa < rows.length - 1) {
            nextBtn.click();
            return;
        }

        // Termos
        if (!termos.checked) {
            termos.reportValidity();
            return;
        }

        // Senha forte
        const r = verificarSenha(senhaCadastro.value);

        if (!Object.values(r).every(Boolean)) {
            senhaCadastro.setCustomValidity("A senha não atende aos requisitos de segurança.");
            senhaCadastro.reportValidity();
            senhaCadastro.focus();
            return;
        }
        senhaCadastro.setCustomValidity("");

        // Confirmação
        if (senhaCadastro.value !== confirmarSenha.value) {
            confirmarSenha.setCustomValidity("As senhas não são iguais.");
            confirmarSenha.reportValidity();
            confirmarSenha.focus();
            return;
        }
        confirmarSenha.setCustomValidity("");

        // Revalida as etapas anteriores (garante que nada ficou vazio)
        for (let i = 0; i < rows.length - 1; i++) {
            if (!validarEtapa(i)) {
                mostrarEtapa(i);
                return;
            }
        }

        const usuario = {
            cpf: somenteNumeros(cpf.value),

            cep: somenteNumeros(cep.value),

            nome: document
                .querySelector("#form-cadastro input[placeholder='Nome']")
                .value.trim(),

            nomeSocial: "",

            dataNascimento: normalizarData(nascimento.value),

            email: document
                .querySelector("#form-cadastro input[type='email']")
                .value.trim(),

            // "nao-binario" -> "NAO_BINARIO"
            genero: inputGenero.value.toUpperCase().replace(/-/g, "_"),

            telefone: somenteNumeros(telefone.value),

            senha: senhaCadastro.value
        };

        // Para depuração (a senha não é exibida)
        console.log("Payload do cadastro:", { ...usuario, senha: "***" });

        cad.disabled = true;

        try {
            await postCadastro(usuario);
        } finally {
            cad.disabled = false;
        }
    });

    async function postCadastro(usuario) {

        try {

            const response = await fetch(`${API_URL}/usuario/register`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(usuario)
            });

            // Lê o corpo como texto e tenta converter em JSON
            const texto = await response.text();
            let data = {};

            try {
                data = texto ? JSON.parse(texto) : {};
            } catch (e) {
                data = texto; // resposta que não é JSON
            }

            console.log("Resposta do cadastro:", response.status, data);

            const mensagem = extrairMensagem(data);

            if (response.ok) {
                mostrarAlertaCadastro(mensagem || "Sua conta foi criada com sucesso.");
                return;
            }

            if (response.status === 400) {
                mostrarAlerta("erro", "Dados inválidos", mensagem || "Verifique os dados informados.");
                return;
            }

            if (response.status === 401 || response.status === 403) {
                mostrarAlerta(
                    "erro",
                    "Acesso negado",
                    mensagem || "O servidor recusou a requisição (verifique CORS e permissões)."
                );
                return;
            }

            if (response.status === 409) {
                mostrarAlerta("aviso", "Usuário já cadastrado", mensagem || "Já existe um usuário cadastrado com esses dados.");
                return;
            }

            if (response.status >= 500) {
                mostrarAlerta("erro", "Erro no servidor", "Ocorreu um problema no servidor. Tente novamente mais tarde.");
                return;
            }

            mostrarAlerta(
                "erro",
                "Não foi possível cadastrar",
                mensagem || `O servidor retornou o código ${response.status}.`
            );

        } catch (error) {

            console.error("Erro ao realizar cadastro:", error);

            mostrarAlerta(
                "erro",
                "Erro de conexão",
                "Não foi possível conectar ao servidor."
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
        formCadastro.reset();
        resetarGenero();
        mostrarEtapa(0);

        nivelSenha.style.width = "0%";
        textoSeguranca.textContent = "Digite uma senha";

        // Volta para o painel de login (o login.js cuida da animação)
        document.getElementById("goLogin").click();

        const emailLogin = document.getElementById("email-login");

        if (emailLogin) {
            setTimeout(() => emailLogin.focus(), 500);
        }
    });

})();
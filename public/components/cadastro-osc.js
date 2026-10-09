document.addEventListener("DOMContentLoaded", () => {

    /* =====================================================
       ELEMENTOS PRINCIPAIS
    ===================================================== */

    const form = document.getElementById("form-osc");
    if (!form) return;

    const frame = document.getElementById("frame");
    const etapas = form.querySelectorAll(".row.etapa-osc");

    const btnVoltar = document.getElementById("voltar-osc");
    const btnProximo = document.getElementById("proximo-osc");
    const btnCadastrar = document.getElementById("cadastrar-osc");

    const progressoTexto = document.getElementById("progresso-texto");
    const progressoBarra = document.getElementById("progresso-barra");

    const alertaCadastro = document.getElementById("alerta-cadastro");
    const btnIrLogin = document.getElementById("btn-ir-login");

    // página de login da OSC (mesma pasta)
    const URL_LOGIN = "login-osc.html";

    let etapaAtual = 0;

    // elementos da etapa de áreas (declarados aqui no topo para evitar problemas de ordem)
    const dropdownFoco = document.getElementById("dropdown-foco");
    const botaoFoco = document.getElementById("botao-foco");
    const focoSelecionado = document.getElementById("foco-selecionado");
    const focoInput = document.getElementById("foco-principal");
    const opcoesFoco = document.getElementById("opcoes-foco");

    const botaoAdicionarArea = document.getElementById("botao-adicionar-area");
    const dropdownSecundario = document.getElementById("dropdown-secundario");
    const opcoesSecundarias = document.getElementById("opcoes-secundarias");
    const areasSelecionadas = document.getElementById("areas-selecionadas");
    const areasEscolhidas = new Set();

    const botaoAdicionarOds = document.getElementById("botao-adicionar-ods");
    const dropdownOds = document.getElementById("dropdown-ods");
    const opcoesOds = document.getElementById("opcoes-ods");
    const odsSelecionadas = document.getElementById("ods-selecionadas");
    const odsEscolhidas = new Set();


    /* =====================================================
       ETAPAS
    ===================================================== */

    function mostrarEtapa(numero) {

        etapas.forEach((etapa, index) => {
            etapa.classList.toggle("ativa", index === numero);
        });

        fecharTodosDropdowns();
        atualizarBotoes();
        atualizarProgresso();

        etapas[numero]?.scrollTo({ top: 0 });
        frame?.scrollTo({ top: 0 });
    }

    function atualizarBotoes() {

        const primeira = etapaAtual === 0;
        const ultima = etapaAtual === etapas.length - 1;

        // [hidden] é !important no CSS; os botões ficam no rodapé fixo do card
        btnVoltar.hidden = primeira;
        btnProximo.hidden = ultima;
        btnCadastrar.hidden = !ultima;
    }

    function atualizarProgresso() {

        const total = etapas.length;

        if (progressoTexto) progressoTexto.textContent = `Etapa ${etapaAtual + 1} de ${total}`;
        if (progressoBarra) progressoBarra.style.width = `${((etapaAtual + 1) / total) * 100}%`;
    }


    /* =====================================================
       VALIDAÇÕES AUXILIARES
    ===================================================== */

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

    function requisitosSenha(senha, confirmacao) {
        return {
            tamanho: senha.length >= 8,
            maiuscula: /[A-Z]/.test(senha),
            minuscula: /[a-z]/.test(senha),
            numero: /\d/.test(senha),
            especial: /[^A-Za-z0-9]/.test(senha),
            igual: senha.length > 0 && senha === confirmacao
        };
    }

    function limparErro() {
        form.querySelectorAll(".msg-error").forEach(el => el.remove());
    }

    function mostrarErro(elemento, mensagem) {

        limparErro();

        const erro = document.createElement("p");
        erro.className = "msg-error";
        erro.setAttribute("role", "alert");
        erro.textContent = mensagem;

        // dentro de .campo, para respeitar a grade
        const alvo = elemento.closest(".campo") || elemento;
        alvo.insertAdjacentElement("beforeend", erro);
        alvo.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }

    function validarEtapa() {

        limparErro();

        const etapa = etapas[etapaAtual];

        // 1) Área principal (input hidden não é validado pelo navegador)
        if (etapa.contains(focoInput) && !focoInput.value) {
            mostrarErro(botaoFoco, "Selecione a área principal da ONG.");
            botaoFoco.focus();
            return false;
        }

        // 2) Campos nativos (required, type=email, url, min, max...)
        const campos = etapa.querySelectorAll("input, select, textarea");

        for (const campo of campos) {

            if (campo.type === "hidden" || campo.disabled) continue;

            if (!campo.checkValidity()) {
                campo.reportValidity();
                campo.focus();
                return false;
            }
        }

        // 3) CNPJ
        const cnpj = form.elements["cnpj"];

        if (etapa.contains(cnpj) && !cnpjValido(cnpj.value)) {
            mostrarErro(cnpj, "Informe um CNPJ válido.");
            cnpj.focus();
            return false;
        }

        // 4) Horários
        const abertura = form.elements["horario_abertura"];
        const fechamento = form.elements["horario_fechamento"];

        if (
            etapa.contains(abertura) &&
            abertura.value && fechamento.value &&
            fechamento.value <= abertura.value
        ) {
            mostrarErro(fechamento, "O horário de fechamento deve ser depois do de abertura.");
            fechamento.focus();
            return false;
        }

        // 5) Senha
        const senha = form.elements["senha"];
        const confirmar = form.elements["confirmar_senha"];

        if (etapa.contains(senha)) {

            const req = requisitosSenha(senha.value, confirmar.value);

            if (!req.tamanho || !req.maiuscula || !req.minuscula || !req.numero || !req.especial) {
                mostrarErro(senha, "A senha precisa cumprir todos os requisitos abaixo.");
                senha.focus();
                return false;
            }

            if (!req.igual) {
                mostrarErro(confirmar, "As senhas não coincidem.");
                confirmar.focus();
                return false;
            }
        }

        return true;
    }


    /* =====================================================
       NAVEGAÇÃO
    ===================================================== */

    btnProximo.addEventListener("click", () => {

        if (!validarEtapa()) return;

        if (etapaAtual < etapas.length - 1) {
            etapaAtual++;
            mostrarEtapa(etapaAtual);
        }
    });

    btnVoltar.addEventListener("click", () => {

        if (etapaAtual > 0) {
            etapaAtual--;
            mostrarEtapa(etapaAtual);
        }
    });


    /* =====================================================
       ÁREA PRINCIPAL
    ===================================================== */

    botaoFoco?.addEventListener("click", (event) => {
        event.stopPropagation();
        alternarLista(opcoesFoco);
    });

    opcoesFoco?.querySelectorAll("button").forEach(opcao => {

        opcao.addEventListener("click", () => {

            focoInput.value = opcao.dataset.valor;
            focoSelecionado.textContent = opcao.textContent.trim();
            opcoesFoco.classList.remove("aberto");

            // se a nova principal já estava como secundária, remove
            if (areasEscolhidas.has(focoInput.value)) {
                areasSelecionadas
                    .querySelector(`.area-selecionada[data-valor="${focoInput.value}"]`)
                    ?.remove();
                areasEscolhidas.delete(focoInput.value);
            }

            limparErro();
            atualizarAreasSecundarias();
        });
    });


    /* =====================================================
       ÁREAS SECUNDÁRIAS
    ===================================================== */

    botaoAdicionarArea?.addEventListener("click", (event) => {
        event.stopPropagation();
        alternarLista(opcoesSecundarias);
    });

    opcoesSecundarias?.querySelectorAll("button").forEach(opcao => {

        opcao.addEventListener("click", () => {

            const valor = opcao.dataset.valor;
            const nome = opcao.textContent.trim();

            if (focoInput.value === valor || areasEscolhidas.has(valor)) return;

            areasEscolhidas.add(valor);

            criarChip({
                container: areasSelecionadas,
                classe: "area-selecionada",
                classeBotao: "remover-area",
                valor,
                rotulo: nome,
                nomeInput: "areas_secundarias[]",
                aoRemover: () => {
                    areasEscolhidas.delete(valor);
                    atualizarAreasSecundarias();
                }
            });

            atualizarAreasSecundarias();
        });
    });

    function atualizarAreasSecundarias() {

        if (!opcoesSecundarias) return;

        opcoesSecundarias.querySelectorAll("button").forEach(opcao => {

            const valor = opcao.dataset.valor;
            const esconder = areasEscolhidas.has(valor) || valor === focoInput.value;

            opcao.disabled = esconder;
            opcao.style.display = esconder ? "none" : "";
        });
    }


    /* =====================================================
       ODS
    ===================================================== */

    botaoAdicionarOds?.addEventListener("click", (event) => {
        event.stopPropagation();
        alternarLista(opcoesOds);
    });

    opcoesOds?.querySelectorAll("button").forEach(opcao => {

        opcao.addEventListener("click", () => {

            const valor = opcao.dataset.valor;
            const numero = `ODS ${valor}`;
            const nome = opcao.textContent.replace(numero, "").trim();

            if (odsEscolhidas.has(valor)) return;

            odsEscolhidas.add(valor);

            criarChip({
                container: odsSelecionadas,
                classe: "ods-selecionada",
                classeBotao: "remover-ods",
                valor,
                rotulo: nome,
                numero,
                nomeInput: "ods[]",
                aoRemover: () => {
                    odsEscolhidas.delete(valor);
                    atualizarOds();
                }
            });

            atualizarOds();
        });
    });

    function atualizarOds() {

        if (!opcoesOds) return;

        opcoesOds.querySelectorAll("button").forEach(opcao => {

            const selecionada = odsEscolhidas.has(opcao.dataset.valor);

            opcao.disabled = selecionada;
            opcao.style.display = selecionada ? "none" : "";
        });
    }


    function criarChip({ container, classe, classeBotao, valor, rotulo, numero, nomeInput, aoRemover }) {

        if (!container) return;

        const item = document.createElement("div");
        item.className = classe;
        item.dataset.valor = valor;

        const texto = document.createElement("span");

        if (numero) {
            const num = document.createElement("span");
            num.className = "ods-numero";
            num.textContent = numero;
            texto.appendChild(num);
            texto.appendChild(document.createTextNode(" "));
        }

        texto.appendChild(document.createTextNode(rotulo));

        const botao = document.createElement("button");
        botao.type = "button";
        botao.className = classeBotao;
        botao.setAttribute("aria-label", `Remover ${numero || rotulo}`);
        botao.textContent = "×";

        const hidden = document.createElement("input");
        hidden.type = "hidden";
        hidden.name = nomeInput;
        hidden.value = valor;

        botao.addEventListener("click", () => {
            item.remove();
            aoRemover();
            reposicionarAbertas();
        });

        item.append(texto, botao, hidden);
        container.appendChild(item);

        reposicionarAbertas();
    }


    const listas = [
        { lista: opcoesFoco,        gatilho: botaoFoco },
        { lista: opcoesSecundarias, gatilho: botaoAdicionarArea },
        { lista: opcoesOds,         gatilho: botaoAdicionarOds }
    ].filter(item => item.lista && item.gatilho);

    function posicionarLista({ lista, gatilho }) {

        const r = gatilho.getBoundingClientRect();

        const folga = 8;
        const alturaIdeal = 220;

        const espacoAbaixo = window.innerHeight - r.bottom - folga;
        const espacoAcima = r.top - folga;

        // abre para cima só se embaixo estiver apertado e houver mais espaço em cima
        const paraCima = espacoAbaixo < 160 && espacoAcima > espacoAbaixo;

        const espaco = Math.max(
            120,
            Math.min(alturaIdeal, paraCima ? espacoAcima : espacoAbaixo)
        );

        lista.style.left = `${r.left}px`;
        lista.style.width = `${r.width}px`;
        lista.style.maxHeight = `${espaco}px`;

        if (paraCima) {
            lista.style.top = "auto";
            lista.style.bottom = `${window.innerHeight - r.top + 6}px`;
        } else {
            lista.style.bottom = "auto";
            lista.style.top = `${r.bottom + 6}px`;
        }
    }

    function reposicionarAbertas() {
        listas
            .filter(item => item.lista.classList.contains("aberto"))
            .forEach(posicionarLista);
    }

    function alternarLista(lista) {

        const item = listas.find(i => i.lista === lista);
        const abrir = !lista.classList.contains("aberto");

        fecharTodosDropdowns();

        if (abrir && item) {
            posicionarLista(item);
            lista.classList.add("aberto");
        }
    }

    function fecharTodosDropdowns() {
        opcoesFoco?.classList.remove("aberto");
        opcoesSecundarias?.classList.remove("aberto");
        opcoesOds?.classList.remove("aberto");
    }

    document.addEventListener("click", (event) => {

        if (!dropdownFoco?.contains(event.target)) {
            opcoesFoco?.classList.remove("aberto");
        }

        if (
            !dropdownSecundario?.contains(event.target) &&
            !botaoAdicionarArea?.contains(event.target)
        ) {
            opcoesSecundarias?.classList.remove("aberto");
        }

        if (
            !dropdownOds?.contains(event.target) &&
            !botaoAdicionarOds?.contains(event.target)
        ) {
            opcoesOds?.classList.remove("aberto");
        }
    });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") fecharTodosDropdowns();
    });

    // ao rolar a etapa, a lista se desprenderia do botão: fecha
    etapas.forEach(etapa => {
        etapa.addEventListener("scroll", fecharTodosDropdowns, { passive: true });
    });

    window.addEventListener("resize", reposicionarAbertas);

    const camposVoluntariado = form.querySelectorAll("[data-voluntariado]");

    function atualizarVoluntariado() {

        const aceita = form.querySelector('input[name="aceita_voluntarios"]:checked')?.value === "sim";

        camposVoluntariado.forEach(campo => {

            campo.hidden = !aceita;

            campo.querySelectorAll("input, select, textarea").forEach(el => {
                el.disabled = !aceita;

                if (!aceita) {
                    if (el.type === "radio" || el.type === "checkbox") el.checked = false;
                    else el.value = "";
                }
            });
        });
    }

    form.querySelectorAll('input[name="aceita_voluntarios"]')
        .forEach(radio => radio.addEventListener("change", atualizarVoluntariado));



    const doacoesSim = document.getElementById("doacoes-sim");
    const doacoesNao = document.getElementById("doacoes-nao");
    const tipoPix = document.getElementById("tipo-chave-pix");
    const chavePix = document.getElementById("chave-pix");
    const linkDoacao = document.getElementById("link-doacao");

    function atualizarCamposDoacao() {

        const aceita = !!doacoesSim?.checked;

        [tipoPix, chavePix, linkDoacao].forEach(campo => {

            if (!campo) return;

            campo.disabled = !aceita;

            if (!aceita) campo.value = "";
        });
    }

    doacoesSim?.addEventListener("change", atualizarCamposDoacao);
    doacoesNao?.addEventListener("change", atualizarCamposDoacao);

    const materiaisAceitos = document.getElementById("materiais-aceitos");

    function atualizarMateriais() {

        const aceita = form.querySelector(
            'input[name="aceita_doacoes_materiais"]:checked'
        )?.value === "sim";

        if (materiaisAceitos) {
            materiaisAceitos.disabled = !aceita;
            if (!aceita) materiaisAceitos.value = "";
        }
    }

    form.querySelectorAll('input[name="aceita_doacoes_materiais"]')
        .forEach(radio => radio.addEventListener("change", atualizarMateriais));


    const senha = document.getElementById("senha-osc");
    const confirmarSenha = document.getElementById("confirmar-senha-osc");
    const listaRequisitos = document.getElementById("requisitos");

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

    function atualizarRequisitos() {

        if (!senha || !listaRequisitos) return;

        const req = requisitosSenha(senha.value, confirmarSenha.value);

        listaRequisitos.querySelectorAll("li").forEach(item => {
            item.classList.toggle("ok", !!req[item.dataset.req]);
        });
    }

    senha?.addEventListener("input", atualizarRequisitos);
    confirmarSenha?.addEventListener("input", atualizarRequisitos);


    const cnpj = document.getElementById("cnpj");
    const cep = document.getElementById("cep");

    cnpj?.addEventListener("input", () => {
        cnpj.value = cnpj.value
            .replace(/\D/g, "")
            .slice(0, 14)
            .replace(/^(\d{2})(\d)/, "$1.$2")
            .replace(/^(\d{2})\.(\d{3})(\d)/, "$1.$2.$3")
            .replace(/\.(\d{3})(\d)/, ".$1/$2")
            .replace(/(\d{4})(\d)/, "$1-$2");
    });

    cep?.addEventListener("input", () => {
        cep.value = cep.value
            .replace(/\D/g, "")
            .slice(0, 8)
            .replace(/^(\d{5})(\d)/, "$1-$2");
    });


    /* =====================================================
       ENVIO DO FORMULÁRIO
    ===================================================== */

    form.addEventListener("submit", (event) => {

        event.preventDefault();

  
        if (etapaAtual < etapas.length - 1) {
            btnProximo.click();
            return;
        }

        if (!validarEtapa()) return;

        const dados = new FormData(form);

        dados.delete("senha");
        dados.delete("confirmar_senha");
        console.log("Cadastro da OSC:", Object.fromEntries(dados));

        if (alertaCadastro) {
            alertaCadastro.hidden = false;
            btnIrLogin?.focus();
        }
    });

    btnIrLogin?.addEventListener("click", () => {
        window.location.href = URL_LOGIN;
    });


    /* =====================================================
       INICIALIZAÇÃO
    ===================================================== */

    mostrarEtapa(0);
    atualizarAreasSecundarias();
    atualizarOds();
    atualizarVoluntariado();
    atualizarCamposDoacao();
    atualizarMateriais();
    atualizarRequisitos();

});
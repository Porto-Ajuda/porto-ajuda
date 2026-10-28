import { getAPI_URL } from "./modoBack.js";
import { fetchAutenticado } from "./api.js";

const API_URL = getAPI_URL();

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
    const statusCadastro = document.getElementById("status-cadastro-osc");
    const linkLogin = document.getElementById("link-login-cadastro");

    let etapaAtual = 0;

    /* =====================================================
       ELEMENTOS DAS ÁREAS E ODS
    ===================================================== */

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
    const odsSugestoes = document.getElementById("ods-sugestoes");
    const odsEscolhidas = new Set();

    const CAUSA_PARA_ENUM = {
        "assistencia-social": ["INCLUSAO_SOCIAL", "COMBATE_POBREZA"],
        "protecao-animal": ["ANIMAIS"],
        "meio-ambiente": ["MEIO_AMBIENTE"],
        educacao: ["EDUCACAO"],
        saude: ["SAUDE"],
        "direitos-humanos": ["INCLUSAO_SOCIAL"],
        cultura: ["CULTURA"],
        esporte: ["ESPORTE"]
    };

    const AREA_POR_CAUSA = {
        INCLUSAO_SOCIAL: "assistencia-social",
        COMBATE_POBREZA: "assistencia-social",
        ANIMAIS: "protecao-animal",
        MEIO_AMBIENTE: "meio-ambiente",
        EDUCACAO: "educacao",
        SAUDE: "saude",
        CULTURA: "cultura",
        ESPORTE: "esporte"
    };

    const SUGESTOES_ODS_POR_AREA = {
        "assistencia-social": [1, 10, 16],
        "protecao-animal": [15],
        "meio-ambiente": [6, 7, 11, 12, 13, 14, 15],
        educacao: [4],
        saude: [3],
        "direitos-humanos": [5, 10, 16],
        cultura: [4, 11],
        esporte: [3, 4]
    };

    /* =====================================================
       ELEMENTOS DO CNPJ E CEP
    ===================================================== */

    const cnpj = document.getElementById("cnpj");
    const cep = document.getElementById("cep");
    const statusConsultaCnpj = document.getElementById("status-consulta-cnpj");

    let cnpjConsultadoValor = "";
    let cnpjElegivel = false;
    let consultaCnpjEmAndamento = null;

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

        btnVoltar.hidden = primeira;
        btnProximo.hidden = ultima;
        btnCadastrar.hidden = !ultima;
    }

    function atualizarProgresso() {
        const total = etapas.length;

        if (progressoTexto) {
            progressoTexto.textContent = `Etapa ${etapaAtual + 1} de ${total}`;
        }

        if (progressoBarra) {
            progressoBarra.style.width =
                `${((etapaAtual + 1) / total) * 100}%`;
        }
    }

    /* =====================================================
       VALIDAÇÕES AUXILIARES
    ===================================================== */

    function cnpjValido(valor) {
        const n = valor.replace(/\D/g, "");

        if (n.length !== 14 || /^(\d)\1+$/.test(n)) {
            return false;
        }

        const digito = (tamanho) => {
            let soma = 0;
            let peso = tamanho - 7;

            for (let i = 0; i < tamanho; i++) {
                soma += Number(n[i]) * peso--;

                if (peso < 2) {
                    peso = 9;
                }
            }

            const resto = soma % 11;
            return resto < 2 ? 0 : 11 - resto;
        };

        return digito(12) === Number(n[12])
            && digito(13) === Number(n[13]);
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

        const alvo = elemento.closest(".campo") || elemento;
        alvo.insertAdjacentElement("beforeend", erro);
        alvo.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }

    /* =====================================================
       CONSULTA DO CNPJ NA API
    ===================================================== */

    function mostrarStatusCnpj(mensagem, estado = "") {
        if (!statusConsultaCnpj) return;

        statusConsultaCnpj.textContent = mensagem;
        statusConsultaCnpj.hidden = !mensagem;
        statusConsultaCnpj.dataset.estado = estado;
    }

    async function consultarCnpj() {
        if (!cnpj) return false;

        const numero = cnpj.value.replace(/\D/g, "");

        if (!cnpjValido(numero)) {
            cnpjConsultadoValor = "";
            cnpjElegivel = false;

            mostrarStatusCnpj(
                numero.length === 0
                    ? ""
                    : "Confira os 14 dígitos do CNPJ.",
                "erro"
            );

            return false;
        }

        if (cnpjConsultadoValor === numero) {
            return cnpjElegivel;
        }

        if (consultaCnpjEmAndamento?.numero === numero) {
            return consultaCnpjEmAndamento.promise;
        }

        cnpjElegivel = false;
        mostrarStatusCnpj("Consultando o CNPJ...", "carregando");

        const promise = (async () => {
            try {
                const resposta = await fetch(
                    `${API_URL}/osc/consultar-cnpj/${numero}`
                );

                if (!resposta.ok) {
                    throw new Error(`Erro HTTP ${resposta.status}`);
                }

                const resultado = await resposta.json();

                // Não aplica uma resposta antiga a um CNPJ diferente.
                if (cnpj.value.replace(/\D/g, "") !== numero) {
                    return false;
                }

                cnpjConsultadoValor = numero;
                cnpjElegivel = resultado.elegivel === true;

                mostrarStatusCnpj(
                    resultado.motivo ||
                    (cnpjElegivel
                        ? "CNPJ aprovado na verificação inicial."
                        : "CNPJ não aprovado na verificação inicial."),
                    cnpjElegivel ? "sucesso" : "erro"
                );

                return cnpjElegivel;

            } catch (erro) {
                console.error("Erro ao consultar CNPJ:", erro);

                if (cnpj.value.replace(/\D/g, "") === numero) {
                    cnpjConsultadoValor = "";
                    cnpjElegivel = false;

                    mostrarStatusCnpj(
                        "Não foi possível consultar o CNPJ. Verifique sua conexão e tente novamente.",
                        "erro"
                    );
                }

                return false;

            } finally {
                if (consultaCnpjEmAndamento?.numero === numero) {
                    consultaCnpjEmAndamento = null;
                }
            }
        })();

        consultaCnpjEmAndamento = { numero, promise };

        return promise;
    }

    if (cnpj) {
        cnpj.addEventListener("blur", consultarCnpj);

        cnpj.addEventListener("input", () => {
            cnpjConsultadoValor = "";
            cnpjElegivel = false;
            mostrarStatusCnpj("");
        });

        // Máscara: 00.000.000/0000-00
        cnpj.addEventListener("input", () => {
            cnpj.value = cnpj.value
                .replace(/\D/g, "")
                .slice(0, 14)
                .replace(/^(\d{2})(\d)/, "$1.$2")
                .replace(/^(\d{2})\.(\d{3})(\d)/, "$1.$2.$3")
                .replace(/\.(\d{3})(\d)/, ".$1/$2")
                .replace(/(\d{4})(\d)/, "$1-$2");
        });
    }

    if (cep) {
        cep.addEventListener("input", () => {
            cep.value = cep.value
                .replace(/\D/g, "")
                .slice(0, 8)
                .replace(/^(\d{5})(\d)/, "$1-$2");
        });
    }

    /* =====================================================
       VALIDAÇÃO DA ETAPA ATUAL
    ===================================================== */

    async function validarEtapa() {
        limparErro();

        const etapa = etapas[etapaAtual];

        if (!etapa) return false;

        // 1. Causa principal
        if (etapa.contains(focoInput) && !focoInput.value) {
            mostrarErro(botaoFoco, "Selecione a causa principal da ONG.");
            botaoFoco.focus();
            return false;
        }

        // 2. Campos nativos
        const campos = etapa.querySelectorAll("input, select, textarea");

        for (const campo of campos) {
            if (campo.type === "hidden" || campo.disabled) continue;

            if (!campo.checkValidity()) {
                campo.reportValidity();
                campo.focus();
                return false;
            }
        }

        // 3. CNPJ
        if (cnpj && etapa.contains(cnpj)) {
            if (!cnpjValido(cnpj.value)) {
                mostrarErro(cnpj, "Informe um CNPJ válido.");
                cnpj.focus();
                return false;
            }

            const aprovado = await consultarCnpj();

            if (!aprovado) {
                mostrarErro(
                    cnpj,
                    "É necessário um CNPJ aprovado na verificação inicial para continuar."
                );
                cnpj.focus();
                return false;
            }
        }

        // 4. Horários
        const abertura = form.elements["horario_abertura"];
        const fechamento = form.elements["horario_fechamento"];

        if (
            abertura &&
            fechamento &&
            etapa.contains(abertura) &&
            abertura.value &&
            fechamento.value &&
            fechamento.value <= abertura.value
        ) {
            mostrarErro(
                fechamento,
                "O horário de fechamento deve ser depois do de abertura."
            );
            fechamento.focus();
            return false;
        }

        if (
            abertura &&
            fechamento &&
            etapa.contains(abertura) &&
            Boolean(abertura.value) !== Boolean(fechamento.value)
        ) {
            const campoIncompleto = abertura.value ? fechamento : abertura;
            mostrarErro(campoIncompleto, "Informe os dois horários ou deixe ambos vazios.");
            campoIncompleto.focus();
            return false;
        }

        return true;
    }

    /* =====================================================
       NAVEGAÇÃO
    ===================================================== */

    btnProximo.addEventListener("click", async () => {
        if (!await validarEtapa()) return;

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

            limparErro();
            atualizarAreasSecundarias();
            atualizarSugestoesOds();
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

            if (areasEscolhidas.has(valor)) {
                return;
            }

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
                    atualizarSugestoesOds();
                }
            });

            atualizarAreasSecundarias();
            atualizarSugestoesOds();
        });
    });

    function atualizarAreasSecundarias() {
        if (!opcoesSecundarias) return;

        opcoesSecundarias.querySelectorAll("button").forEach(opcao => {
            const valor = opcao.dataset.valor;
            const esconder = areasEscolhidas.has(valor);

            opcao.disabled = esconder;
            opcao.style.display = esconder ? "none" : "";
        });
    }

    /* =====================================================
       ODS
    ===================================================== */

    function selecionarOds(valor) {
        const numeroOds = Number(valor);
        if (!Number.isInteger(numeroOds) || numeroOds < 1 || numeroOds > 17) return;

        const valorNormalizado = String(numeroOds);
        if (odsEscolhidas.has(valorNormalizado)) return;

        const opcao = opcoesOds?.querySelector(`button[data-valor="${valorNormalizado}"]`);
        const numero = `ODS ${valorNormalizado}`;
        const nome = opcao?.textContent.replace(numero, "").trim() || numero;
        odsEscolhidas.add(valorNormalizado);

        criarChip({
            container: odsSelecionadas,
            classe: "ods-selecionada",
            classeBotao: "remover-ods",
            valor: valorNormalizado,
            rotulo: nome,
            numero,
            nomeInput: "ods[]",
            aoRemover: () => {
                odsEscolhidas.delete(valorNormalizado);
                atualizarOds();
                atualizarSugestoesOds();
            }
        });
        atualizarOds();
        atualizarSugestoesOds();
    }

    botaoAdicionarOds?.addEventListener("click", (event) => {
        event.stopPropagation();
        alternarLista(opcoesOds);
    });

    opcoesOds?.querySelectorAll("button").forEach(opcao => {
        opcao.addEventListener("click", () => selecionarOds(opcao.dataset.valor));
    });

    function atualizarOds() {
        if (!opcoesOds) return;

        opcoesOds.querySelectorAll("button").forEach(opcao => {
            const selecionada = odsEscolhidas.has(opcao.dataset.valor);

            opcao.disabled = selecionada;
            opcao.style.display = selecionada ? "none" : "";
        });
    }

    function atualizarSugestoesOds() {
        if (!odsSugestoes) return;

        const areaPrincipal = AREA_POR_CAUSA[focoInput?.value];
        const areas = [areaPrincipal, ...areasEscolhidas].filter(Boolean);
        const sugestoes = new Set(
            areas.flatMap(area => SUGESTOES_ODS_POR_AREA[area] || [])
        );
        odsSugestoes.replaceChildren();

        const pendentes = [...sugestoes].filter(
            numero => !odsEscolhidas.has(String(numero))
        );

        if (pendentes.length === 0) {
            odsSugestoes.textContent = areas.length
                ? "As sugestões já estão selecionadas. Você ainda pode incluir outras ODS no menu."
            : "Selecione uma causa principal para ver sugestões.";
            return;
        }

        pendentes.forEach(numero => {
            const botao = document.createElement("button");
            botao.type = "button";
            botao.className = "ods-sugestao";
            botao.textContent = `Adicionar ODS ${numero}`;
            botao.addEventListener("click", () => selecionarOds(numero));
            odsSugestoes.appendChild(botao);
        });
    }

    /* =====================================================
       CRIAÇÃO DOS CHIPS
    ===================================================== */

    function criarChip({
        container,
        classe,
        classeBotao,
        valor,
        rotulo,
        numero,
        nomeInput,
        aoRemover
    }) {
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

    /* =====================================================
       POSICIONAMENTO DOS DROPDOWNS
    ===================================================== */

    const listas = [
        { lista: opcoesFoco, gatilho: botaoFoco },
        { lista: opcoesSecundarias, gatilho: botaoAdicionarArea },
        { lista: opcoesOds, gatilho: botaoAdicionarOds }
    ].filter(item => item.lista && item.gatilho);

    function posicionarLista({ lista, gatilho }) {
        const r = gatilho.getBoundingClientRect();

        const folga = 8;
        const alturaIdeal = 220;

        const espacoAbaixo = window.innerHeight - r.bottom - folga;
        const espacoAcima = r.top - folga;

        const paraCima =
            espacoAbaixo < 160 && espacoAcima > espacoAbaixo;

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

    etapas.forEach(etapa => {
        etapa.addEventListener("scroll", fecharTodosDropdowns, {
            passive: true
        });
    });

    window.addEventListener("resize", reposicionarAbertas);

    /* =====================================================
       VOLUNTARIADO
    ===================================================== */

    const camposVoluntariado = form.querySelectorAll("[data-voluntariado]");

    function atualizarVoluntariado() {
        const aceita = form.querySelector(
            'input[name="aceita_voluntarios"]:checked'
        )?.value === "sim";

        camposVoluntariado.forEach(campo => {
            campo.hidden = !aceita;

            campo.querySelectorAll("input, select, textarea").forEach(el => {
                el.disabled = !aceita;

                if (!aceita) {
                    if (el.type === "radio" || el.type === "checkbox") {
                        el.checked = false;
                    } else {
                        el.value = "";
                    }
                }
            });
        });
    }

    form.querySelectorAll('input[name="aceita_voluntarios"]')
        .forEach(radio => {
            radio.addEventListener("change", atualizarVoluntariado);
        });

    /* =====================================================
       DOAÇÕES FINANCEIRAS
    ===================================================== */

    /* =====================================================
       ENVIO DO FORMULÁRIO
    ===================================================== */

    function valorTexto(nome) {
        const valor = form.elements[nome]?.value?.trim();
        return valor || null;
    }

    function valorSimNao(nome) {
        const valor = form.querySelector(`input[name="${nome}"]:checked`)?.value;
        if (valor === "sim") return true;
        if (valor === "nao") return false;
        return null;
    }

    function mostrarStatusCadastro(mensagem, tipo = "erro") {
        if (!statusCadastro) return;
        statusCadastro.textContent = mensagem;
        statusCadastro.dataset.tipo = tipo;
        statusCadastro.hidden = !mensagem;
        if (mensagem) statusCadastro.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }

    async function mensagemErroResposta(resposta) {
        try {
            const corpo = await resposta.clone().json();
            return corpo.detail || corpo.mensagem || corpo.message
                || "Não foi possível salvar o cadastro da OSC.";
        } catch {
            return "Não foi possível salvar o cadastro da OSC. Tente novamente.";
        }
    }

    function montarCausas() {
        const causaPrincipal = focoInput?.value || null;
        const causasSecundarias = new Set(
            [...areasEscolhidas]
                .flatMap(area => CAUSA_PARA_ENUM[area] || [])
                .filter(causa => causa !== causaPrincipal)
        );

        return {
            causaPrincipal,
            causasSecundarias: [...causasSecundarias]
        };
    }

    form.addEventListener("submit", async (event) => {
        event.preventDefault();

        if (etapaAtual < etapas.length - 1 || btnCadastrar.disabled) return;
        mostrarStatusCadastro("");
        if (linkLogin) linkLogin.hidden = true;
        if (!await validarEtapa()) return;

        const arquivoLogo = form.elements["logo"]?.files?.[0] || null;
        if (arquivoLogo && arquivoLogo.size > 10 * 1024 * 1024) {
            mostrarStatusCadastro("A imagem da logo não pode ultrapassar 10 MB.");
            form.elements["logo"].focus();
            return;
        }

        const diasFuncionamento = [...form.querySelectorAll('input[name="dias_funcionamento[]"]:checked')]
            .map(campo => campo.value);
        const payload = {
            cnpj: cnpj.value.replace(/\D/g, ""),
            ...montarCausas(),
            ods: [...odsEscolhidas].map(Number),
            nome: valorTexto("nome_ong"),
            descricaoCurta: valorTexto("descricao_curta"),
            descricao: valorTexto("descricao_completa"),
            anoFundacao: Number.parseInt(valorTexto("ano_fundacao"), 10),
            email: valorTexto("email_institucional"),
            telefone: valorTexto("telefone"),
            whatsapp: valorTexto("whatsapp"),
            site: valorTexto("site"),
            linkedin: valorTexto("linkedin"),
            instagram: valorTexto("instagram"),
            youtube: valorTexto("youtube"),
            areaGeografica: valorTexto("area_geografica"),
            pais: valorTexto("pais"),
            estado: valorTexto("estado"),
            cidade: valorTexto("cidade"),
            cep: valorTexto("cep"),
            bairro: valorTexto("bairro"),
            rua: valorTexto("rua"),
            numero: valorTexto("numero"),
            complemento: valorTexto("complemento"),
            diasFuncionamento,
            horarioAbertura: valorTexto("horario_abertura"),
            horarioFechamento: valorTexto("horario_fechamento"),
            atendimentoPresencial: !!form.elements["atendimento_presencial"]?.checked,
            atendimentoOnline: !!form.elements["atendimento_online"]?.checked,
            necessitaAgendamento: valorSimNao("necessita_agendamento"),
            aceitaVoluntarios: valorSimNao("aceita_voluntarios"),
            frequenciaVoluntariado: valorTexto("frequencia_voluntariado"),
            idadeMinimaVoluntario: valorTexto("idade_minima_voluntario")
                ? Number.parseInt(valorTexto("idade_minima_voluntario"), 10)
                : null,
            processoSeletivoVoluntario: valorSimNao("processo_seletivo_voluntario"),
            emiteCertificadoVoluntario: valorSimNao("emite_certificado_voluntario"),
            atividadesVoluntariado: valorTexto("atividades_voluntariado")
        };

        const multipart = new FormData();
        multipart.append(
            "dados",
            new Blob([JSON.stringify(payload)], { type: "application/json" })
        );
        if (arquivoLogo) multipart.append("logo", arquivoLogo);

        btnCadastrar.disabled = true;
        btnCadastrar.setAttribute("aria-busy", "true");
        const textoOriginal = btnCadastrar.textContent;
        btnCadastrar.textContent = "Enviando...";

        try {
            const resposta = await fetchAutenticado("/osc/create", {
                method: "POST",
                body: multipart
            });

            if (!resposta.ok) {
                const detalhe = await mensagemErroResposta(resposta);
                if (resposta.status === 401 || resposta.status === 403) {
                    mostrarStatusCadastro(
                        resposta.status === 401
                            ? "Entre em uma conta ativa e confirmada para cadastrar a OSC. Seus dados foram mantidos."
                            : "Sua conta não tem permissão para cadastrar uma OSC. Seus dados foram mantidos."
                    );
                    if (linkLogin) linkLogin.hidden = false;
                } else {
                    mostrarStatusCadastro(detalhe);
                }
                return;
            }

            if (alertaCadastro) {
                alertaCadastro.hidden = false;
                btnIrLogin?.focus();
            }
        } catch (erro) {
            console.error("Falha ao enviar cadastro da OSC:", erro);
            mostrarStatusCadastro(
                erro instanceof Error
                    ? `${erro.message} Seus dados foram mantidos; tente novamente.`
                    : "Não foi possível conectar ao serviço. Seus dados foram mantidos; tente novamente."
            );
        } finally {
            btnCadastrar.disabled = false;
            btnCadastrar.removeAttribute("aria-busy");
            btnCadastrar.textContent = textoOriginal;
        }
    });

    btnIrLogin?.addEventListener("click", () => {
        window.location.href = "/";
    });

    /* =====================================================
       INICIALIZAÇÃO
    ===================================================== */

    mostrarEtapa(0);
    atualizarAreasSecundarias();
    atualizarOds();
    atualizarSugestoesOds();
    atualizarVoluntariado();

});

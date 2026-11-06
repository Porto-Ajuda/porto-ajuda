import { getAPI_URL } from "./modoBack.js";
import {
    fetchAutenticado,
    sessaoInvalidaConfirmada
} from "./api.js";
import { marcarSessaoAnonima, obterTokenCSRF } from "./auth.js";

/* =========================================================
   CONSTANTES
   ========================================================= */

const API_URL = getAPI_URL();

const ROTAS = API_URL === "http://localhost:8080"
    || API_URL === "https://api.portoajuda.com.br"
    ? {
        cadastroOng: "/cadastro-osc",
        perfilOng: "ong-profile"
    }
    : {
        cadastroOng: "cadastro-osc.html",
        perfilOng: "ong-profile"
    };

const TAMANHO_MAXIMO_FOTO = 10 * 1024 * 1024;

const TIPOS_IMAGEM_PERMITIDOS = new Set([
    "image/jpeg",
    "image/png",
    "image/webp"
]);

function mascararTelefone(telefone) {
    const digitos = String(telefone ?? "").replace(/\D/g, "");
    if (digitos.length < 4) return "não disponível";
    return `${"•".repeat(Math.min(digitos.length - 2, 12))}${digitos.slice(-2)}`;
}

function configurarVerificacaoTelefoneAdmin(perfilInicial) {
    const secao = document.getElementById("seguranca-administrativa");
    const telefoneMascarado = document.getElementById("telefone-seguranca-mascarado");
    const estadoSecao = document.getElementById("estado-seguranca-administrativa");
    const botaoIniciar = document.getElementById("iniciar-verificacao-telefone");
    const dialogo = document.getElementById("dialog-verificacao-telefone");
    const botaoFechar = document.getElementById("fechar-dialog-verificacao-telefone");
    const estadoDialogo = document.getElementById("estado-dialog-verificacao-telefone");
    const formularioSolicitacao = document.getElementById("form-solicitar-codigo-telefone");
    const campoSenha = document.getElementById("senha-verificacao-telefone");
    const botaoEnviar = document.getElementById("enviar-codigo-telefone");
    const formularioConfirmacao = document.getElementById("form-confirmar-codigo-telefone");
    const campoCodigo = document.getElementById("codigo-verificacao-telefone");
    const botaoConfirmar = document.getElementById("confirmar-codigo-telefone");
    const botaoReenviar = document.getElementById("reenviar-codigo-telefone");
    const expiracao = document.getElementById("expiracao-codigo-telefone");

    if (!secao || !dialogo) return () => {};

    let perfilAtual = perfilInicial;
    let challengeId = null;
    let expiraEm = 0;
    let temporizador = null;
    let requisicao = null;
    let versaoRequisicao = 0;
    let enviando = false;
    let modoReenvio = false;
    let expiracaoAnunciada = false;

    function atualizarEstadoPerfil(perfil) {
        if (perfilAtual && perfil !== perfilAtual) Object.assign(perfilAtual, perfil);
        perfilAtual = perfil;
        const ehAdministrador = perfil?.administrador === true;
        secao.hidden = !ehAdministrador;
        if (!ehAdministrador) {
            if (dialogo.open) fecharDialogo();
            return;
        }

        const telefoneDisponivel = Boolean(String(perfil?.telefone ?? "").trim());
        telefoneMascarado.textContent = mascararTelefone(perfil?.telefone);
        const telefoneVerificado = perfil?.telefoneVerificado === true;
        estadoSecao.textContent = telefoneVerificado
            ? "Telefone verificado."
            : telefoneDisponivel
                ? "Telefone ainda não verificado."
                : "Cadastre um telefone no seu perfil para iniciar a verificação.";
        botaoIniciar.hidden = telefoneVerificado;
        botaoIniciar.disabled = !telefoneDisponivel;
    }

    function atualizarContagem() {
        if (!expiracao || !expiraEm) return;
        const restante = Math.max(0, expiraEm - Date.now());
        const minutos = Math.floor(restante / 60000).toString().padStart(2, "0");
        const segundos = Math.floor((restante % 60000) / 1000).toString().padStart(2, "0");
        expiracao.textContent = restante > 0
            ? `O código expira em ${minutos}:${segundos}.`
            : "Código expirado. Solicite outro código para continuar.";
        if (restante === 0) {
            botaoConfirmar.disabled = true;
            if (!expiracaoAnunciada && estadoDialogo) {
                estadoDialogo.textContent = "O prazo do código terminou. Solicite outro código para continuar.";
                expiracaoAnunciada = true;
            }
        }
    }

    function iniciarContagem() {
        window.clearInterval(temporizador);
        expiracaoAnunciada = false;
        expiraEm = Date.now() + 5 * 60 * 1000;
        botaoConfirmar.disabled = false;
        atualizarContagem();
        temporizador = window.setInterval(atualizarContagem, 1000);
    }

    function mostrarSolicitacao(reenvio, focar = true) {
        modoReenvio = reenvio;
        formularioSolicitacao.hidden = false;
        formularioConfirmacao.hidden = true;
        const rotulo = formularioSolicitacao.querySelector("label[for='senha-verificacao-telefone']");
        if (rotulo) rotulo.textContent = reenvio
            ? "Confirme sua senha para solicitar outro código"
            : "Confirme sua senha";
        botaoEnviar.textContent = reenvio ? "Enviar novo código por SMS" : "Enviar código por SMS";
        if (estadoDialogo) estadoDialogo.textContent = reenvio
            ? "Solicitar outro código invalida o desafio anterior. O limite de solicitações é aplicado pelo servidor."
            : "Confirme sua senha para solicitar o código.";
        campoSenha.value = "";
        if (focar) campoSenha.focus();
    }

    function mostrarConfirmacao(mensagem) {
        formularioSolicitacao.hidden = true;
        formularioConfirmacao.hidden = false;
        campoCodigo.value = "";
        if (estadoDialogo) estadoDialogo.textContent = mensagem;
        iniciarContagem();
        campoCodigo.focus();
    }

    function limparDesafioLocal() {
        window.clearInterval(temporizador);
        temporizador = null;
        challengeId = null;
        expiraEm = 0;
        campoCodigo.value = "";
        campoSenha.value = "";
        formularioSolicitacao.reset();
        formularioConfirmacao.reset();
        if (estadoDialogo) estadoDialogo.textContent = "";
        if (expiracao) expiracao.textContent = "";
        mostrarSolicitacao(false, false);
    }

    function fecharDialogo() {
        versaoRequisicao += 1;
        requisicao?.abort();
        requisicao = null;
        enviando = false;
        botaoEnviar.disabled = false;
        botaoConfirmar.disabled = false;
        if (dialogo.open) dialogo.close();
        limparDesafioLocal();
    }

    async function enviarPost(caminho, corpo, signal) {
        const csrf = await obterTokenCSRF();
        return fetch(`${API_URL}${caminho}`, {
            method: "POST",
            credentials: "include",
            cache: "no-store",
            signal,
            headers: {
                Accept: "application/json",
                "Content-Type": "application/json",
                [csrf.headerName]: csrf.token
            },
            body: JSON.stringify(corpo)
        });
    }

    async function sessaoComumContinuaValida() {
        const resposta = await fetchAutenticado("/usuario/perfil", { method: "GET" });
        if (sessaoInvalidaConfirmada(resposta)) {
            window.location.replace("/login");
            return false;
        }
        return resposta.ok;
    }

    async function solicitarCodigo(evento) {
        evento.preventDefault();
        if (enviando || !formularioSolicitacao.reportValidity()) return;

        enviando = true;
        const minhaVersao = ++versaoRequisicao;
        requisicao = new AbortController();
        botaoEnviar.disabled = true;
        if (botaoReenviar) botaoReenviar.disabled = true;
        if (estadoDialogo) estadoDialogo.textContent = "Enviando solicitação…";

        const senha = campoSenha.value;
        campoSenha.value = "";
        // A nova solicitação pode invalidar o desafio anterior mesmo se a resposta
        // se perder; descarte o identificador antigo antes de enviar.
        if (modoReenvio) challengeId = null;

        try {
            const resposta = await enviarPost(
                "/usuario/telefone/verificacao",
                { senha },
                requisicao.signal
            );
            if (minhaVersao !== versaoRequisicao) return;

            if (resposta.status === 202) {
                const resultado = await resposta.json().catch(() => null);
                if (minhaVersao !== versaoRequisicao) return;
                if (typeof resultado?.challengeId !== "string") {
                    throw new Error("A resposta do servidor não foi reconhecida.");
                }
                challengeId = resultado.challengeId;
                mostrarConfirmacao(
                    resultado.mensagem || "Se o cadastro permitir, o código será enviado por SMS."
                );
                return;
            }

            if (resposta.status === 401) {
                const sessaoValida = await sessaoComumContinuaValida();
                if (minhaVersao !== versaoRequisicao) return;
                if (sessaoValida && estadoDialogo) {
                    estadoDialogo.textContent = "Não foi possível iniciar a verificação. Confira sua sessão e tente novamente.";
                }
                return;
            }

            if (estadoDialogo) {
                estadoDialogo.textContent = resposta.status === 429
                    ? "Muitas solicitações. Aguarde antes de tentar novamente."
                    : resposta.status === 403
                        ? "A solicitação de segurança expirou. Feche e abra novamente para tentar."
                        : "Não foi possível solicitar o código. Tente novamente mais tarde.";
            }
        } catch (erro) {
            if (erro.name !== "AbortError" && minhaVersao === versaoRequisicao && estadoDialogo) {
                estadoDialogo.textContent = "Não foi possível conectar ao serviço. Tente novamente.";
            }
        } finally {
            if (minhaVersao === versaoRequisicao) {
                enviando = false;
                requisicao = null;
                botaoEnviar.disabled = false;
                if (botaoReenviar) botaoReenviar.disabled = false;
            }
        }
    }

    async function confirmarCodigo(evento) {
        evento.preventDefault();
        if (enviando || !challengeId || !formularioConfirmacao.reportValidity()) return;
        if (Date.now() >= expiraEm) {
            atualizarContagem();
            return;
        }

        enviando = true;
        const minhaVersao = ++versaoRequisicao;
        requisicao = new AbortController();
        botaoConfirmar.disabled = true;
        botaoReenviar.disabled = true;
        if (estadoDialogo) estadoDialogo.textContent = "Validando código…";
        const codigo = campoCodigo.value;
        campoCodigo.value = "";

        try {
            const resposta = await enviarPost(
                `/usuario/telefone/verificacao/${encodeURIComponent(challengeId)}`,
                { codigo },
                requisicao.signal
            );
            if (minhaVersao !== versaoRequisicao) return;

            if (resposta.status === 204) {
                if (estadoDialogo) estadoDialogo.textContent = "Código confirmado. Atualizando o perfil…";
                const perfilResposta = await fetchAutenticado("/usuario/perfil", { method: "GET" });
                if (minhaVersao !== versaoRequisicao) return;
                if (sessaoInvalidaConfirmada(perfilResposta)) {
                    window.location.replace("/login");
                    return;
                }
                if (!perfilResposta.ok) {
                    throw new Error("Não foi possível atualizar o perfil.");
                }
                const perfilAtualizado = await perfilResposta.json();
                if (minhaVersao !== versaoRequisicao) return;
                atualizarEstadoPerfil(perfilAtualizado);
                window.dispatchEvent(new CustomEvent("perfil:atualizado", { detail: perfilAtualizado }));
                if (perfilAtualizado.telefoneVerificado === true) {
                    if (estadoDialogo) estadoDialogo.textContent = "Telefone verificado.";
                    fecharDialogo();
                } else if (estadoDialogo) {
                    estadoDialogo.textContent = "A confirmação foi recebida, mas o perfil ainda não mostra o telefone verificado. Atualize a página e confira novamente.";
                }
                return;
            }

            if (resposta.status === 401) {
                const sessaoValida = await sessaoComumContinuaValida();
                if (minhaVersao !== versaoRequisicao) return;
                if (!sessaoValida) return;
                if (estadoDialogo) estadoDialogo.textContent = "Código inválido ou expirado. Confira os dígitos ou solicite outro código.";
                campoCodigo.focus();
                return;
            }

            if (estadoDialogo) {
                estadoDialogo.textContent = resposta.status === 429
                    ? "Muitas tentativas. Aguarde antes de tentar novamente."
                    : "Não foi possível confirmar o código. Tente novamente mais tarde.";
            }
        } catch (erro) {
            if (erro.name !== "AbortError" && minhaVersao === versaoRequisicao && estadoDialogo) {
                estadoDialogo.textContent = "Não foi possível conectar ao serviço. O código não será reenviado automaticamente.";
            }
        } finally {
            if (minhaVersao === versaoRequisicao) {
                enviando = false;
                requisicao = null;
                botaoConfirmar.disabled = Date.now() >= expiraEm;
                botaoReenviar.disabled = false;
            }
        }
    }

    botaoIniciar?.addEventListener("click", () => {
        limparDesafioLocal();
        if (typeof dialogo.showModal === "function") dialogo.showModal();
        else dialogo.setAttribute("open", "");
        campoSenha.focus();
    });
    botaoFechar?.addEventListener("click", fecharDialogo);
    dialogo.addEventListener("cancel", evento => {
        evento.preventDefault();
        fecharDialogo();
    });
    dialogo.addEventListener("click", evento => {
        if (evento.target === dialogo) fecharDialogo();
    });
    formularioSolicitacao?.addEventListener("submit", solicitarCodigo);
    formularioConfirmacao?.addEventListener("submit", confirmarCodigo);
    botaoReenviar?.addEventListener("click", () => mostrarSolicitacao(true));

    atualizarEstadoPerfil(perfilAtual);
    return atualizarEstadoPerfil;
}

/* =========================================================
   PREENCHER DADOS DO PERFIL
   ========================================================= */

function preencherPerfil(usuario) {
    const campos = {
        nomeSocial: document.getElementById("nome-social"),
        email: document.getElementById("email"),
        telefone: document.getElementById("telefone"),
        cep: document.getElementById("cep"),
        dataNascimento: document.getElementById("data-nascimento")
    };

    if (campos.nomeSocial) {
        campos.nomeSocial.value = usuario.nomeSocial ?? "";
    }

    if (campos.email) {
        campos.email.value = usuario.email ?? "";
    }

    if (campos.telefone) {
        campos.telefone.value = usuario.telefone ?? "";
    }

    if (campos.cep) {
        campos.cep.value = usuario.cep ?? "";
    }

    if (campos.dataNascimento) {
        if (usuario.dataNascimento) {
            const partes = usuario.dataNascimento.split("-");

            campos.dataNascimento.value =
                partes.length === 3
                    ? `${partes[2]}/${partes[1]}/${partes[0]}`
                    : "";
        } else {
            campos.dataNascimento.value = "";
        }
    }

    const nomeCivil = usuario.nome?.trim() || "Usuário";
    const nomeSocial = usuario.nomeSocial?.trim() || "";

    function abreviarNome(nome, estilo = "primeiro-ultimo") {
        const conectores = new Set([
            "de", "da", "do", "das", "dos", "e"
        ]);

        const partes = nome
            .trim()
            .split(/\s+/)
            .filter(Boolean)
            .filter((parte, indice, todas) =>
                indice === 0 ||
                indice === todas.length - 1 ||
                !conectores.has(parte.toLocaleLowerCase("pt-BR"))
            );

        if (partes.length <= 2) {
            return partes.join(" ");
        }

        if (estilo === "iniciais") {
            return partes
                .slice(0, -1)
                .map(parte => `${parte[0].toLocaleUpperCase("pt-BR")}.`)
                .concat(partes.at(-1))
                .join(" ");
        }

        return [
            partes[0],
            ...partes.slice(1, -1).map(parte =>
                `${parte[0].toLocaleUpperCase("pt-BR")}.`
            ),
            partes.at(-1)
        ].join(" ");
    }

    const nomeExibido = abreviarNome(nomeSocial || nomeCivil);

    const titulo = document.getElementById("perfil-nome");

    if (titulo) {
        titulo.textContent = nomeExibido;
    }



    const campoNomeSocial =
        document.getElementById("perfil-nome-civil");

    const labelNomeSocial =
        document.getElementById("label-nome-civil");

    if (campoNomeSocial) {
        // Exibe o nome civil por padrão.
        campoNomeSocial.value = nomeCivil;

        // O nome civil não pode ser alterado.
        campoNomeSocial.disabled = true;

        // Só exibe o campo quando existir nome social cadastrado.
        campoNomeSocial.hidden = !nomeSocial;
    }

    if (labelNomeSocial) {
        labelNomeSocial.hidden = !nomeSocial;
        labelNomeSocial.textContent = "Nome";
    }



    const iniciais = nomeExibido
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map(parte => parte[0].toLocaleUpperCase("pt-BR"))
        .join("");

    atualizarExibicaoFoto(usuario.fotoPerfilUrl, iniciais);

    const dataCadastro =
        document.getElementById("perfil-data-cadastro");

    if (dataCadastro) {
        const data = usuario.dataCriacao
            ? new Date(usuario.dataCriacao)
            : null;

        dataCadastro.textContent =
            data && !Number.isNaN(data.getTime())
                ? `Membro desde ${data.toLocaleDateString("pt-BR")}`
                : "Data de cadastro indisponível";
    }

    window.dispatchEvent(
        new CustomEvent("perfil:atualizado", { detail: usuario })
    );
}

/* =========================================================
EXIBIR FOTO OU INICIAIS
   ========================================================= */

function atualizarExibicaoFoto(url, iniciais = "?") {
    const foto = document.getElementById("foto-perfil");

    if (!foto) {
        return;
    }

    foto.replaceChildren();

    if (!url) {
        foto.textContent = iniciais || "?";
        return;
    }

    const imagem = document.createElement("img");

    imagem.src = url;
    imagem.alt = "Foto de perfil";
    imagem.className = "imagem-foto-perfil";

    imagem.addEventListener("error", () => {
        foto.replaceChildren();
        foto.textContent = iniciais || "?";
    }, { once: true });

    foto.appendChild(imagem);
}

/* =========================================================
   UPLOAD DA FOTO DE PERFIL
   ========================================================= */

function configurarUploadFoto() {
    const inputFoto = document.getElementById("input-foto-perfil");
    const estadoFoto = document.getElementById("estado-foto-perfil");

    if (!inputFoto) {
        return;
    }

    inputFoto.addEventListener("change", async () => {
        const arquivo = inputFoto.files?.[0];

        if (!arquivo) {
            return;
        }

        if (!TIPOS_IMAGEM_PERMITIDOS.has(arquivo.type)) {
            if (estadoFoto) {
                estadoFoto.textContent =
                    "Formato inválido. Selecione JPEG, PNG ou WebP.";
            }

            inputFoto.value = "";
            return;
        }

        if (arquivo.size > TAMANHO_MAXIMO_FOTO) {
            if (estadoFoto) {
                estadoFoto.textContent =
                    "A imagem não pode ultrapassar 10 MB.";
            }

            inputFoto.value = "";
            return;
        }

        if (estadoFoto) {
            estadoFoto.textContent = "Enviando foto...";
        }

        inputFoto.disabled = true;

        try {
            const formulario = new FormData();
            formulario.append("foto", arquivo);

            const resposta = await fetchAutenticado(
                "/usuario/foto-perfil",
                {
                    method: "POST",
                    body: formulario
                }
            );

            if (sessaoInvalidaConfirmada(resposta)) {
                window.location.replace("/login");
                return;
            }

            const resultado = await resposta.json().catch(() => ({}));

            if (!resposta.ok) {
                throw new Error(
                    resultado.mensagem ||
                    `Falha no upload (HTTP ${resposta.status}).`
                );
            }

            if (!resultado.fotoPerfilUrl) {
                throw new Error(
                    "O servidor não retornou a URL da foto."
                );
            }

            const titulo = document.getElementById("perfil-nome");
            const nome = titulo?.textContent?.trim() || "Usuário";

            const iniciais = nome
                .split(/\s+/)
                .filter(Boolean)
                .slice(0, 2)
                .map(parte => parte[0].toLocaleUpperCase("pt-BR"))
                .join("");

            atualizarExibicaoFoto(resultado.fotoPerfilUrl, iniciais);

            window.dispatchEvent(
                new CustomEvent("perfil:atualizado", {
                    detail: { fotoPerfilUrl: resultado.fotoPerfilUrl }
                })
            );

            if (estadoFoto) {
                estadoFoto.textContent =
                    "Foto de perfil atualizada com sucesso.";
            }
        } catch (erro) {
            console.error("Erro ao enviar foto de perfil:", erro);

            if (estadoFoto) {
                estadoFoto.textContent =
                    erro.message ||
                    "Não foi possível enviar a foto. Tente novamente.";
            }
        } finally {
            inputFoto.disabled = false;
            inputFoto.value = "";
        }
    });
}

/* =========================================================
   INICIALIZAÇÃO DA PÁGINA
   ========================================================= */

document.addEventListener("DOMContentLoaded", async () => {
    const estado = document.getElementById("estado-autenticacao");
    const conteudo = document.getElementById("conteudo-protegido");

    function redirecionarParaLogin() {
        window.location.replace("/login");
    }

    try {
        /* =============================================
           1. BUSCAR PERFIL E VALIDAR SESSÃO
           ============================================= */

        const respostaPerfil = await fetchAutenticado(
            "/usuario/perfil",
            {
                method: "GET"
            }
        );

        if (sessaoInvalidaConfirmada(respostaPerfil)) {
            redirecionarParaLogin();
            return;
        }

        if (!respostaPerfil.ok) {
            throw new Error(
                `Erro ao carregar perfil: HTTP ${respostaPerfil.status}`
            );
        }

        const usuario = await respostaPerfil.json();

        preencherPerfil(usuario);
        const atualizarSegurancaAdministrativa =
            configurarVerificacaoTelefoneAdmin(usuario);
        configurarUploadFoto();

        // excluir conta


        const botaoExcluirConta = document.getElementById("excluir-conta");
        const estadoExclusaoConta = document.getElementById("estado-exclusao-conta");

        const modalExclusao = document.getElementById("modal-exclusao-conta");
        const botaoFecharModal = document.getElementById("fechar-modal-exclusao");
        const botaoCancelarModal = document.getElementById("cancelar-modal-exclusao");
        const botaoConfirmarModal = document.getElementById("confirmar-modal-exclusao");

        let focoAnteriorExclusao = null;

        function abrirModalExclusao() {
            if (!modalExclusao) return;

            focoAnteriorExclusao = document.activeElement;
            modalExclusao.hidden = false;
            document.body.style.overflow = "hidden";

            botaoCancelarModal?.focus();
        }

        function fecharModalExclusao() {
            if (!modalExclusao || modalExclusao.hidden) return;

            modalExclusao.hidden = true;
            document.body.style.overflow = "";

            if (focoAnteriorExclusao instanceof HTMLElement) {
                focoAnteriorExclusao.focus();
            }
        }

        botaoExcluirConta?.addEventListener("click", () => {
            abrirModalExclusao();
        });

        botaoFecharModal?.addEventListener("click", fecharModalExclusao);
        botaoCancelarModal?.addEventListener("click", fecharModalExclusao);

        // Fecha ao clicar no fundo, sem confirmar a solicitação.
        modalExclusao?.addEventListener("click", (evento) => {
            if (evento.target === modalExclusao) {
                fecharModalExclusao();
            }
        });

        // Escape fecha o modal, mas nunca confirma a exclusão.
        document.addEventListener("keydown", (evento) => {
            if (evento.key === "Escape" && modalExclusao && !modalExclusao.hidden) {
                fecharModalExclusao();
            }
        });

        botaoConfirmarModal?.addEventListener("click", async () => {
            if (!botaoExcluirConta || botaoConfirmarModal.disabled) return;

            botaoConfirmarModal.disabled = true;
            botaoCancelarModal.disabled = true;
            botaoFecharModal.disabled = true;

            botaoConfirmarModal.textContent = "Processando...";

            if (estadoExclusaoConta) {
                estadoExclusaoConta.hidden = false;
                estadoExclusaoConta.textContent = "Registrando solicitação...";
            }

            try {
                const resposta = await fetchAutenticado("/usuario/delete", {
                    method: "DELETE"
                });

                if (sessaoInvalidaConfirmada(resposta)) {
                    fecharModalExclusao();
                    redirecionarParaLogin();
                    return;
                }

                const resultado = await resposta.json().catch(() => ({}));

                if (!resposta.ok) {
                    throw new Error(
                        resultado.mensagem ||
                        `Não foi possível solicitar a exclusão (HTTP ${resposta.status}).`
                    );
                }

                const dataLocalAgendada =
                    resultado.dataExclusaoAgendada?.split("T")[0];

                const partesData = dataLocalAgendada?.split("-");

                const dataFormatada = partesData?.length === 3
                    ? `${partesData[2]}/${partesData[1]}/${partesData[0]}`
                    : "daqui a seis meses";

                fecharModalExclusao();
                marcarSessaoAnonima();

                if (estadoExclusaoConta) {
                    estadoExclusaoConta.textContent =
                        `Conta desativada. A exclusão está agendada para ${dataFormatada}. ` +
                        "Você pode cancelar pelo acesso de recuperação na página de login.";
                }

                window.setTimeout(() => {
                    window.location.replace("/login?exclusao=solicitada");
                }, 2500);

            } catch (erro) {
                console.error("Erro ao solicitar exclusão da conta:", erro);

                if (estadoExclusaoConta) {
                    estadoExclusaoConta.hidden = false;
                    estadoExclusaoConta.textContent =
                        erro.message || "Não foi possível solicitar a exclusão da conta.";
                }
            } finally {
                botaoConfirmarModal.disabled = false;
                botaoCancelarModal.disabled = false;
                botaoFecharModal.disabled = false;
                botaoConfirmarModal.textContent = "Solicitar exclusão";
            }
        });


        /* =============================================
           2. MOSTRAR CONTEÚDO
           ============================================= */

        if (estado) {
            estado.hidden = true;
        }

        if (conteudo) {
            conteudo.hidden = false;
        }


        /* =============================================
           3. EDITAR CAMPOS DO PERFIL
           ============================================= */

        const campoNomeSocial = document.getElementById("perfil-nome-civil");
        const labelNomeSocial = document.getElementById("label-nome-civil");

        const campoTelefone = document.getElementById("telefone");
        const campoCep = document.getElementById("cep");

        const campoPerfil = document.querySelector(".campo-perfil");
        const acoesEdicao = document.querySelectorAll(".acoes-edicao-perfil");
        const estadoEdicao = document.getElementById("estado-edicao-perfil");

        const botaoEditar = document.getElementById("alterar-dados-perfil");
        const botaoSalvar = document.getElementById("salvar-dados-perfil");
        const botaoCancelar = document.getElementById("cancelar-edicao-perfil");

        let editandoPerfil = false;
        let valoresOriginais = {};

        function mostrarEstadoEdicao(mensagem) {
            if (!estadoEdicao) return;

            estadoEdicao.textContent = mensagem;
            estadoEdicao.hidden = !mensagem;
        }

        function iniciarEdicaoPerfil() {
            if (editandoPerfil) return;

            // Guardar os valores atuais para permitir cancelar.
            valoresOriginais = {
                nomeSocial: campoNomeSocial?.value ?? "",
                telefone: campoTelefone?.value ?? "",
                cep: campoCep?.value ?? ""
            };

            // Trocar o nome civil pelo nome social no campo de edição.
            if (campoNomeSocial) {
                campoNomeSocial.value = usuario.nomeSocial ?? "";
                campoNomeSocial.hidden = false;
                campoNomeSocial.disabled = false;
                campoNomeSocial.placeholder = "Digite seu nome social";
            }

            if (labelNomeSocial) {
                labelNomeSocial.hidden = false;
                labelNomeSocial.textContent = "Nome social";
            }

            // Habilitar apenas os campos que podem ser editados.
            if (campoTelefone) campoTelefone.disabled = false;
            if (campoCep) campoCep.disabled = false;

            // Permanecem bloqueados.
            const campoEmail = document.getElementById("email");
            const campoDataNascimento = document.getElementById("data-nascimento");

            if (campoEmail) campoEmail.disabled = true;
            if (campoDataNascimento) campoDataNascimento.disabled = true;

            editandoPerfil = true;

            if (campoPerfil) {
                campoPerfil.classList.add("editando");
            }

            acoesEdicao.forEach(grupo => {
                grupo.hidden = false;
            });

            if (botaoEditar) {
                botaoEditar.hidden = true;
            }

            mostrarEstadoEdicao("");
        }

        function cancelarEdicaoPerfil() {
            if (!editandoPerfil) return;

            // Restaurar os valores anteriores.
            if (campoNomeSocial) {
                campoNomeSocial.value = valoresOriginais.nomeSocial;
            }

            if (campoTelefone) {
                campoTelefone.value = valoresOriginais.telefone;
            }

            if (campoCep) {
                campoCep.value = valoresOriginais.cep;
            }

            // Restaurar a apresentação normal do perfil.
            if (campoNomeSocial) {
                campoNomeSocial.disabled = true;
                campoNomeSocial.hidden = !(usuario.nomeSocial?.trim());
                campoNomeSocial.placeholder = "";
            }

            if (labelNomeSocial) {
                labelNomeSocial.hidden = !(usuario.nomeSocial?.trim());
                labelNomeSocial.textContent = "Nome";
            }

            if (campoTelefone) campoTelefone.disabled = true;
            if (campoCep) campoCep.disabled = true;

            if (campoPerfil) {
                campoPerfil.classList.remove("editando");
            }

            acoesEdicao.forEach(grupo => {
                grupo.hidden = true;
            });

            if (botaoEditar) {
                botaoEditar.hidden = false;
            }

            editandoPerfil = false;
        }


        async function salvarEdicaoPerfil() {
            if (!editandoPerfil) return;

            const campoNomeSocial = document.getElementById("perfil-nome-civil");
            const campoTelefone = document.getElementById("telefone");
            const campoCep = document.getElementById("cep");

            const dados = {
                nomeSocial: campoNomeSocial.value.trim(),
                telefone: campoTelefone.value.trim(),
                cep: campoCep.value.trim()
            };

            if (botaoSalvar) {
                botaoSalvar.disabled = true;
            }

            mostrarEstadoEdicao("Salvando alterações...");

            try {
                const resposta = await fetchAutenticado("/usuario/perfil", {
                    method: "PATCH",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify(dados)
                });

                if (await sessaoInvalidaConfirmada(resposta)) {
                    return;
                }

                const resultado = await resposta.json().catch(() => null);

                if (!resposta.ok) {
                    throw new Error(
                        resultado?.mensagem ||
                        resultado?.message ||
                        "Não foi possível salvar as alterações."
                    );
                }

                // Atualiza os dados locais com a resposta do backend.
                usuario.nomeSocial = resultado.nomeSocial ?? null;
                usuario.telefone = resultado.telefone ?? null;
                usuario.cep = resultado.cep ?? null;
                usuario.administrador = resultado.administrador === true;
                usuario.telefoneVerificado = resultado.telefoneVerificado === true;

                // Atualiza os campos e o nome exibido no perfil.
                campoNomeSocial.value = usuario.nomeSocial ?? "";
                campoTelefone.value = usuario.telefone ?? "";
                campoCep.value = usuario.cep ?? "";

                preencherPerfil(usuario);
                atualizarSegurancaAdministrativa(resultado);

                cancelarEdicaoPerfil();
                mostrarEstadoEdicao("Alterações salvas com sucesso.");

            } catch (erro) {
                console.error("Erro ao salvar perfil:", erro);
                mostrarEstadoEdicao(
                    erro.message || "Erro ao salvar as alterações."
                );
            } finally {
                if (botaoSalvar) {
                    botaoSalvar.disabled = false;
                }
            }
        }

        if (botaoSalvar) {
            botaoSalvar.addEventListener("click", salvarEdicaoPerfil);
        }


        if (botaoEditar) {
            botaoEditar.addEventListener("click", iniciarEdicaoPerfil);
        }

        if (botaoCancelar) {
            botaoCancelar.addEventListener("click", cancelarEdicaoPerfil);
        }



        /* =============================================
           6. ACESSIBILIDADE — NÍVEIS, TEMAS, ONGs
           ============================================= */

        const TAMANHOS_LETRA = {
            pequeno: 90,
            medio: 100,
            grande: 120,
            "extra-grande": 140
        };

        const MODOS_EXIBICAO = ["escuro", "claro", "alto-contraste"];

        function lerEscolha(nome) {
            return document.querySelector(`input[name="${nome}"]:checked`)?.value;
        }

        function marcarEscolha(nome, valor) {
            const campo = document.querySelector(
                `input[name="${nome}"][value="${valor}"]`
            );

            if (campo) campo.checked = true;
        }

        const libras = document.getElementById("acss-libras");
        const chipsOng = document.querySelectorAll('input[name="acss-tipos-ong"]');
        const contadorOng = document.getElementById("acss-contador-ong");
        const botaoLimparOng = document.getElementById("acss-limpar-ong");

        function atualizarContadorOng() {
            if (!contadorOng) return;

            const total = Array.from(chipsOng).filter(chip => chip.checked).length;

            contadorOng.textContent =
                total === 0 ? "Nenhum tipo selecionado"
                    : total === 1 ? "1 tipo selecionado"
                        : `${total} tipos selecionados`;
        }

        // Tamanho da letra (prévia ao vivo)
        document
            .querySelectorAll('input[name="acss-tamanho-letra"]')
            .forEach(opcao => {
                opcao.addEventListener("change", () => {
                    document.body.classList.remove(
                        "texto-pequeno",
                        "texto-medio",
                        "texto-grande",
                        "texto-extra-grande"
                    );

                    document.body.classList.add(`texto-${opcao.value}`);
                });
            });

        // Modo de exibição (prévia ao vivo)
        document
            .querySelectorAll('input[name="acss-modo-exibicao"]')
            .forEach(opcao => {
                opcao.addEventListener("change", () => {
                    document.body.classList.remove(
                        "modo-escuro",
                        "modo-claro",
                        "alto-contraste"
                    );

                    document.body.classList.add(
                        opcao.value === "alto-contraste"
                            ? "alto-contraste"
                            : `modo-${opcao.value}`
                    );
                });
            });

        // VLibras
        libras?.addEventListener("change", () => {
            document.body.classList.toggle("libras-ativo", libras.checked);
        });

        // Checklist de ONGs
        chipsOng.forEach(chip => {
            chip.addEventListener("change", atualizarContadorOng);
        });

        botaoLimparOng?.addEventListener("click", () => {
            chipsOng.forEach(chip => {
                chip.checked = false;
            });

            atualizarContadorOng();
        });

        // Restaura as escolhas salvas neste navegador
        try {
            const salvas = JSON.parse(
                localStorage.getItem("portoAjudaPreferencias") ?? "null"
            );

            if (salvas) {
                const nomeTamanho = Object.keys(TAMANHOS_LETRA).find(
                    chave => TAMANHOS_LETRA[chave] === salvas.tamanhoFonte
                );

                if (nomeTamanho) {
                    marcarEscolha("acss-tamanho-letra", nomeTamanho);
                }

                marcarEscolha(
                    "acss-modo-exibicao",
                    salvas.altoContraste ? "alto-contraste" : salvas.tema
                );

                if (libras) libras.checked = Boolean(salvas.libras);

                (salvas.tiposOng ?? []).forEach(tipo => {
                    marcarEscolha("acss-tipos-ong", tipo);
                });
            }
        } catch {
            /* preferências locais inválidas: ignora */
        }

        atualizarContadorOng();

        /* =============================================
           SALVAR PREFERÊNCIAS DE ACESSIBILIDADE
           ============================================= */

        const botaoSalvarAcss = document.getElementById("salvar-preferencias-acss");
        const estadoPreferenciasAcss = document.getElementById("estado-preferencias-acss");

        botaoSalvarAcss?.addEventListener("click", async () => {
            const tamanhoFonte = TAMANHOS_LETRA[lerEscolha("acss-tamanho-letra")];
            const modo = lerEscolha("acss-modo-exibicao");

            if (tamanhoFonte === undefined || !MODOS_EXIBICAO.includes(modo)) {
                estadoPreferenciasAcss.textContent =
                    "Verifique as opções de acessibilidade.";
                estadoPreferenciasAcss.hidden = false;
                return;
            }

            const dados = {
                tamanhoFonte,
                tema: modo === "alto-contraste" ? "escuro" : modo,
                altoContraste: modo === "alto-contraste",
                libras: libras?.checked ?? false,
                tiposOng: Array.from(chipsOng)
                    .filter(chip => chip.checked)
                    .map(chip => chip.value)
            };

            botaoSalvarAcss.disabled = true;
            estadoPreferenciasAcss.textContent = "Salvando preferências...";
            estadoPreferenciasAcss.hidden = false;

            try {
                const resposta = await fetchAutenticado(
                    "/usuario/preferencias",
                    {
                        method: "PUT",
                        headers: {
                            "Content-Type": "application/json"
                        },
                        body: JSON.stringify(dados)
                    }
                );

                if (await sessaoInvalidaConfirmada(resposta)) {
                    return;
                }

                const resultado = await resposta.json().catch(() => null);

                if (!resposta.ok) {
                    throw new Error(
                        resultado?.mensagem ||
                        resultado?.message ||
                        `Não foi possível salvar as preferências (HTTP ${resposta.status}).`
                    );
                }

                // Atualiza o cache local utilizado pelo site.
                localStorage.setItem(
                    "portoAjudaPreferencias",
                    JSON.stringify(dados)
                );

                estadoPreferenciasAcss.textContent =
                    "Preferências de acessibilidade salvas com sucesso.";

            } catch (erro) {
                console.error("Erro ao salvar preferências:", erro);

                estadoPreferenciasAcss.textContent =
                    erro.message || "Não foi possível salvar as preferências.";
            } finally {
                botaoSalvarAcss.disabled = false;
            }
        });

        /* =============================================
   11. CRIAR E TROCAR CONTA DE OSC
   ============================================= */

        // Criar conta de OSC
        const botaoCriarOng = document.getElementById("criar-osc");

        botaoCriarOng?.addEventListener("click", () => {
            window.location.href = ROTAS.cadastroOng;
        });

        // Trocar conta de OSC
        const botaoTrocarOng = document.getElementById("trocar-osc");

        const modalTrocarOng = document.getElementById("modal-trocar-osc");
        const listaOngsUsuario = document.getElementById("lista-oscs-usuario");
        const estadoTrocarOng = document.getElementById("estado-trocar-osc");
        const botaoFecharTrocarOng = document.getElementById("fechar-modal-trocar-osc");

        let focoAnteriorTrocaOng = null;

        function fecharModalTrocarOng() {
            if (!modalTrocarOng || modalTrocarOng.hidden) return;

            modalTrocarOng.hidden = true;
            document.body.style.overflow = "";

            if (focoAnteriorTrocaOng instanceof HTMLElement) {
                focoAnteriorTrocaOng.focus();
            }
        }

        function abrirModalTrocarOng() {
            if (!modalTrocarOng) return;

            focoAnteriorTrocaOng = document.activeElement;

            modalTrocarOng.hidden = false;
            document.body.style.overflow = "hidden";

            botaoFecharTrocarOng?.focus();
        }

        function acessarPerfilOng(osc) {
            if (!osc?.id) {
                throw new Error("Não foi possível identificar esta organização.");
            }

            window.location.href =
                `${ROTAS.perfilOng}?id=${encodeURIComponent(osc.id)}`;
        }

        function criarItemOng(osc) {
            const item = document.createElement("button");

            item.type = "button";
            item.className = "item-selecao-osc";

            const logo = document.createElement("img");

            logo.className = "item-selecao-osc-logo";
            logo.alt = "";

            if (osc.logo) {
                logo.src = osc.logo;
            } else {
                logo.src = "../assets/logo-night.png";
            }

            const informacoes = document.createElement("span");
            informacoes.className = "item-selecao-osc-informacoes";

            const nome = document.createElement("strong");
            nome.textContent = osc.nome || "Organização sem nome";

            const cargo = document.createElement("small");
            cargo.textContent = osc.cargo || "Membro";

            informacoes.append(nome, cargo);
            if (osc.status === "INATIVA") {
                const status = document.createElement("small");
                status.className = "status-ong-inativa";
                status.textContent = osc.arquivada
                    ? "Organização arquivada"
                    : "Organização inativa";
                informacoes.append(status);
            } else if (osc.arquivada) {
                const status = document.createElement("small");
                status.className = "status-ong-inativa";
                status.textContent = "Organização arquivada";
                informacoes.append(status);
            }
            item.append(logo, informacoes);

            item.addEventListener("click", () => {
                acessarPerfilOng(osc);
            });

            return item;
        }

        async function carregarMinhasOngs() {
            if (!botaoTrocarOng || botaoTrocarOng.disabled) return;

            botaoTrocarOng.disabled = true;
            botaoTrocarOng.textContent = "Carregando...";

            try {
                const resposta = await fetchAutenticado("/osc/minhas", {
                    method: "GET"
                });

                if (await sessaoInvalidaConfirmada(resposta)) {
                    window.location.replace("/login");
                    return;
                }

                const resultado = await resposta.json().catch(() => null);

                if (!resposta.ok) {
                    throw new Error(
                        resultado?.mensagem ||
                        resultado?.message ||
                        `Não foi possível carregar suas organizações (HTTP ${resposta.status}).`
                    );
                }

                if (!Array.isArray(resultado)) {
                    throw new Error("O servidor retornou uma resposta inválida.");
                }

                if (resultado.length === 0) {
                    abrirModalTrocarOng();

                    estadoTrocarOng.textContent =
                        "Você ainda não está vinculado a nenhuma organização.";

                    return;
                }

                // Se houver apenas uma OSC, acessa diretamente.
                if (resultado.length === 1) {
                    acessarPerfilOng(resultado[0]);
                    return;
                }

                // Se houver várias, apresenta a seleção.
                listaOngsUsuario.replaceChildren();

                resultado.forEach(osc => {
                    listaOngsUsuario.appendChild(criarItemOng(osc));
                });

                estadoTrocarOng.textContent = "";

                abrirModalTrocarOng();

            } catch (erro) {
                console.error("Erro ao carregar organizações:", erro);

                abrirModalTrocarOng();

                estadoTrocarOng.textContent =
                    erro.message || "Não foi possível carregar suas organizações.";

            } finally {
                botaoTrocarOng.disabled = false;
                botaoTrocarOng.textContent = "Trocar conta";
            }
        }

        botaoTrocarOng?.addEventListener("click", carregarMinhasOngs);

        botaoFecharTrocarOng?.addEventListener(
            "click",
            fecharModalTrocarOng
        );

        modalTrocarOng?.addEventListener("click", evento => {
            if (evento.target === modalTrocarOng) {
                fecharModalTrocarOng();
            }
        });

        document.addEventListener("keydown", evento => {
            if (
                evento.key === "Escape" &&
                modalTrocarOng &&
                !modalTrocarOng.hidden
            ) {
                fecharModalTrocarOng();
            }
        });

    } catch (erro) {
        console.error("Erro ao carregar o perfil:", erro);

        if (conteudo) {
            conteudo.hidden = true;
        }

        if (estado) {
            estado.hidden = false;
            estado.replaceChildren();

            const mensagem = document.createElement("p");

            mensagem.textContent =
                "Não foi possível carregar seu perfil. Tente novamente.";

            const tentarNovamente = document.createElement("button");
            tentarNovamente.type = "button";
            tentarNovamente.textContent = "Tentar novamente";

            tentarNovamente.addEventListener("click", () => {
                window.location.reload();
            });

            estado.append(mensagem, tentarNovamente);
        }
    }
});

document.getElementById("criar-stripe").addEventListener("click", () => {
    const botao = document.getElementById("criar-stripe");
    botao.disabled = true;
    botao.textContent = "Aguarde...";
    window.location.assign(`${API_URL}/stripe/account-link`);
});

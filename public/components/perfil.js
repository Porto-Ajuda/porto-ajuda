import { fetchAutenticado } from "./api.js";

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
    const nomeExibido = nomeSocial || nomeCivil;

    const titulo = document.getElementById("perfil-nome");

    if (titulo) {
        titulo.textContent = nomeExibido;
    }

    const elementoNomeCivil =
        document.getElementById("perfil-nome-civil");

    if (elementoNomeCivil) {
        elementoNomeCivil.hidden = !nomeSocial;
        elementoNomeCivil.textContent = nomeSocial
            ? `Nome: ${nomeCivil}`
            : "";
    }

    const iniciais = nomeExibido
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map(parte => parte[0].toLocaleUpperCase("pt-BR"))
        .join("");

    const foto = document.getElementById("foto-perfil");

    if (foto) {
        foto.textContent = iniciais || "?";
    }

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
           1. CONFIRMAR SESSÃO
           ============================================= */

        const respostaSessao = await fetchAutenticado(
            "/usuario/sessao-atual",
            {
                method: "GET"
            }
        );

        if (
            respostaSessao.status === 401 ||
            respostaSessao.status === 403
        ) {
            redirecionarParaLogin();
            return;
        }

        if (!respostaSessao.ok) {
            throw new Error(
                `Falha ao verificar sessão: HTTP ${respostaSessao.status}`
            );
        }

        const sessao = await respostaSessao.json();

        if (sessao.autenticado !== true) {
            redirecionarParaLogin();
            return;
        }

        /* =============================================
           2. BUSCAR DADOS DO PERFIL
           ============================================= */

        const respostaPerfil = await fetchAutenticado(
            "/usuario/perfil",
            {
                method: "GET"
            }
        );

        if (
            respostaPerfil.status === 401 ||
            respostaPerfil.status === 403
        ) {
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

        /* =============================================
           3. MOSTRAR CONTEÚDO
           ============================================= */

        if (estado) {
            estado.hidden = true;
        }

        if (conteudo) {
            conteudo.hidden = false;
        }

        /* =============================================
           4. EDITAR CAMPOS DO PERFIL
           ============================================= */

        const btn = document.querySelector(".painel-perfil .btn");
        const inputs = document.querySelectorAll(
            ".campo-perfil input"
        );
        const campoPerfil = document.querySelector(".campo-perfil");

        let editando = false;

        inputs.forEach(input => {
            input.disabled = true;
        });

        if (btn && campoPerfil) {
            campoPerfil.classList.remove("editando");

            btn.addEventListener("click", () => {
                editando = !editando;

                inputs.forEach(input => {
                    input.disabled = !editando;
                });

                campoPerfil.classList.toggle("editando", editando);

                btn.textContent = editando
                    ? "Salvar Dados"
                    : "Alterar Dados";

                if (!editando) {
                    console.info(
                        "A edição foi encerrada, mas o salvamento na API ainda não foi implementado."
                    );
                }
            });
        }

        /* =============================================
           5. ABAS
           ============================================= */

        const abas = document.querySelectorAll(".aba");
        const paineis = document.querySelectorAll(".painel-aba");

        abas.forEach(aba => {
            aba.addEventListener("click", () => {
                abas.forEach(item => {
                    item.setAttribute("aria-selected", "false");
                });

                paineis.forEach(painel => {
                    painel.hidden = true;
                });

                aba.setAttribute("aria-selected", "true");

                const idPainel = aba.getAttribute("aria-controls");
                const painelAtivo = document.getElementById(idPainel);

                if (painelAtivo) {
                    painelAtivo.hidden = false;
                    painelAtivo.focus();
                }
            });
        });

        /* =============================================
           6. FILTROS DO REGISTRO
           ============================================= */

        const filtros = document.querySelectorAll(".filtro");
        const registros = document.querySelectorAll(".registro");
        const registroVazio = document.getElementById("registro-vazio");

        filtros.forEach(filtro => {
            filtro.addEventListener("click", () => {
                const tipoFiltro = filtro.dataset.filtro;

                filtros.forEach(item => {
                    item.setAttribute("aria-pressed", "false");
                });

                filtro.setAttribute("aria-pressed", "true");

                let quantidadeVisivel = 0;

                registros.forEach(registro => {
                    const mostrar =
                        tipoFiltro === "tudo" ||
                        registro.dataset.tipo === tipoFiltro;

                    registro.hidden = !mostrar;

                    if (mostrar) {
                        quantidadeVisivel++;
                    }
                });

                if (registroVazio) {
                    registroVazio.hidden = quantidadeVisivel !== 0;
                }
            });
        });

        /* =============================================
           7. ACESSIBILIDADE — TAMANHO DA LETRA
           ============================================= */

        const tamanhoLetra = document.getElementById(
            "acss-tamanho-letra"
        );

        if (tamanhoLetra) {
            tamanhoLetra.addEventListener("change", () => {
                document.body.classList.remove(
                    "texto-pequeno",
                    "texto-medio",
                    "texto-grande",
                    "texto-extra-grande"
                );

                document.body.classList.add(
                    `texto-${tamanhoLetra.value}`
                );
            });
        }

        /* =============================================
           8. ACESSIBILIDADE — MODO DE EXIBIÇÃO
           ============================================= */

        const modoExibicao = document.getElementById(
            "acss-modo-exibicao"
        );

        if (modoExibicao) {
            modoExibicao.addEventListener("change", () => {
                document.body.classList.remove(
                    "modo-escuro",
                    "modo-claro",
                    "alto-contraste"
                );

                if (modoExibicao.value === "alto-contraste") {
                    document.body.classList.add("alto-contraste");
                } else {
                    document.body.classList.add(
                        `modo-${modoExibicao.value}`
                    );
                }
            });
        }

        /* =============================================
           9. ACESSIBILIDADE — IDIOMA
           ============================================= */

        const idioma = document.getElementById("acss-idioma");

        if (idioma) {
            idioma.addEventListener("change", () => {
                document.documentElement.lang = idioma.value;
            });
        }

        /* =============================================
           10. ACESSIBILIDADE — LEITURA DE TELA
           ============================================= */

        const leituraTela = document.getElementById(
            "acss-leitura-tela"
        );

        if (leituraTela) {
            leituraTela.addEventListener("change", () => {
                document.body.classList.toggle(
                    "leitura-ativa",
                    leituraTela.checked
                );
            });
        }

        /* =============================================
           11. ACESSIBILIDADE — LIBRAS
           ============================================= */

        const libras = document.getElementById("acss-libras");

        if (libras) {
            libras.addEventListener("change", () => {
                document.body.classList.toggle(
                    "libras-ativo",
                    libras.checked
                );
            });
        }

        /* =============================================
           12. CRIAR CONTA DE OSC
           ============================================= */

        const accOsc = document.getElementById("criar-osc");

        accOsc?.addEventListener("click", () => {
            window.location.href = "cadastro/cadastro-osc.html";
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
                "Não foi possível carregar os dados do perfil. Tente novamente.";

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
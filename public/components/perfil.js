
import {
    fetchAutenticado,
    sessaoInvalidaConfirmada
} from "./api.js";

/* =========================================================
   CONSTANTES
   ========================================================= */

const TAMANHO_MAXIMO_FOTO = 10 * 1024 * 1024;

const TIPOS_IMAGEM_PERMITIDOS = new Set([
    "image/jpeg",
    "image/png",
    "image/webp"
]);

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
        configurarUploadFoto();

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

        const btn = document.querySelector(
            ".painel-perfil .btn, .painel-perfil .botao-editar"
        );

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
                        "O salvamento dos dados do perfil ainda não está implementado."
                    );
                }
            });
        }

        /* =============================================
           4. ABAS
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
           5. FILTROS DO REGISTRO
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
           6. ACESSIBILIDADE — TAMANHO DA LETRA
           ============================================= */

        const tamanhoLetra = document.getElementById(
            "acss-tamanho-letra"
        );

        tamanhoLetra?.addEventListener("change", () => {
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

        /* =============================================
           7. ACESSIBILIDADE — MODO DE EXIBIÇÃO
           ============================================= */

        const modoExibicao = document.getElementById(
            "acss-modo-exibicao"
        );

        modoExibicao?.addEventListener("change", () => {
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

        /* =============================================
           8. ACESSIBILIDADE — IDIOMA
           ============================================= */

        const idioma = document.getElementById("acss-idioma");

        idioma?.addEventListener("change", () => {
            document.documentElement.lang = idioma.value;
        });

        /* =============================================
           9. ACESSIBILIDADE — LEITURA DE TELA
           ============================================= */

        const leituraTela = document.getElementById(
            "acss-leitura-tela"
        );

        leituraTela?.addEventListener("change", () => {
            document.body.classList.toggle(
                "leitura-ativa",
                leituraTela.checked
            );
        });

        /* =============================================
           10. ACESSIBILIDADE — LIBRAS
           ============================================= */

        const libras = document.getElementById("acss-libras");

        libras?.addEventListener("change", () => {
            document.body.classList.toggle(
                "libras-ativo",
                libras.checked
            );
        });

        /* =============================================
           11. CRIAR CONTA DE OSC
           ============================================= */

        const accOsc = document.getElementById("criar-osc");

        accOsc?.addEventListener("click", () => {
            window.location.href = "/cadastro/cadastro-osc.html";
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

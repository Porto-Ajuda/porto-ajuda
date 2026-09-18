document.addEventListener("DOMContentLoaded", () => {

    /* =========================================================
       PERFIL — EDITAR / SALVAR
       ========================================================= */

    const btn = document.querySelector(".painel-perfil .btn");
    const inputs = document.querySelectorAll(".campo-perfil input");
    const senha = document.getElementById("senha");

    // O .campo-perfil é o elemento que contém a senha e a label
    const campoPerfil = document.querySelector(".campo-perfil");

    let editando = false;

    if (btn && campoPerfil) {

        // Começa com os campos bloqueados
        inputs.forEach(input => {
            input.disabled = true;
        });

        // Garante que a senha comece escondida
        campoPerfil.classList.remove("editando");

        btn.addEventListener("click", () => {

            editando = !editando;

            // Ativa/desativa os campos
            inputs.forEach(input => {
                input.disabled = !editando;
            });

            // Mostra/esconde senha
            campoPerfil.classList.toggle("editando", editando);

            if (editando) {

                btn.textContent = "Salvar Dados";

            } else {

                btn.textContent = "Alterar Dados";

                // Só tenta salvar se essa função existir
                if (typeof setItemsToLocalStorage === "function") {
                    setItemsToLocalStorage();
                }
            }
        });
    }


    /* =========================================================
       ABAS — REGISTRO / ACESSIBILIDADE / CONFIGURAÇÕES
       ========================================================= */

    const abas = document.querySelectorAll(".aba");
    const paineis = document.querySelectorAll(".painel-aba");

    abas.forEach(aba => {

        aba.addEventListener("click", () => {

            // Desativa todas as abas
            abas.forEach(item => {
                item.setAttribute("aria-selected", "false");
            });

            // Esconde todos os painéis
            paineis.forEach(painel => {
                painel.hidden = true;
            });

            // Ativa a aba clicada
            aba.setAttribute("aria-selected", "true");

            // Descobre qual painel pertence à aba
            const idPainel = aba.getAttribute("aria-controls");
            const painelAtivo = document.getElementById(idPainel);

            if (painelAtivo) {
                painelAtivo.hidden = false;
                painelAtivo.focus();
            }

        });

    });


    /* =========================================================
       FILTROS DO REGISTRO
       ========================================================= */

    const filtros = document.querySelectorAll(".filtro");
    const registros = document.querySelectorAll(".registro");
    const registroVazio = document.getElementById("registro-vazio");

    filtros.forEach(filtro => {

        filtro.addEventListener("click", () => {

            // Descobre o tipo escolhido
            const tipoFiltro = filtro.dataset.filtro;

            // Atualiza aria-pressed
            filtros.forEach(item => {
                item.setAttribute("aria-pressed", "false");
            });

            filtro.setAttribute("aria-pressed", "true");


            let quantidadeVisivel = 0;

            registros.forEach(registro => {

                const tipoRegistro = registro.dataset.tipo;

                // "tudo" mostra todos
                // caso contrário, compara com data-tipo
                const mostrar =
                    tipoFiltro === "tudo" ||
                    tipoRegistro === tipoFiltro;

                registro.hidden = !mostrar;

                if (mostrar) {
                    quantidadeVisivel++;
                }

            });


            // Mostra o estado vazio caso não exista registro
            if (registroVazio) {
                registroVazio.hidden = quantidadeVisivel !== 0;
            }

        });

    });


    /* =========================================================
       ACESSIBILIDADE — TAMANHO DA LETRA
       ========================================================= */

    const tamanhoLetra = document.getElementById("tamanho-letra");

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


    /* =========================================================
       ACESSIBILIDADE — MODO DE EXIBIÇÃO
       ========================================================= */

    const modoExibicao = document.getElementById("modo-exibicao");

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


    /* =========================================================
       ACESSIBILIDADE — IDIOMA
       ========================================================= */

    const idioma = document.getElementById("idioma");

    if (idioma) {

        idioma.addEventListener("change", () => {

            document.documentElement.lang = idioma.value;

        });

    }


    /* =========================================================
       ACESSIBILIDADE — LEITURA DE TELA
       ========================================================= */

    const leituraTela = document.getElementById("leitura-tela");

    if (leituraTela) {

        leituraTela.addEventListener("change", () => {

            document.body.classList.toggle(
                "leitura-ativa",
                leituraTela.checked
            );

        });

    }


    /* =========================================================
       ACESSIBILIDADE — LIBRAS
       ========================================================= */

    const libras = document.getElementById("libras");

    if (libras) {

        libras.addEventListener("change", () => {

            document.body.classList.toggle(
                "libras-ativo",
                libras.checked
            );

        });

    }

});
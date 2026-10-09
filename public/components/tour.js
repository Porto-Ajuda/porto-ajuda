/* =========================================================
   PORTO AJUDA
   TOUR INTERATIVO DO SITE
========================================================= */

(() => {

    "use strict";


    /* =====================================================
       CONFIGURAÇÃO
    ===================================================== */

    let etapaAtual = 0;
    let etapasAtuais = [];
    let elementoAtual = null;

    let resizeTimer = null;
    let scrollTimer = null;


    /* =====================================================
       TOURS DAS PÁGINAS
    ===================================================== */

    const TOURS = {

        /* =================================================
           MAPA / ONGs
        ================================================= */

        mapa: [

            {
                seletor: ".section-ongs",
                titulo: "Encontre organizações",
                texto:
                    "Nesta área você encontra organizações e serviços disponíveis na sua região."
            },

            {
                seletor: ".barra-pesquisa",
                titulo: "Pesquise uma organização",
                texto:
                    "Digite o nome ou parte do nome de uma organização para encontrá-la rapidamente."
            },

            {
                seletor: ".filtros",
                titulo: "Filtre por categoria",
                texto:
                    "Use os filtros para encontrar organizações de acordo com a área de atuação."
            },

            {
                seletor: "#map",
                titulo: "Explore o mapa",
                texto:
                    "O mapa mostra onde as organizações estão localizadas. Você pode navegar, aproximar e selecionar os marcadores."
            },

            {
                seletor: "#botao-inicio",
                titulo: "Voltar à localização inicial",
                texto:
                    "Use este botão para retornar à posição inicial do mapa."
            }

        ],


        /* =================================================
           HOME
        ================================================= */

        home: [

            {
                seletor: ".hero",
                titulo: "Bem-vindo ao Porto Ajuda",
                texto:
                    "Esta é a página inicial. Aqui você encontra os principais caminhos para descobrir organizações, campanhas, voluntariados e formas de ajudar."
            },

            {
                seletor: ".search-box",
                titulo: "Encontre o que procura",
                texto:
                    "Use a pesquisa e os filtros de tema e região para encontrar conteúdos relacionados ao tipo de ajuda que você procura."
            },

            {
                seletor: "#cards-osc",
                titulo: "OSC's em destaque",
                texto:
                    "Aqui aparecem organizações da sociedade civil que estão em destaque no Porto Ajuda."
            },

            {
                seletor: "#cards-voluntariacao",
                titulo: "Voluntariados",
                texto:
                    "Nesta área você pode encontrar oportunidades de voluntariado e participação em iniciativas."
            },

            {
                seletor: "#cards-doacao",
                titulo: "Doações",
                texto:
                    "Aqui ficam iniciativas relacionadas a doações e formas de contribuir."
            }

        ],


        /* =================================================
           SOBRE
        ================================================= */

        sobre: [

            {
                seletor: ".sobre-hero",
                titulo: "Sobre o Porto Ajuda",
                texto:
                    "Aqui você conhece a proposta e a ideia por trás do Porto Ajuda."
            },

            {
                seletor: ".missao-card",
                titulo: "Nossa missão",
                texto:
                    "Esta seção apresenta a missão da iniciativa e o objetivo de aproximar solidariedade de quem precisa."
            },

            {
                seletor: ".pilares",
                titulo: "Nossos pilares",
                texto:
                    "Os pilares mostram os princípios que orientam a atuação do Porto Ajuda."
            },

            {
                seletor: ".citacao",
                titulo: "Uma ideia que resume a iniciativa",
                texto:
                    "A página termina reforçando a ideia de que pequenas ações podem gerar impacto quando realizadas coletivamente."
            }

        ],


        /* =================================================
           PERFIL
        ================================================= */

        perfil: [

            {
                seletor: ".painel-perfil",
                titulo: "Seu perfil",
                texto:
                    "Aqui ficam suas principais informações pessoais e os dados associados à sua conta."
            },

            {
                seletor: "#aba-registro",
                titulo: "Registro",
                texto:
                    "Nesta aba você pode acompanhar o registro das suas atividades e participações."
            },

            {
                seletor: "#aba-acss",
                titulo: "Acessibilidade",
                texto:
                    "Aqui você encontra configurações relacionadas à acessibilidade da sua experiência no Porto Ajuda."
            },

            {
                seletor: "#aba-configuracoes",
                titulo: "Configurações",
                texto:
                    "Use esta área para controlar configurações da sua conta."
            },

            {
                seletor: ".linha-tempo",
                titulo: "Seu histórico",
                texto:
                    "O histórico reúne registros importantes das atividades realizadas na plataforma."
            }

        ],


        /* =================================================
           ONG / FEED
        ================================================= */

        ongs: [

            {
                seletor: ".ONGs-hero",
                titulo: "Iniciativas e organizações",
                texto:
                    "Aqui você acompanha conteúdos e publicações relacionados às organizações presentes no Porto Ajuda."
            },

            {
                seletor: "#feed-posts",
                titulo: "Publicações",
                texto:
                    "Nesta área aparecem as publicações e informações compartilhadas pelas organizações."
            },

            {
                seletor: ".post-card",
                titulo: "Veja uma publicação",
                texto:
                    "Cada cartão apresenta informações de uma publicação. Selecione uma delas para conhecer mais detalhes."
            }

        ],


        /* =================================================
           PERFIL DE ONG
        ================================================= */

        ongPerfil: [

            {
                seletor: ".infos-ong",
                titulo: "Informações da organização",
                texto:
                    "Aqui você encontra as principais informações sobre esta organização."
            },

            {
                seletor: "#descricao-ong",
                titulo: "Sobre a organização",
                texto:
                    "Nesta área é apresentada a descrição e a finalidade da organização."
            },

            {
                seletor: "#servicos-lista",
                titulo: "Serviços oferecidos",
                texto:
                    "Veja quais serviços ou formas de atendimento estão disponíveis."
            },

            {
                seletor: "#ods-container",
                titulo: "Objetivos de Desenvolvimento Sustentável",
                texto:
                    "Esta seção relaciona a atuação da organização aos Objetivos de Desenvolvimento Sustentável."
            },

            {
                seletor: "#Doacao",
                titulo: "Apoie esta organização",
                texto:
                    "Quando disponível, esta área apresenta opções para contribuir diretamente com a organização."
            }

        ],


        /* =================================================
           CADASTRO / LOGIN
        ================================================= */

        cadastro: [

            {
                seletor: "#frame",
                titulo: "Entre ou crie sua conta",
                texto:
                    "Nesta tela você pode entrar em uma conta existente ou iniciar um novo cadastro."
            },

            {
                seletor: "#goCad",
                titulo: "Criar uma conta",
                texto:
                    "Use esta opção para acessar o formulário de cadastro."
            },

            {
                seletor: "#form-cadastro",
                titulo: "Preencha seus dados",
                texto:
                    "O cadastro é dividido em etapas para facilitar o preenchimento das informações."
            },

            {
                seletor: "#nextBtn",
                titulo: "Avance pelas etapas",
                texto:
                    "Depois de preencher uma etapa, use este botão para continuar."
            },

            {
                seletor: "#senha-texto",
                titulo: "Crie uma senha segura",
                texto:
                    "A senha possui requisitos de segurança que são verificados enquanto você digita."
            },

            {
                seletor: "#check-termos",
                titulo: "Termos e privacidade",
                texto:
                    "Antes de concluir o cadastro, leia e confirme os Termos de Uso e a Política de Privacidade."
            }

        ],


        /* =================================================
           INICIATIVAS
        ================================================= */

        iniciativas: [

            {
                seletor: ".introducao_jornal",
                titulo: "Campanhas sociais",
                texto:
                    "Aqui você encontra campanhas e iniciativas sociais recentes."
            },

            {
                seletor: ".noticias",
                titulo: "Conheça as iniciativas",
                texto:
                    "Cada cartão apresenta uma iniciativa, campanha ou ação que pode ser relevante para a comunidade."
            },

            {
                seletor: ".noticia",
                titulo: "Leia uma campanha",
                texto:
                    "Selecione uma notícia para conhecer os detalhes da iniciativa."
            }

        ],


        /* =================================================
           CONTATO
        ================================================= */

        contato: [

            {
                seletor: ".corpo",
                titulo: "Entre em contato",
                texto:
                    "Use este formulário para enviar uma mensagem para a equipe do Porto Ajuda."
            },

            {
                seletor: "#nome",
                titulo: "Seu nome",
                texto:
                    "Informe seu nome para que sua mensagem possa ser identificada."
            },

            {
                seletor: "#email",
                titulo: "Seu e-mail",
                texto:
                    "Informe um endereço de e-mail válido para contato."
            },

            {
                seletor: "#mensagem",
                titulo: "Sua mensagem",
                texto:
                    "Escreva aqui sua dúvida, sugestão ou solicitação."
            },

            {
                seletor: "#form-contato button[type='submit']",
                titulo: "Enviar",
                texto:
                    "Depois de preencher o formulário, utilize este botão para enviar sua mensagem."
            }

        ],


        /* =================================================
           APOIE-NOS
        ================================================= */

        apoie: [
            {
                seletor: ".primeira_tela",
                titulo: "Apoie o Porto Ajuda",
                texto: "Nesta página você encontra formas de contribuir para manter o projeto funcionando."
            },

            {
                seletor: ".valores_site",
                titulo: "Transparência",
                texto: "Nesta seção você pode entender como os recursos ajudam a manter a infraestrutura do Porto Ajuda."
            },

            {
                seletor: ".opcoes_pagamento",
                titulo: "Escolha o valor",
                texto: "Você pode escolher um dos valores sugeridos ou informar outro valor. Vamos gerar um Pix de exemplo para você ver como funciona.",
            },

            {
                seletor: "#resultado-pix",
                titulo: "Pagamento via Pix",
                texto: "Depois de escolher o valor, o Porto Ajuda apresenta aqui o QR Code e as informações necessárias para realizar o pagamento.",
                acao: "gerarPixDemo"
            },

            {
                seletor: "#copiar-chave",
                titulo: "Copiar chave Pix",
                texto: "Use este botão para copiar a chave Pix sem precisar digitá-la manualmente."
            },

            {
                seletor: "#copiar-pix",
                titulo: "Pix copia e cola",
                texto: "Você também pode copiar o código Pix completo e utilizá-lo no aplicativo do seu banco."
            },

            {
                seletor: ".compartilhar_site",
                titulo: "Ajude compartilhando",
                texto: "Não precisa doar para ajudar. Compartilhar o projeto também aumenta o alcance da iniciativa."
            }
        ]

    };

    const SEQUENCIA_TOUR_COMPLETO = [
        // { pagina: "home", url: "/" },
        // { pagina: "sobre", url: "/sobre" },
        // { pagina: "iniciativas", url: "/iniciativas" },
        // { pagina: "apoie", url: "/apoie" },
        // { pagina: "mapa", url: "/proximidade" },
        // { pagina: "ongs", url: "/oscs" }

        { pagina: "home", url: "Porto-Ajuda.html" },
        { pagina: "sobre", url: "sobre.html" },
        { pagina: "iniciativas", url: "iniciativas.html" },
        { pagina: "apoie", url: "apoie.html" },
        { pagina: "mapa", url: "proximity.html" },
        { pagina: "ongs", url: "oscs-page.html" }
    ];

    const CHAVE_TOUR_COMPLETO =
        "portoAjudaTourCompleto";

    function salvarEstadoTourCompleto(estado) {

        try {

            sessionStorage.setItem(
                CHAVE_TOUR_COMPLETO,
                JSON.stringify(estado)
            );

        } catch (erro) {

            console.warn(
                "Tour: não foi possível salvar o progresso.",
                erro
            );

        }

    }


    function lerEstadoTourCompleto() {

        try {

            const bruto =
                sessionStorage.getItem(
                    CHAVE_TOUR_COMPLETO
                );

            return bruto ?
                JSON.parse(bruto) :
                null;

        } catch (erro) {

            return null;

        }

    }


    function limparEstadoTourCompleto() {

        try {

            sessionStorage.removeItem(
                CHAVE_TOUR_COMPLETO
            );

        } catch (erro) {

        }

    }


    /* =====================================================
       IDENTIFICAR PÁGINA
    ===================================================== */

    function identificarPagina() {

        const path = window.location.pathname.toLowerCase();

        if (
            document.querySelector("#map") ||
            path.includes("proximity")
        ) {
            return "mapa";
        }

        if (
            document.querySelector(".primeira_tela") ||
            path.includes("apoie")
        ) {
            return "apoie";
        }

        if (
            document.querySelector("#form-contato") ||
            path.includes("contato")
        ) {
            return "contato";
        }

        if (
            document.querySelector(".noticias") &&
            document.querySelector(".manchete")
        ) {
            return "iniciativas";
        }

        if (
            document.querySelector("#form-cadastro") ||
            document.querySelector("#frame")
        ) {
            return "cadastro";
        }

        if (
            document.querySelector(".painel-perfil") ||
            document.querySelector("#titulo-perfil")
        ) {
            return "perfil";
        }

        if (
            document.querySelector(".infos-ong") ||
            document.querySelector("#descricao-ong")
        ) {
            return "ongPerfil";
        }

        if (
            document.querySelector(".ONGs-hero") ||
            document.querySelector("#feed-posts")
        ) {
            return "ongs";
        }

        if (
            document.querySelector(".sobre-hero") ||
            document.querySelector(".missao-card")
        ) {
            return "sobre";
        }

        if (
            document.querySelector(".hero") ||
            document.querySelector("#cards-osc") ||
            document.querySelector("#cards-doacao")
        ) {
            return "home";
        }

        return null;
    }


    /* =====================================================
       PEGAR ETAPAS VÁLIDAS
    ===================================================== */

    function obterEtapas() {

        const pagina = identificarPagina();

        if (!pagina || !TOURS[pagina]) {
            return [];
        }

        return TOURS[pagina].filter(etapa => {

            return document.querySelector(etapa.seletor);

        });

    }


    /* =====================================================
       ABRIR TOUR (pagina anterior)
    ===================================================== */

    function iniciarTour(opcoes = {}) {

        if (document.getElementById("tour-overlay")) {
            return;
        }

        etapasAtuais = obterEtapas();

        if (!etapasAtuais.length) {

            console.warn(
                "Porto Ajuda: nenhuma etapa disponível para esta página."
            );

            avancarTourCompletoSemEtapas();

            return;
        }

        etapaAtual =
            opcoes.comecarNaUltimaEtapa ?
                etapasAtuais.length - 1 :
                0;

        criarInterface();

        mostrarEtapa();

    }


    /* =====================================================
       ABRIR TOUR
    ===================================================== */

    function iniciarTourCompleto() {

        const pagina =
            identificarPagina();

        const indice =
            SEQUENCIA_TOUR_COMPLETO.findIndex(
                item => item.pagina === pagina
            );

        if (indice === -1) {

            iniciarTour();

            return;

        }

        salvarEstadoTourCompleto({
            ativo: true,
            indice
        });

        iniciarTour();

    }

    function avancarTourCompletoSemEtapas() {

        const estado =
            lerEstadoTourCompleto();

        if (!estado || !estado.ativo) {
            return;
        }

        const proxima =
            SEQUENCIA_TOUR_COMPLETO[
            estado.indice + 1
            ];

        if (!proxima) {

            limparEstadoTourCompleto();

            return;

        }

        salvarEstadoTourCompleto({
            ativo: true,
            indice: estado.indice + 1
        });

        window.location.href =
            proxima.url;

    }


    /* =====================================================
       CRIAR INTERFACE
    ===================================================== */

    function criarInterface() {

        const overlay =
            document.createElement("div");

        overlay.id = "tour-overlay";

        overlay.setAttribute(
            "aria-label",
            "Tour pelo Porto Ajuda"
        );

        overlay.innerHTML = `

            <div
                id="tour-destaque"
                aria-hidden="true">
            </div>

            <div
                id="tour-caixa"
                role="dialog"
                aria-modal="true"
                aria-labelledby="tour-titulo">

                <div class="tour-topo">

                    <div class="tour-marca">
                        <span class="tour-ponto"></span>
                        Porto Ajuda
                    </div>

                    <button
                        id="tour-fechar"
                        type="button"
                        aria-label="Fechar tour"
                        title="Fechar">
                        ×
                    </button>

                </div>

                <div class="tour-conteudo">

                    <span
                        id="tour-etiqueta">
                        TOUR PELO SITE
                    </span>

                    <h2 id="tour-titulo"></h2>

                    <p id="tour-texto"></p>

                </div>

                <div class="tour-progresso">

                    <div
                        id="tour-progresso-barra">
                    </div>

                </div>

                <div class="tour-rodape">

                    <span
                        id="tour-contador">
                    </span>

                    <div class="tour-acoes">

                        <button
                            id="tour-pular"
                            type="button">
                            Pular
                        </button>

                        <button
                            id="tour-voltar"
                            type="button">
                            <i class="fa-solid fa-arrow-left"></i>
                            Voltar
                        </button>

                        <button
                            id="tour-proximo"
                            type="button">
                            Próximo
                            <i class="fa-solid fa-arrow-right"></i>
                        </button>

                    </div>

                </div>

                <div class="tour-dica">
                    <span>ESC</span>
                    para fechar
                    <span>F1</span>
                    para abrir novamente
                </div>

            </div>
        `;

        document.body.appendChild(overlay);


        /* BOTÃO FECHAR */

        document
            .getElementById("tour-fechar")
            .addEventListener(
                "click",
                finalizarTour
            );


        /* PULAR */

        document
            .getElementById("tour-pular")
            .addEventListener(
                "click",
                finalizarTour
            );


        /* VOLTAR */

        document
            .getElementById("tour-voltar")
            .addEventListener(
                "click",
                etapaAnterior
            );


        /* PRÓXIMO */

        document
            .getElementById("tour-proximo")
            .addEventListener(
                "click",
                proximaEtapa
            );

    }

    /* =====================================================
       EXECUTAR AÇÃO DA ETAPA
    ===================================================== */

    function executarAcaoEtapa(etapa) {

        if (!etapa || !etapa.acao) {
            return;
        }

        if (etapa.acao === "gerarPixDemo") {

            garantirPixGerado();

        }

    }

    function garantirPixGerado() {

        const resultado =
            document.getElementById("resultado-pix");

        const qrcode =
            document.getElementById("qrcode");

        if (!resultado) {

            console.warn(
                "Tour: #resultado-pix não encontrado nesta página."
            );

            return;
        }


        const pixJaGerado =
            qrcode &&
            qrcode.children.length > 0;

        if (pixJaGerado) {

            resultado.style.display =
                "block";

            elementoAtual =
                resultado;

            setTimeout(() => {

                atualizarPosicao();

            }, 200);

            return;

        }

        const botaoPix =
            document.querySelector(".valor-pix");

        if (!botaoPix) {

            console.warn(
                "Tour: nenhum botão .valor-pix encontrado."
            );

            return;
        }

        botaoPix.click();

        setTimeout(() => {

            elementoAtual =
                resultado;

            atualizarPosicao();

        }, 600);

    }


    /* =====================================================
       MOSTRAR ETAPA
    ===================================================== */

    function mostrarEtapa() {

        if (!etapasAtuais.length) {
            finalizarTour();
            return;
        }

        const etapa =
            etapasAtuais[etapaAtual];

        const elemento =
            document.querySelector(
                etapa.seletor
            );


        if (!elemento) {

            proximaEtapa();

            return;
        }


        elementoAtual = elemento;


        /* ================================================
           TEXTO
        ================================================ */

        const titulo =
            document.getElementById(
                "tour-titulo"
            );

        const texto =
            document.getElementById(
                "tour-texto"
            );

        titulo.textContent =
            etapa.titulo;

        texto.textContent =
            etapa.texto;


        /* ================================================
           CONTADOR
        ================================================ */

        document.getElementById(
            "tour-contador"
        ).textContent =
            `${etapaAtual + 1} de ${etapasAtuais.length}`;


        /* ================================================
           PROGRESSO
        ================================================ */

        const progresso =
            ((etapaAtual + 1) /
                etapasAtuais.length) *
            100;

        document.getElementById(
            "tour-progresso-barra"
        ).style.width =
            `${progresso}%`;


        /* ================================================
           BOTÕES
        ================================================ */

        const voltar =
            document.getElementById(
                "tour-voltar"
            );

        const proximo =
            document.getElementById(
                "tour-proximo"
            );

        const estadoCompleto =
            lerEstadoTourCompleto();

        const temPaginaAnterior =
            !!estadoCompleto &&
            estadoCompleto.ativo &&
            estadoCompleto.indice > 0;

        const temProximaPagina =
            !!estadoCompleto &&
            estadoCompleto.ativo &&
            !!SEQUENCIA_TOUR_COMPLETO[
            estadoCompleto.indice + 1
            ];

        voltar.disabled =
            etapaAtual === 0 &&
            !temPaginaAnterior;

        const ultimaEtapaDaPagina =
            etapaAtual ===
            etapasAtuais.length - 1;

        if (
            ultimaEtapaDaPagina &&
            temProximaPagina
        ) {

            proximo.innerHTML = `
                Próxima página
                <i class="fa-solid fa-arrow-right"></i>
            `;

        } else if (ultimaEtapaDaPagina) {

            proximo.innerHTML = `
                Concluir
                <i class="fa-solid fa-check"></i>
            `;

        } else {

            proximo.innerHTML = `
                Próximo
                <i class="fa-solid fa-arrow-right"></i>
            `;

        }


        /* ================================================
           ROLAGEM
        ================================================ */

        elemento.scrollIntoView({
            behavior: "smooth",
            block: "center",
            inline: "nearest"
        });

        clearTimeout(scrollTimer);

        scrollTimer =
            setTimeout(() => {

                atualizarPosicao();

                executarAcaoEtapa(etapa);

            }, 400);

    }


    /* =====================================================
       ATUALIZAR POSIÇÃO
    ===================================================== */

    function atualizarPosicao() {

        if (!elementoAtual) {
            return;
        }

        const destaque =
            document.getElementById(
                "tour-destaque"
            );

        const caixa =
            document.getElementById(
                "tour-caixa"
            );

        if (!destaque || !caixa) {
            return;
        }


        const rect =
            elementoAtual.getBoundingClientRect();

        if (rect.width === 0 && rect.height === 0) {
            return;
        }


        /* ================================================
           DESTAQUE
        ================================================ */

        const margem = 8;

        destaque.style.top =
            `${rect.top - margem}px`;

        destaque.style.left =
            `${rect.left - margem}px`;

        destaque.style.width =
            `${rect.width + margem * 2}px`;

        destaque.style.height =
            `${rect.height + margem * 2}px`;


        /* ================================================
           POSICIONAMENTO DA CAIXA
        ================================================ */

        const margemCaixa = 24;

        const caixaWidth =
            caixa.offsetWidth;

        const caixaHeight =
            caixa.offsetHeight;


        let left =
            rect.left +
            rect.width / 2 -
            caixaWidth / 2;

        left =
            Math.max(
                16,
                left
            );

        left =
            Math.min(
                window.innerWidth -
                caixaWidth -
                16,
                left
            );


        const espacoAbaixo =
            window.innerHeight -
            rect.bottom;

        const espacoAcima =
            rect.top;


        let top;


        /* ================================================
           ABAIXO
        ================================================ */

        if (
            espacoAbaixo >=
            caixaHeight + margemCaixa
        ) {

            top =
                rect.bottom +
                margemCaixa;

        }


        /* ================================================
           ACIMA
        ================================================ */

        else if (
            espacoAcima >=
            caixaHeight + margemCaixa
        ) {

            top =
                rect.top -
                caixaHeight -
                margemCaixa;

        }


        /* ================================================
           MOBILE / SEM ESPAÇO
        ================================================ */

        else {

            top =
                window.innerHeight -
                caixaHeight -
                16;

        }


        caixa.style.left =
            `${left}px`;

        caixa.style.top =
            `${Math.max(16, top)}px`;

        caixa.style.bottom =
            "auto";

    }


    /* =====================================================
       PRÓXIMA ETAPA
    ===================================================== */

    function proximaEtapa() {

        if (
            etapaAtual <
            etapasAtuais.length - 1
        ) {

            etapaAtual++;

            mostrarEtapa();

            return;

        }

        const estado =
            lerEstadoTourCompleto();

        if (estado && estado.ativo) {

            const proxima =
                SEQUENCIA_TOUR_COMPLETO[
                estado.indice + 1
                ];

            if (proxima) {

                salvarEstadoTourCompleto({
                    ativo: true,
                    indice: estado.indice + 1
                });

                window.location.href =
                    proxima.url;

                return;

            }

        }

        finalizarTour();

    }


    /* =====================================================
       ETAPA ANTERIOR
    ===================================================== */

    function etapaAnterior() {

        if (etapaAtual > 0) {

            etapaAtual--;

            mostrarEtapa();

            return;

        }

        const estado =
            lerEstadoTourCompleto();

        if (
            estado &&
            estado.ativo &&
            estado.indice > 0
        ) {

            const anterior =
                SEQUENCIA_TOUR_COMPLETO[
                estado.indice - 1
                ];

            salvarEstadoTourCompleto({
                ativo: true,
                indice: estado.indice - 1,
                comecarNaUltimaEtapa: true
            });

            window.location.href =
                anterior.url;

        }

    }


    /* =====================================================
       FINALIZAR
    ===================================================== */

    function finalizarTour() {

        const overlay =
            document.getElementById(
                "tour-overlay"
            );

        if (!overlay) {
            return;
        }


        overlay.classList.add(
            "tour-saindo"
        );

        limparEstadoTourCompleto();


        setTimeout(() => {

            overlay.remove();

            elementoAtual = null;

        }, 220);

    }


    /* =====================================================
       BOTÃO DO FOOTER
    ===================================================== */

    document.addEventListener(
        "click",
        evento => {

            const botao =
                evento.target.closest(
                    "#btn-tour"
                );

            if (!botao) {
                return;
            }

            iniciarTourCompleto();

        }
    );


    /* =====================================================
       F1 / ESC
    ===================================================== */

    document.addEventListener(
        "keydown",
        evento => {

            /* F1 */

            if (
                evento.key === "F1"
            ) {

                evento.preventDefault();

                if (
                    !document.getElementById(
                        "tour-overlay"
                    )
                ) {

                    iniciarTourCompleto();

                }

                return;
            }


            /* ESC */

            if (
                evento.key === "Escape"
            ) {

                if (
                    document.getElementById(
                        "tour-overlay"
                    )
                ) {

                    finalizarTour();

                }

            }

        }
    );


    /* =====================================================
       REDIMENSIONAMENTO
    ===================================================== */

    window.addEventListener(
        "resize",
        () => {

            if (
                !document.getElementById(
                    "tour-overlay"
                )
            ) {
                return;
            }

            clearTimeout(
                resizeTimer
            );

            resizeTimer =
                setTimeout(
                    atualizarPosicao,
                    100
                );

        }
    );


    /* =====================================================
       SCROLL MANUAL
    ===================================================== */

    window.addEventListener(
        "scroll",
        () => {

            if (
                !document.getElementById(
                    "tour-overlay"
                )
            ) {
                return;
            }

            atualizarPosicao();

        },
        { passive: true }
    );

    function retomarTourCompletoSeNecessario() {

        const estado =
            lerEstadoTourCompleto();

        if (!estado || !estado.ativo) {
            return;
        }

        const pagina =
            identificarPagina();

        const itemEsperado =
            SEQUENCIA_TOUR_COMPLETO[
            estado.indice
            ];

        if (
            !itemEsperado ||
            itemEsperado.pagina !== pagina
        ) {

            limparEstadoTourCompleto();

            return;

        }

        const iniciar = () => {

            setTimeout(() => {

                iniciarTour({
                    comecarNaUltimaEtapa:
                        !!estado.comecarNaUltimaEtapa
                });

            }, 350);

        };

        if (document.readyState === "complete") {

            iniciar();

        } else {

            window.addEventListener(
                "load",
                iniciar,
                { once: true }
            );

        }

    }

    retomarTourCompletoSeNecessario();


    /* =====================================================
       DISPONIBILIZAR GLOBALMENTE
    ===================================================== */

    window.iniciarTourPortoAjuda =
        iniciarTour;

    window.iniciarTourCompletoPortoAjuda =
        iniciarTourCompleto;

})();

// ========================================
// CÓDIGO SECRETO - KONAMI CODE
// ========================================

const easterEggs = [
    {
        tipo: "frase",
        conteudo: "The cake is a lie - Portal"
    },
    {
        tipo: "frase",
        conteudo: "You're gonna have a bad time. - Undertale"
    },
    {
        tipo: "frase",
        conteudo: "Toasty! - Mortal Kombat"
    },
    {
        tipo: "frase",
        conteudo: "There is no cow level - Diablo"
    },
    {
        tipo: "frase",
        conteudo: "All your base are belong to us - Zero Wing"
    },
    {
        tipo: "frase",
        conteudo: "Do a barrel roll! - Star Fox"
    },
    {
        tipo: "frase",
        conteudo: "Would you kindly? - BioShock"
    },
    {
        tipo: "frase",
        conteudo: "War never changes. - Fallout"
    },
    {
        tipo: "frase",
        conteudo: "It's dangerous to go alone! Take this. - The Legend of Zelda"
    },
    {
        tipo: "frase",
        conteudo: "The right man in the wrong place. - Half-Life 2"
    },
    {
        tipo: "frase",
        conteudo: "Stay awhile and listen. - Diablo II"
    },
    {
        tipo: "frase",
        conteudo: "Praise the sun! - Dark Souls"
    },
    {
        tipo: "frase",
        conteudo: "Snake? Snake?! SNAAAKE! - Metal Gear Solid"
    },
    {
        tipo: "frase",
        conteudo: "Irineu, você não sabe nem eu!"
    },
    {
        tipo: "frase",
        conteudo: "Dança, gatinho, dança!"
    },
    {
        tipo: "frase",
        conteudo: "Ô loco, meu!"
    },
    {
        tipo: "frase",
        conteudo: "Rapaz, que isso!"
    },
    {
        tipo: "frase",
        conteudo: "Quem quer dinheiro?"
    },
    {
        tipo: "frase",
        conteudo: "Ma ôê!"
    },
    {
        tipo: "frase",
        conteudo: "Tá pegando fogo, bicho!"
    },
    {
        tipo: "frase",
        conteudo: "Se vira nos 30!"
    },
    {
        tipo: "frase",
        conteudo: "You're gonna have a bad time. - Undertale"
    },
    {
        tipo: "frase",
        conteudo: "Rip and tear. - Doom"
    },
    {
        tipo: "frase",
        conteudo: "I used to be an adventurer like you... - Skyrim"
    },
    {
        tipo: "frase",
        conteudo: "Hey! Listen! - The Legend of Zelda: Ocarina of Time"
    },
    {
        tipo: "frase",
        conteudo: "Zug zug. - World of Warcraft"
    },
    {
        tipo: "frase",
        conteudo: "Parece que você descobriu uma coisa que não deveria estar aqui..."
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/xTQQ1vfn68g?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/RpeZtgF6zA4?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/FWuAiQt5bsc?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/-gJ6Yf5xbwA?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/7CHUtmnAS-k?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/f5pCVs10uZs?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/3QFhxUyMyM4?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/CIF-r7F2clc?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/vMJ6rUG2rGc?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/4iisysmwB_k?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/nXtU_ckgnl4?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/TpAu95MjO0I?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/4hYt9OsBJ-Q?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/1XSysnj2Bk0?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/9n7JNGYFidM?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/imM_BHL5EAw?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/BeSGOfUS-9I?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/qthxa7m_B4E?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/LJHZ15s0Tus?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/AFkoZRHE8fI?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "video",
        conteudo: "https://www.youtube.com/embed/Um9L184uXuI?autoplay=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1"
    },
    {
        tipo: "doom",
        conteudo: "../doom/index.html"
    }
];

const codigoSecreto = [
    "ArrowUp",
    "ArrowUp",
    "ArrowDown",
    "ArrowDown",
    "ArrowLeft",
    "ArrowRight",
    "ArrowLeft",
    "ArrowRight",
    "KeyB",
    "KeyA"
];

let progressoCodigo = 0;


// ========================================
// DETECTAR CÓDIGO SECRETO
// ========================================

document.addEventListener("keydown", (event) => {

    // Não interfere enquanto a pessoa estiver digitando
    const elemento = event.target;

    if (
        elemento.tagName === "INPUT" ||
        elemento.tagName === "TEXTAREA" ||
        elemento.tagName === "SELECT" ||
        elemento.isContentEditable
    ) {
        return;
    }


    // ========================================
    // FECHAR DOOM COM ESC
    // ========================================

    if (event.key === "Escape") {

        const mensagem =
            document.getElementById("mensagem-secreta");

        if (mensagem) {

            // Para qualquer narração
            if ("speechSynthesis" in window) {
                speechSynthesis.cancel();
            }

            mensagem.remove();
        }

        return;
    }


    // ========================================
    // VERIFICA A PRÓXIMA TECLA DA SEQUÊNCIA
    // ========================================

    if (event.code === codigoSecreto[progressoCodigo]) {

        progressoCodigo++;

        // Código completo
        if (progressoCodigo === codigoSecreto.length) {

            mostrarEasterEgg();

            // Reinicia para poder descobrir novamente
            progressoCodigo = 0;
        }

    } else {

        // Se errar, reinicia.
        // Se a tecla pressionada for um novo "↑",
        // ela já começa uma nova tentativa.
        progressoCodigo =
            event.code === codigoSecreto[0] ? 1 : 0;
    }
});


// ========================================
// MOSTRAR EASTER EGG
// ========================================

function mostrarEasterEgg() {

    // Não deixa abrir dois ao mesmo tempo
    if (document.getElementById("mensagem-secreta")) {
        return;
    }


    // ========================================
    // VERIFICA SE EXISTEM EASTER EGGS
    // ========================================

    if (!easterEggs || easterEggs.length === 0) {

        console.warn(
            "Nenhum easter egg foi cadastrado."
        );

        return;
    }


    // ========================================
    // SORTEIO POR PROBABILIDADE
    // ========================================
    //
    // 49,5% → frase
    // 49,5% → vídeo
    // 1%    → DOOM
    //

    const sorteio = Math.random() * 100;

    let tipoEscolhido;

    if (sorteio < 49.5) {

        tipoEscolhido = "frase";

    } else if (sorteio < 99) {

        tipoEscolhido = "video";

    } else {

        tipoEscolhido = "doom";
    }


    // ========================================
    // PEGA APENAS OS EASTER EGGS
    // DO TIPO SORTEADO
    // ========================================

    const easterEggsDisponiveis =
        easterEggs.filter(
            item => item.tipo === tipoEscolhido
        );


    // ========================================
    // VERIFICA SE EXISTE ALGUM ITEM
    // ========================================

    if (easterEggsDisponiveis.length === 0) {

        console.warn(
            `Nenhum easter egg do tipo "${tipoEscolhido}" foi cadastrado.`
        );

        return;
    }


    // ========================================
    // SORTEIA UM ITEM DENTRO DO TIPO
    // ========================================

    const easterEgg =
        easterEggsDisponiveis[
        Math.floor(
            Math.random() *
            easterEggsDisponiveis.length
        )
        ];


    // ========================================
    // VERIFICA SE O TIPO É VÁLIDO
    // ========================================

    if (
        easterEgg.tipo !== "frase" &&
        easterEgg.tipo !== "video" &&
        easterEgg.tipo !== "doom"
    ) {

        console.warn(
            "Tipo de easter egg inválido:",
            easterEgg.tipo
        );

        return;
    }


    // ========================================
    // CRIA O MODAL
    // ========================================

    const mensagem =
        document.createElement("div");

    mensagem.id = "mensagem-secreta";


    // ========================================
    // EASTER EGG DE FRASE
    // ========================================

    if (easterEgg.tipo === "frase") {

        mensagem.innerHTML = `
            <div class="mensagem-secreta-conteudo">

                <span class="mensagem-secreta-icone">
                    ✦
                </span>

                <h2>Easter Egg encontrado!</h2>

                <p>
                    ${easterEgg.conteudo}
                </p>

                <button
                    type="button"
                    id="fechar-segredo">
                    Fechar
                </button>

            </div>
        `;


        // ========================================
        // NARRAÇÃO DA FRASE
        // ========================================

        if ("speechSynthesis" in window) {

            const fala =
                new SpeechSynthesisUtterance(
                    easterEgg.conteudo
                );

            fala.lang = "pt-BR";
            fala.volume = 1;
            fala.rate = 1;
            fala.pitch = 1;


            // Cancela qualquer fala anterior
            speechSynthesis.cancel();


            // Fala a frase
            speechSynthesis.speak(fala);
        }
    }


    // ========================================
    // EASTER EGG DE VÍDEO
    // ========================================

    else if (easterEgg.tipo === "video") {

        mensagem.innerHTML = `
            <div class="mensagem-secreta-conteudo">

                <span class="mensagem-secreta-icone">
                    ✦
                </span>

                <h2>Easter Egg encontrado!</h2>

                <div class="video-secreto">

                    <iframe
                        src="${easterEgg.conteudo}"
                        title="Easter Egg"
                        frameborder="0"
                        allow="autoplay; encrypted-media"
                        tabindex="-1">
                    </iframe>

                </div>

                <button
                    type="button"
                    id="fechar-segredo">
                    Fechar
                </button>

            </div>
        `;
    }


    // ========================================
    // EASTER EGG DOOM
    // ========================================

    else if (easterEgg.tipo === "doom") {

        mensagem.innerHTML = `
            <div class="doom-container">

                <iframe
                    src="${easterEgg.conteudo}"
                    class="doom-frame"
                    title="DOOM"
                    allow="fullscreen; autoplay"
                    allowfullscreen>
                </iframe>

            </div>
        `;
    }


    // ========================================
    // ADICIONA AO SITE
    // ========================================

    document.body.appendChild(mensagem);


    // ========================================
    // CONFIGURA O IFRAME
    // ========================================

    const iframe =
        mensagem.querySelector("iframe");


    if (iframe) {

        // Vídeos não podem ser clicados
        if (easterEgg.tipo === "video") {

            iframe.style.pointerEvents = "none";
        }

        // DOOM permanece completamente interativo
    }


    // ========================================
    // BOTÃO FECHAR
    // ========================================

    const botaoFechar =
        mensagem.querySelector("#fechar-segredo");


    if (botaoFechar) {

        botaoFechar.addEventListener(
            "click",
            () => {

                // Para a narração
                if ("speechSynthesis" in window) {
                    speechSynthesis.cancel();
                }

                // Remove o modal
                mensagem.remove();
            }
        );
    }


    // ========================================
    // CLICAR FORA PARA FECHAR
    // ========================================

    mensagem.addEventListener(
        "click",
        (event) => {

            // O DOOM não fecha ao clicar fora
            if (easterEgg.tipo === "doom") {
                return;
            }


            // Frase e vídeo fecham ao clicar no fundo
            if (event.target === mensagem) {

                // Para a narração
                if ("speechSynthesis" in window) {
                    speechSynthesis.cancel();
                }

                mensagem.remove();
            }
        }
    );
}
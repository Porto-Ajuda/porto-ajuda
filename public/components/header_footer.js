/* =========================================================
   MODAL
   ========================================================= */

const modalCSS = document.createElement("link");

modalCSS.rel = "stylesheet";
modalCSS.href = "../styles/modal.css";

document.head.appendChild(modalCSS);


const script1 = document.createElement("script");

script1.src = "../components/modal/modal-body.js";

script1.onload = () => {

    const script2 = document.createElement("script");

    script2.src = "../components/modal/modal-control.js";

    document.head.appendChild(script2);
};

document.head.appendChild(script1);


/* =========================================================
   MODAL - CONTAINER
   ========================================================= */

document.body.insertAdjacentHTML(
    "afterbegin",
    `<div id="modal-termos-politica"></div>`
);


/* =========================================================
   HEADER
   ========================================================= */

const cabecalho = `

<a href="Porto-Ajuda.html">

    <div class="logo">

        <div class="logo-circle">

            <img
                class="logo-circle-img"
                src="../assets/logo-night.png"
                alt="Logo Porto Ajuda"
            >

        </div>

        <span>Porto Ajuda</span>

    </div>

</a>


<nav>

    <ul class="nav-links">

        <li>
            <a href="Porto-Ajuda.html">
                Home
            </a>
        </li>

        <li id="barra">|</li>

        <li>
            <a href="sobre.html">
                Sobre
            </a>
        </li>

        <li id="barra">|</li>

        <li>
            <a href="iniciativas.html">
                Campanhas Sociais
            </a>
        </li>

        <li id="barra">|</li>

        <li>
            <a href="apoie.html">
                Apoiar o Site
            </a>
        </li>

        <li id="barra">|</li>

        <li>
            <a href="proximity.html">
                ONG's Regionais
            </a>
        </li>

    </ul>

</nav>


<div class="header-actions">

    <!-- BOTÃO MOBILE -->

    <button
        id="botao-menu-mobile"
        class="menu-mobile-button"
        type="button"
        aria-label="Abrir menu"
        aria-expanded="false"
        aria-controls="menu-mobile-overlay"
    >
        ☰
    </button>


    <!-- PERFIL -->

    <a
        href="login.html"
        aria-label="Entrar ou acessar perfil"
    >

        <div class="profile-icon">

            <i class="fa-regular fa-user"></i>

        </div>

    </a>

</div>

`;


document
    .querySelector(".navbar")
    ?.insertAdjacentHTML(
        "beforeend",
        cabecalho
    );


/* =========================================================
   MENU MOBILE
   ========================================================= */

const menuMobileHTML = `

<div
    id="menu-mobile-overlay"
    class="menu-mobile-overlay"
    aria-hidden="true"
>

    <aside
        id="menu-mobile"
        class="menu-mobile"
        aria-label="Menu de navegação"
    >

        <div class="menu-mobile-header">

            <h2>
                Navegação
            </h2>

            <button
                id="fechar-menu-mobile"
                class="menu-mobile-fechar"
                type="button"
                aria-label="Fechar menu"
            >
                ×
            </button>

        </div>


        <ul class="menu-mobile-links">

            <li>
                <a href="Porto-Ajuda.html">
                    Home
                </a>
            </li>

            <li>
                <a href="sobre.html">
                    Sobre
                </a>
            </li>

            <li>
                <a href="iniciativas.html">
                    Campanhas Sociais
                </a>
            </li>

            <li>
                <a href="apoie.html">
                    Apoiar o Site
                </a>
            </li>

            <li>
                <a href="proximity.html">
                    ONG's Regionais
                </a>
            </li>

        </ul>

    </aside>

</div>

`;


document.body.insertAdjacentHTML(
    "beforeend",
    menuMobileHTML
);


/* =========================================================
   CONTROLE DO MENU MOBILE
   ========================================================= */

const botaoMenu =
    document.querySelector(
        "#botao-menu-mobile"
    );

const menuOverlay =
    document.querySelector(
        "#menu-mobile-overlay"
    );

const botaoFecharMenu =
    document.querySelector(
        "#fechar-menu-mobile"
    );

const painelMenu =
    document.querySelector(
        "#menu-mobile"
    );


function abrirMenuMobile() {

    if (!menuOverlay) {
        return;
    }

    menuOverlay.classList.add("aberto");

    menuOverlay.setAttribute(
        "aria-hidden",
        "false"
    );

    document.body.classList.add(
        "menu-mobile-aberto"
    );


    if (botaoMenu) {

        botaoMenu.setAttribute(
            "aria-expanded",
            "true"
        );

        botaoMenu.setAttribute(
            "aria-label",
            "Fechar menu"
        );

    }

}


function fecharMenuMobile() {

    if (!menuOverlay) {
        return;
    }

    menuOverlay.classList.remove("aberto");

    menuOverlay.setAttribute(
        "aria-hidden",
        "true"
    );

    document.body.classList.remove(
        "menu-mobile-aberto"
    );


    if (botaoMenu) {

        botaoMenu.setAttribute(
            "aria-expanded",
            "false"
        );

        botaoMenu.setAttribute(
            "aria-label",
            "Abrir menu"
        );

    }

}


/* Abrir */

botaoMenu?.addEventListener(
    "click",
    (evento) => {

        evento.preventDefault();

        evento.stopPropagation();

        const aberto =
            menuOverlay?.classList.contains(
                "aberto"
            );

        if (aberto) {

            fecharMenuMobile();

        } else {

            abrirMenuMobile();

        }

    }
);


/* Fechar */

botaoFecharMenu?.addEventListener(
    "click",
    (evento) => {

        evento.preventDefault();

        evento.stopPropagation();

        fecharMenuMobile();

    }
);


/*
   Clicar fora do painel fecha o menu.

   Como o overlay está por cima de toda a página,
   o usuário não consegue interagir com nada
   atrás dele.
*/

menuOverlay?.addEventListener(
    "click",
    (evento) => {

        if (
            evento.target === menuOverlay
        ) {

            fecharMenuMobile();

        }

    }
);


/*
   Impede que cliques no painel
   cheguem ao overlay.
*/

painelMenu?.addEventListener(
    "click",
    (evento) => {

        evento.stopPropagation();

    }
);


/*
   ESC fecha o menu.
*/

document.addEventListener(
    "keydown",
    (evento) => {

        if (
            evento.key === "Escape" &&
            menuOverlay?.classList.contains("aberto")
        ) {

            fecharMenuMobile();

        }

    }
);


/*
   Se voltar para desktop enquanto
   o menu estiver aberto, fecha.
*/

window.addEventListener(
    "resize",
    () => {

        if (
            window.innerWidth > 768 &&
            menuOverlay?.classList.contains("aberto")
        ) {

            fecharMenuMobile();

        }

    }
);


/*
   Quando clicar em um link do menu,
   o menu é fechado imediatamente.
*/

document
    .querySelectorAll(
        ".menu-mobile-links a"
    )
    .forEach(
        (link) => {

            link.addEventListener(
                "click",
                () => {

                    fecharMenuMobile();

                }
            );

        }
    );


/* =========================================================
   FOOTER
   ========================================================= */

const rodape = `

<div class="footer-logo">

    <div class="logo-footer">

        <img
            class="img"
            src="../assets/logo-night.png"
            alt="Logo Porto Ajuda"
        >

        <div class="logo-name">
            Porto Ajuda
        </div>

    </div>

</div>


<div class="footer-links">

    <button
        class="open-modal style-open-modal"
        data-modal="modal-lgpd"
        type="button"
    >
        Política De Privacidade
    </button>


    <button
        class="open-modal style-open-modal"
        data-modal="modal-termos"
        type="button"
    >
        Termos de Uso
    </button>


    <a href="contato.html">
        Contato
    </a>

</div>

`;


document
    .querySelector("#footer")
    ?.insertAdjacentHTML(
        "beforeend",
        rodape
    );


/* =========================================================
   ACESSIBILIDADE
   ========================================================= */

const acessibilidadeHTML = `

<div id="acessibilidade">

    <button
        id="botao-acessibilidade"
        type="button"
    >
        🚹
    </button>


    <div id="painel-acessibilidade">

        <div class="acessibilidade-cabecalho">

            <h2>
                🚹 Acessibilidade
            </h2>

            <button
                id="fechar-acessibilidade"
                type="button"
            >
                ×
            </button>

        </div>


        <div class="acessibilidade-conteudo">

            <h3>
                Tamanho da fonte
            </h3>


            <div class="controle-fonte">

                <button
                    id="diminuir-fonte"
                    type="button"
                >
                    −
                </button>


                <strong id="tamanho-fonte">
                    100%
                </strong>


                <button
                    id="aumentar-fonte"
                    type="button"
                >
                    +
                </button>


                <button
                    id="resetar-fonte"
                    type="button"
                >
                    ↻
                </button>

            </div>


            <h3>
                Tema
            </h3>


            <button
                id="alternar-tema"
                class="opcao-acessibilidade"
                type="button"
            >
                ☀️ Modo Claro
            </button>


            <h3>
                Contraste
            </h3>


            <button
                id="alto-contraste"
                class="opcao-acessibilidade"
                type="button"
            >
                ◐ Alto contraste
            </button>


            <h3>
                Libras
            </h3>


            <button
                id="ativar-libras"
                class="opcao-acessibilidade"
                type="button"
            >
                🤟 Ativar Libras
            </button>

        </div>

    </div>

</div>

`;


document.body.insertAdjacentHTML(
    "beforeend",
    acessibilidadeHTML
);


/* =========================================================
   CSS ACESSIBILIDADE
   ========================================================= */

const cssAcessibilidade =
    document.createElement("link");

cssAcessibilidade.rel =
    "stylesheet";

cssAcessibilidade.href =
    "../styles/acessibilidade.css";

document.head.appendChild(
    cssAcessibilidade
);


/* =========================================================
   JS ACESSIBILIDADE
   ========================================================= */

const jsAcessibilidade =
    document.createElement("script");

jsAcessibilidade.src =
    "../components/acessibilidade.js";

document.body.appendChild(
    jsAcessibilidade
);


/* =========================================================
   VLibras
   ========================================================= */

window.carregarVLibras =
    function () {

        return new Promise(
            (resolve, reject) => {

                /*
                   Se já estiver carregado,
                   esconde o botão oficial.
                */

                if (
                    window.VLibrasWidget?.initBtn
                ) {

                    const botaoVLibras =
                        window.VLibrasWidget.initBtn;

                    esconderBotaoVLibras(
                        botaoVLibras
                    );

                    resolve(
                        botaoVLibras
                    );

                    return;
                }


                /*
                   Procura o script existente.
                */

                let script =
                    document.querySelector(
                        'script[data-vlibras="true"]'
                    );


                /*
                   Se não existir,
                   cria o script.
                */

                if (!script) {

                    script =
                        document.createElement(
                            "script"
                        );

                    script.src =
                        "https://vlibras.gov.br/app/vlibras-plugin.js";

                    script.dataset.vlibras =
                        "true";

                    document.body.appendChild(
                        script
                    );

                }


                /*
                   Aguarda o VLibras carregar.
                */

                let tentativas = 0;

                const verificar =
                    setInterval(
                        () => {

                            tentativas++;


                            if (
                                window
                                    .VLibrasWidget
                                    ?.initBtn
                            ) {

                                clearInterval(
                                    verificar
                                );


                                const botaoVLibras =
                                    window
                                        .VLibrasWidget
                                        .initBtn;


                                esconderBotaoVLibras(
                                    botaoVLibras
                                );


                                resolver(
                                    botaoVLibras
                                );

                            }


                            /*
                               10 segundos
                               aproximadamente.
                            */

                            if (
                                tentativas >= 100
                            ) {

                                clearInterval(
                                    verificar
                                );

                                reject(
                                    new Error(
                                        "VLibras não foi inicializado."
                                    )
                                );

                            }

                        },
                        100
                    );

            }
        );

    };

/* =========================================================
FECHAMENTO DO VLibras
========================================================= */

window.addEventListener(
    "vp-widget-close",
    () => {

        const dados =
            localStorage.getItem(
                "portoAjudaPreferencias"
            );

        if (!dados) {
            return;
        }

        try {

            const preferencias =
                JSON.parse(dados);

            preferencias.libras = false;

            localStorage.setItem(
                "portoAjudaPreferencias",
                JSON.stringify(preferencias)
            );

            console.log(
                "VLibras fechado. libras = false"
            );

        } catch (erro) {

            console.error(
                "Erro ao salvar preferência do VLibras:",
                erro
            );

        }

    }
);


/* =========================================================
   ESCONDER BOTÃO OFICIAL DO VLibras
   ========================================================= */

function esconderBotaoVLibras(
    botaoVLibras
) {

    if (!botaoVLibras) {
        return;
    }

    botaoVLibras.style.display =
        "none";

    botaoVLibras.style.visibility =
        "hidden";

    botaoVLibras.style.opacity =
        "0";

    botaoVLibras.style.pointerEvents =
        "none";

    botaoVLibras.style.zIndex =
        "-1";

}


/* =========================================================
   PROCURAR POPUP DO VLibras
   ========================================================= */

function procurarElementoRecursivo(
    elemento,
    id
) {

    if (!elemento) {
        return null;
    }


    /*
       Elemento normal.
    */

    if (elemento.id === id) {
        return elemento;
    }


    /*
       Shadow DOM.
    */

    if (elemento.shadowRoot) {

        const encontrado =
            procurarElementoRecursivo(
                elemento.shadowRoot,
                id
            );

        if (encontrado) {
            return encontrado;
        }

    }


    /*
       Filhos.
    */

    if (elemento.children) {

        for (
            const filho
            of elemento.children
        ) {

            const encontrado =
                procurarElementoRecursivo(
                    filho,
                    id
                );

            if (encontrado) {
                return encontrado;
            }

        }

    }

    return null;
}


/* =========================================================
   ESCONDER POPUP VLibras
   ========================================================= */

function esconderPopupVLibras() {

    const popup =
        procurarElementoRecursivo(
            document.documentElement,
            "vlibras-popup"
        );


    if (!popup) {
        return;
    }


    popup.style.display =
        "none";

    popup.style.visibility =
        "hidden";

    popup.style.opacity =
        "0";

    popup.style.pointerEvents =
        "none";

    popup.style.zIndex =
        "-1";

}

let vlibrasEstavaAberto = false;

/* =========================================================
   OBSERVADOR DO VLibras
   ========================================================= */

/*
   O VLibras pode recriar elementos depois de
   carregado. Por isso usamos um MutationObserver.

   Assim, mesmo que o botão/popup apareça
   posteriormente, ele será escondido.
*/

const observadorVLibras =
    new MutationObserver(
        () => {

            if (
                window
                    .VLibrasWidget
                    ?.initBtn
            ) {

                esconderBotaoVLibras(
                    window
                        .VLibrasWidget
                        .initBtn
                );

            }

            esconderPopupVLibras();

        }
    );


observadorVLibras.observe(
    document.documentElement,
    {
        childList: true,
        subtree: true
    }
);


/* =========================================================
   AJUSTE DA ACESSIBILIDADE EM RELAÇÃO AO FOOTER
   ========================================================= */

function ajustarAcessibilidadeFooter() {

    const acessibilidade =
        document.querySelector(
            "#acessibilidade"
        );

    const footer =
        document.querySelector(
            "#footer"
        );


    if (
        !acessibilidade ||
        !footer
    ) {

        return;

    }


    const footerRect =
        footer.getBoundingClientRect();


    const alturaBotao =
        acessibilidade.offsetHeight;


    const margem = 20;


    const limiteInferior =
        window.innerHeight - margem;


    if (
        footerRect.top <
        limiteInferior - alturaBotao
    ) {

        const distanciaFooter =
            window.innerHeight -
            footerRect.top;


        acessibilidade.style.bottom =
            `${distanciaFooter + margem}px`;

    } else {

        acessibilidade.style.bottom =
            `${margem}px`;

    }

}


/* =========================================================
   EVENTOS FOOTER / ACESSIBILIDADE
   ========================================================= */

window.addEventListener(
    "scroll",
    ajustarAcessibilidadeFooter
);


window.addEventListener(
    "resize",
    ajustarAcessibilidadeFooter
);


window.addEventListener(
    "load",
    ajustarAcessibilidadeFooter
);


ajustarAcessibilidadeFooter();

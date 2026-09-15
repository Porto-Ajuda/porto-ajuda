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

// Modal fica fora da navbar, direto no body
document.body.insertAdjacentHTML(
    "afterbegin",
    `<div id="modal-termos-politica"></div>`
);

const cabecalho = `        
<a href="Porto-Ajuda.html">
    <div class="logo">
    
      <div class="logo-circle">
        <img class="logo-circle-img" src="../assets/logo-night.png">
      </div>

      <span >Porto Ajuda</span>

    </div>
    </a>

    <nav>

      <ul class="nav-links">

      <li><a href="Porto-Ajuda.html">Home</a></li>
<li id="barra">|</li>
      <li><a href="sobre.html">Sobre</a></li>
<li id="barra">|</li>
      <li><a href="#">Ajuda</a></li>
<li id="barra">|</li>
      <li><a href="#">Apoiar o Site</a></li>
<li id="barra">|</li>
      <li><a href="proximity.html">ONG's Regionais</a></li>
      </ul>

    </nav>

  <a href="login.html">
    <div class="profile-icon">
      <i class="fa-regular fa-user"></i>
    </div>
  </a>
  `;

document.querySelector(".navbar")?.insertAdjacentHTML("beforeend", cabecalho);

const rodape = `
    <div class="footer-logo">
      <div class="logo-footer">
        <img class="img" src="../assets/logo-night.png">
        <div class="logo-name">Porto Ajuda</div>
      </div>
    </div>

    <div class="footer-links">

      <button class="open-modal style-open-modal" data-modal="modal-lgpd" type="button">
        Política De Privacidade
      </button>
      <button class="open-modal style-open-modal" data-modal="modal-termos" type="button">
        Termos de Uso
      </button>
      <a href="contato.html">Contato</a>

    </div>
  `;

document.querySelector("#footer")?.insertAdjacentHTML("beforeend", rodape);

// ==========================
// ACESSIBILIDADE
// ==========================

const acessibilidadeHTML = `
    <div id="acessibilidade">

        <button id="botao-acessibilidade" type="button">
            ♿
        </button>

        <div id="painel-acessibilidade">

            <div class="acessibilidade-cabecalho">

                <h2>♿ Acessibilidade</h2>

                <button id="fechar-acessibilidade" type="button">
                    ×
                </button>

            </div>

            <div class="acessibilidade-conteudo">

                <h3>Tamanho da fonte</h3>

                <div class="controle-fonte">

                    <button id="diminuir-fonte" type="button">
                        −
                    </button>

                    <strong id="tamanho-fonte">
                        100%
                    </strong>

                    <button id="aumentar-fonte" type="button">
                        +
                    </button>

                    <button id="resetar-fonte" type="button">
                        ↻
                    </button>

                </div>

                <h3>Tema</h3>

                <button id="alternar-tema"
                        class="opcao-acessibilidade"
                        type="button">
                    ☀️ Modo Claro
                </button>

                <h3>Contraste</h3>

                <button id="alto-contraste"
                        class="opcao-acessibilidade"
                        type="button">
                    ◐ Alto contraste
                </button>

                <h3>Libras</h3>

                <button id="ativar-libras"
                        class="opcao-acessibilidade"
                        type="button">
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

const cssAcessibilidade = document.createElement("link");

cssAcessibilidade.rel = "stylesheet";
cssAcessibilidade.href = "../styles/acessibilidade.css";

document.head.appendChild(cssAcessibilidade);

const jsAcessibilidade = document.createElement("script");

jsAcessibilidade.src = "../components/acessibilidade.js";

document.body.appendChild(jsAcessibilidade);

window.carregarVLibras = function () {

    return new Promise((resolve, reject) => {

        // Se o VLibras já estiver carregado
        if (window.VLibrasWidget?.initBtn) {

            window.VLibrasWidget.initBtn.style.display = "none";

            resolve(window.VLibrasWidget.initBtn);

            return;
        }


        // Procura o script do VLibras
        let script = document.querySelector(
            'script[data-vlibras="true"]'
        );


        // Se ainda não existir, cria o script
        if (!script) {

            script = document.createElement("script");

            script.src =
                "https://vlibras.gov.br/app/vlibras-plugin.js";

            script.dataset.vlibras = "true";

            document.body.appendChild(script);
        }


        // Espera o VLibras terminar de carregar
        const verificar = setInterval(() => {

            if (window.VLibrasWidget?.initBtn) {

                clearInterval(verificar);

                const botaoVLibras =
                    window.VLibrasWidget.initBtn;


                // Esconde o botão oficial
                botaoVLibras.style.display = "none";


                // Mantém o botão oficial invisível
                botaoVLibras.style.visibility = "hidden";
                botaoVLibras.style.opacity = "0";
                botaoVLibras.style.zIndex = "-1";


                resolve(botaoVLibras);
            }

        }, 100);


        // Se demorar mais de 10 segundos
        setTimeout(() => {

            clearInterval(verificar);

            if (!window.VLibrasWidget?.initBtn) {

                reject(
                    new Error(
                        "VLibras não foi inicializado."
                    )
                );

            }

        }, 10000);

    });

};

function esconderPopupVLibras() {

    function procurarElemento(elemento) {

        if (!elemento) {
            return null;
        }

        // Procura o elemento
        if (elemento.id === "vlibras-popup") {
            return elemento;
        }

        // Procura dentro do Shadow DOM
        if (elemento.shadowRoot) {

            const encontrado =
                procurarElemento(elemento.shadowRoot);

            if (encontrado) {
                return encontrado;
            }
        }

        // Procura nos elementos filhos
        for (const filho of elemento.children) {

            const encontrado =
                procurarElemento(filho);

            if (encontrado) {
                return encontrado;
            }
        }

        return null;
    }


    const popup =
        procurarElemento(document.documentElement);


    if (popup) {

        popup.style.zIndex = "-1";
        popup.style.position = "relative";

        console.log("Popup do VLibras encontrado.");

    } else {

        console.log("Popup do VLibras ainda não encontrado.");

    }
}

if (window.VLibrasWidget?.initBtn) {

    clearInterval(verificar);

    window.VLibrasWidget.initBtn.style.display = "none";

    esconderPopupVLibras();

    resolve(window.VLibrasWidget.initBtn);
}

function esconderPopupVLibras() {

    function procurarElemento(elemento) {

        if (!elemento) {
            return null;
        }


        // Encontrou o popup
        if (elemento.id === "vlibras-popup") {
            return elemento;
        }


        // Procura dentro do Shadow DOM
        if (elemento.shadowRoot) {

            const encontrado =
                procurarElemento(elemento.shadowRoot);

            if (encontrado) {
                return encontrado;
            }
        }


        // Procura nos filhos
        if (elemento.children) {

            for (const filho of elemento.children) {

                const encontrado =
                    procurarElemento(filho);

                if (encontrado) {
                    return encontrado;
                }
            }
        }

        return null;
    }


    const popup =
        procurarElemento(document.documentElement);


    if (popup) {

        // Esconde completamente o botão oficial
        popup.style.display = "none";
        popup.style.visibility = "hidden";
        popup.style.opacity = "0";
        popup.style.pointerEvents = "none";

        console.log(
            "Botão oficial do VLibras escondido."
        );

    }

}

function ajustarAcessibilidadeFooter() {

    const acessibilidade =
        document.querySelector("#acessibilidade");

    const footer =
        document.querySelector("#footer");

    if (!acessibilidade || !footer) {
        return;
    }

    const footerRect =
        footer.getBoundingClientRect();

    const alturaBotao =
        acessibilidade.offsetHeight;

    const margem = 20;

    const limiteInferior =
        window.innerHeight - margem;

    // Footer está chegando no botão
    if (footerRect.top < limiteInferior - alturaBotao) {

        const distanciaFooter =
            window.innerHeight - footerRect.top;

        acessibilidade.style.bottom =
            `${distanciaFooter + margem}px`;

    } else {

        // Posição normal
        acessibilidade.style.bottom =
            `${margem}px`;
    }
}

window.addEventListener(
    "scroll",
    ajustarAcessibilidadeFooter
);

window.addEventListener(
    "resize",
    ajustarAcessibilidadeFooter
);

ajustarAcessibilidadeFooter();
/* =========================================================
   VITRINE — carrossel horizontal da home
   Arquivo: public/components/vitrine.js

   - criarCardVitrine(): monta UM card padrão (serve para ONG,
     publicação, voluntariado e doação).
   - iniciarCarrossel(): busca até 6 itens, desenha os cards e
     liga as setas (que só aparecem com o mouse por perto).
   ========================================================= */

import { urlImagemSegura } from "./ong-content.js";

export const VITRINE_MAX_ITENS = 6;
const DISTANCIA_SETAS_PX = 200;

function criarElemento(tag, classe, texto) {
    const elemento = document.createElement(tag);
    if (classe) elemento.className = classe;
    if (texto != null) elemento.textContent = texto;
    return elemento;
}

function criarIcone(classes) {
    const icone = document.createElement("i");
    icone.className = classes;
    icone.setAttribute("aria-hidden", "true");
    return icone;
}

/* ---------------------------------------------------------
   CARD
   --------------------------------------------------------- */

/**
 * @param {object}   o
 * @param {string}   [o.imagem]          URL da imagem de fundo
 * @param {string}   [o.imagemFallback]  imagem usada se a principal falhar
 * @param {string}   [o.icone]           classe do Font Awesome (ex.: "fa-leaf")
 * @param {string}   o.categoria         texto ao lado do ícone
 * @param {string}   o.titulo            nome grande
 * @param {string}   [o.local]           "Cidade - UF"
 * @param {string}   o.cta               texto do link no hover (sem a seta)
 * @param {string}   [o.href]            se existir, o card é um link
 * @param {Function} [o.aoClicar]        se não houver href, o card é um botão
 * @param {string}   o.rotulo            texto lido por leitores de tela
 */
export function criarCardVitrine({
    imagem,
    imagemFallback,
    icone = "fa-circle-info",
    categoria,
    titulo,
    local = "",
    cta,
    href,
    aoClicar,
    rotulo
}) {
    const item = criarElemento("div", "vitrine-item");
    item.setAttribute("role", "listitem");

    const card = href ? criarElemento("a", "vitrine-card") : criarElemento("button", "vitrine-card");
    if (href) {
        card.href = href;
    } else {
        card.type = "button";
        card.setAttribute("aria-haspopup", "dialog");
    }
    card.setAttribute("aria-label", rotulo);

    // imagem de fundo
    if (imagem && urlImagemSegura(imagem)) {
        const img = document.createElement("img");
        img.className = "vitrine-card-img";
        img.alt = "";
        img.loading = "lazy";
        img.decoding = "async";
        img.referrerPolicy = "no-referrer";
        img.src = imagem;
        let tentouFallback = false;
        img.addEventListener("error", () => {
            if (imagemFallback && !tentouFallback) {
                tentouFallback = true;
                img.src = imagemFallback;
            } else {
                img.remove();
            }
        });
        card.appendChild(img);
    } else if (imagemFallback) {
        const img = document.createElement("img");
        img.className = "vitrine-card-img";
        img.alt = "";
        img.loading = "lazy";
        img.src = imagemFallback;
        card.appendChild(img);
    }

    // "vidro"
    const vidro = criarElemento("div", "vitrine-vidro");

    const topo = criarElemento("span", "vitrine-topo");
    const iconeCategoria = criarElemento("span", "vitrine-cat-icone");
    iconeCategoria.appendChild(criarIcone(`fa-solid ${icone}`));
    topo.append(iconeCategoria, criarElemento("span", "vitrine-cat-nome", categoria));
    if (local) {
        const separador = criarElemento("span", "vitrine-sep", "|");
        separador.setAttribute("aria-hidden", "true");
        const localTopo = criarElemento("span", "vitrine-local-topo", local);
        localTopo.setAttribute("aria-hidden", "true");
        topo.append(separador, localTopo);
    }

    const nome = criarElemento("strong", "vitrine-nome", titulo);
    nome.title = titulo;

    const base = criarElemento("span", "vitrine-base");
    if (local) base.appendChild(criarElemento("span", "vitrine-local-base", local));
    const chamada = criarElemento("span", "vitrine-cta", `${cta} `);
    chamada.appendChild(criarIcone("fa-solid fa-arrow-right"));
    base.appendChild(chamada);

    vidro.append(topo, nome, base);
    card.appendChild(vidro);

    if (!href && typeof aoClicar === "function") {
        card.addEventListener("click", () => aoClicar(card));
    }

    item.appendChild(card);
    return item;
}


/* ---------------------------------------------------------
   AJUSTE DO NOME EM UMA LINHA
   Mede quanto o texto ocupa (em "em") e grava em --em.
   O CSS usa esse valor para diminuir a fonte até caber.
   --------------------------------------------------------- */

let medidor = null;

function obterMedidor(referencia) {
    if (!medidor) {
        medidor = document.createElement("span");
        medidor.setAttribute("aria-hidden", "true");
        medidor.style.cssText =
            "position:absolute;left:-9999px;top:0;visibility:hidden;white-space:nowrap;" +
            "font-size:100px;text-transform:uppercase;letter-spacing:0;";
        document.body.appendChild(medidor);
    }
    const estilo = getComputedStyle(referencia);
    medidor.style.fontFamily = estilo.fontFamily;
    medidor.style.fontWeight = estilo.fontWeight;
    return medidor;
}

function ajustarNomes(trilho) {
    const nomes = trilho.querySelectorAll(".vitrine-nome");
    if (!nomes.length) return;
    const ref = obterMedidor(nomes[0]);
    nomes.forEach(nome => {
        ref.textContent = nome.textContent;
        const emLargura = ref.getBoundingClientRect().width / 100;
        // 3% de folga para não encostar na borda
        nome.style.setProperty("--em", (emLargura * 1.03).toFixed(3));
    });
}

if (document.fonts?.addEventListener) {
    document.fonts.addEventListener("loadingdone", () => {
        document.querySelectorAll(".vitrine-trilho").forEach(ajustarNomes);
    });
}


/* ---------------------------------------------------------
   SETAS (aparecem quando o mouse chega a 60px delas)
   --------------------------------------------------------- */

const carrosseis = new Set();
const carrosseisVisiveis = new Set();
let observador = null;
let escutandoMouse = false;
let quadroPendente = false;
let ultimoPonto = null;

function distanciaAteCaixa(caixa, x, y) {
    const dx = Math.max(caixa.left - x, 0, x - caixa.right);
    const dy = Math.max(caixa.top - y, 0, y - caixa.bottom);
    return Math.hypot(dx, dy);
}

function atualizarProximidade() {
    quadroPendente = false;
    const alvo = observador ? carrosseisVisiveis : carrosseis;
    alvo.forEach(c => {
        [c.esquerda, c.direita].forEach(seta => {
            let visivel = false;
            if (ultimoPonto && !seta.disabled) {
                visivel = distanciaAteCaixa(seta.getBoundingClientRect(), ultimoPonto.x, ultimoPonto.y) <= DISTANCIA_SETAS_PX;
            }
            seta.classList.toggle("vitrine-seta-visivel", visivel);
        });
    });
}

function agendarProximidade() {
    if (quadroPendente) return;
    quadroPendente = true;
    requestAnimationFrame(atualizarProximidade);
}

function escutarMouse() {
    if (escutandoMouse) return;
    escutandoMouse = true;

    document.addEventListener("pointermove", evento => {
        if (evento.pointerType !== "mouse") return;
        ultimoPonto = { x: evento.clientX, y: evento.clientY };
        agendarProximidade();
    }, { passive: true });

    document.documentElement.addEventListener("mouseleave", () => {
        ultimoPonto = null;
        agendarProximidade();
    });

    if ("IntersectionObserver" in window) {
        observador = new IntersectionObserver(entradas => {
            entradas.forEach(e => {
                const c = [...carrosseis].find(x => x.vitrine === e.target);
                if (!c) return;
                if (e.isIntersecting) carrosseisVisiveis.add(c);
                else carrosseisVisiveis.delete(c);
            });
        });
        carrosseis.forEach(c => observador.observe(c.vitrine));
    }
}

function criarSeta(lado) {
    const seta = criarElemento("button", `vitrine-seta vitrine-seta-${lado === "esq" ? "esq" : "dir"}`);
    seta.type = "button";
    seta.setAttribute("aria-label", lado === "esq" ? "Itens anteriores" : "Próximos itens");
    seta.appendChild(criarIcone(`fa-solid ${lado === "esq" ? "fa-chevron-left" : "fa-chevron-right"}`));
    seta.disabled = true;
    return seta;
}

function montarSetas(vitrine, viewport, trilho) {
    const esquerda = criarSeta("esq");
    const direita = criarSeta("dir");
    vitrine.append(esquerda, direita);

    const registro = { vitrine, esquerda, direita };
    carrosseis.add(registro);
    escutarMouse();
    observador?.observe(vitrine);

    function atualizarEstado() {
        const maximo = viewport.scrollWidth - viewport.clientWidth;
        esquerda.disabled = viewport.scrollLeft <= 2;
        direita.disabled = viewport.scrollLeft >= maximo - 2;
        agendarProximidade();
    }

    function rolar(direcao) {
        const primeiro = trilho.querySelector(".vitrine-item");
        const gap = parseFloat(getComputedStyle(trilho).columnGap) || 20;
        const passo = primeiro ? (primeiro.offsetWidth + gap) * 2 : viewport.clientWidth * 0.7;
        const reduzido = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        viewport.scrollBy({ left: direcao * passo, behavior: reduzido ? "auto" : "smooth" });
    }

    esquerda.addEventListener("click", () => rolar(-1));
    direita.addEventListener("click", () => rolar(1));

    let quadro = false;
    viewport.addEventListener("scroll", () => {
        if (quadro) return;
        quadro = true;
        requestAnimationFrame(() => { quadro = false; atualizarEstado(); });
    }, { passive: true });
    window.addEventListener("resize", atualizarEstado);

    return atualizarEstado;
}


/* ---------------------------------------------------------
   CARROSSEL
   --------------------------------------------------------- */

/**
 * @param {object}   o
 * @param {string}   o.trilhoId           id do .vitrine-trilho
 * @param {string}   o.statusId           id do <p role="status">
 * @param {string}   o.tentarNovamenteId  id do botão "Tentar novamente"
 * @param {string}   o.rotulo             ex.: "ONGs" (usado nas mensagens)
 * @param {Function} o.carregar           async () => array de itens
 * @param {Function} o.criarCard          (item) => elemento (use criarCardVitrine)
 */
export function iniciarCarrossel({ trilhoId, statusId, tentarNovamenteId, rotulo, carregar, criarCard }) {
    const trilho = document.getElementById(trilhoId);
    const status = document.getElementById(statusId);
    const tentarNovamente = document.getElementById(tentarNovamenteId);
    const vitrine = trilho?.closest(".vitrine");
    const viewport = trilho?.closest(".vitrine-viewport");
    if (!trilho || !status || !tentarNovamente || !vitrine || !viewport) return null;

    trilho.dataset.homeShowcase = "true";
    // siglas como "ONGs" mantêm a caixa; o resto vai para minúsculas no meio da frase
    const rotuloMinusculo = /^[A-ZÀ-Ú]{2,}/.test(rotulo) ? rotulo : rotulo.toLocaleLowerCase("pt-BR");
    const atualizarSetas = montarSetas(vitrine, viewport, trilho);
    let carregando = false;

    async function iniciar() {
        if (carregando) return;
        carregando = true;
        trilho.setAttribute("aria-busy", "true");
        tentarNovamente.hidden = true;
        status.textContent = `Carregando ${rotuloMinusculo}…`;

        try {
            const itens = (await carregar()).slice(0, VITRINE_MAX_ITENS);
            if (itens.length === 0) {
                trilho.replaceChildren();
                status.textContent = `Nenhum conteúdo público disponível em ${rotuloMinusculo} no momento.`;
            } else {
                trilho.replaceChildren(...itens.map(criarCard));
                status.textContent = "";
                ajustarNomes(trilho);
                if (document.fonts?.ready) document.fonts.ready.then(() => ajustarNomes(trilho));
            }
            viewport.scrollLeft = 0;
        } catch {
            status.textContent = `Não foi possível carregar ${rotuloMinusculo}. Tente novamente.`;
            tentarNovamente.hidden = false;
        } finally {
            carregando = false;
            trilho.removeAttribute("aria-busy");
            requestAnimationFrame(atualizarSetas);
        }
    }

    tentarNovamente.addEventListener("click", () => { void iniciar(); });
    void iniciar();
    return { recarregar: iniciar };
}

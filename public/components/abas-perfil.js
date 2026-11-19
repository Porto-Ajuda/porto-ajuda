const EASE = "cubic-bezier(.22, 1, .36, 1)";
const reduzirMovimento = window.matchMedia("(prefers-reduced-motion: reduce)");
let listaAbas;
let abas = [];
let paineis = [];
let indicadorAba;
let painelAtual = null;
let versaoTroca = 0;
let inicializado = false;

function posicionarIndicador(animar = true) {
    const ativa = document.querySelector('.aba[aria-selected="true"]:not([hidden])');
    if (!ativa || !indicadorAba) return;
    indicadorAba.style.transition = animar && !reduzirMovimento.matches
        ? `transform .45s ${EASE}, width .45s ${EASE}` : "none";
    indicadorAba.style.width = `${ativa.offsetWidth * 0.7}px`;
    indicadorAba.style.transform = `translateX(${ativa.offsetLeft + ativa.offsetWidth * 0.15}px)`;
}

function trocarAba(aba) {
    const novo = document.getElementById(aba.getAttribute("aria-controls"));
    if (!novo || novo === painelAtual) return;
    abas.forEach(item => item.setAttribute("aria-selected", String(item === aba)));
    posicionarIndicador();
    const anterior = painelAtual;
    const minhaVersao = ++versaoTroca;
    painelAtual = novo;
    const mostrarNovo = () => {
        if (minhaVersao !== versaoTroca) return;
        paineis.forEach(painel => {
            if (painel !== novo) {
                painel.getAnimations?.({ subtree: true }).forEach(animacao => animacao.cancel());
                painel.hidden = true;
            }
        });
        novo.hidden = false;
        if (typeof novo.animate !== "function") return;
        if (reduzirMovimento.matches) {
            novo.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 150 });
            return;
        }
        novo.animate([
            { opacity: 0, transform: "translateY(16px) scale(.99)" },
            { opacity: 1, transform: "none" }
        ], { duration: 450, easing: EASE });
        Array.from(novo.children).forEach((filho, indice) => filho.animate([
            { opacity: 0, transform: "translateY(10px)" },
            { opacity: 1, transform: "none" }
        ], { duration: 500, easing: EASE, delay: 60 + Math.min(indice, 8) * 45, fill: "backwards" }));
    };
    if (!anterior || typeof anterior.animate !== "function") { mostrarNovo(); return; }
    const saida = anterior.animate(reduzirMovimento.matches
        ? [{ opacity: 1 }, { opacity: 0 }]
        : [{ opacity: 1, transform: "none" }, { opacity: 0, transform: "translateY(-8px) scale(.995)" }],
    { duration: reduzirMovimento.matches ? 100 : 180, easing: "ease-in", fill: "forwards" });
    saida.onfinish = mostrarNovo;
}

/** Updates availability only after a successful owner-scoped data response. */
export function definirAbaDisponivel(id, disponivel) {
    const aba = document.getElementById(id);
    const painelId = aba?.getAttribute("aria-controls");
    const painel = painelId ? document.getElementById(painelId) : null;
    if (!aba || !painel) return;
    aba.hidden = !disponivel;
    if (disponivel) return;
    const estavaSelecionada = aba.getAttribute("aria-selected") === "true";
    if (!estavaSelecionada) { painel.hidden = true; return; }
    const fallback = Array.from(document.querySelectorAll(".abas-perfil .aba:not([hidden])"))
        .find(item => item !== aba);
    if (!fallback) { painel.hidden = true; return; }
    if (inicializado) {
        trocarAba(fallback);
    } else {
        abas.forEach(item => item.setAttribute("aria-selected", String(item === fallback)));
        paineis.forEach(item => { item.hidden = item.id !== fallback.getAttribute("aria-controls"); });
        painelAtual = document.getElementById(fallback.getAttribute("aria-controls"));
    }
    if (document.activeElement === aba) fallback.focus();
}

function inicializarAbas() {
    if (inicializado) return;
    inicializado = true;
    listaAbas = document.querySelector(".abas-perfil");
    abas = Array.from(document.querySelectorAll(".aba"));
    paineis = Array.from(document.querySelectorAll(".painel-aba"));
    indicadorAba = document.querySelector(".aba-indicador");
    painelAtual = document.querySelector(".painel-aba:not([hidden])");
    abas.forEach(aba => aba.addEventListener("click", () => trocarAba(aba)));
    listaAbas?.addEventListener("keydown", evento => {
        if (!["ArrowLeft", "ArrowRight"].includes(evento.key)) return;
        const visiveis = abas.filter(aba => !aba.hidden);
        const indice = visiveis.indexOf(document.activeElement);
        if (indice === -1 || !visiveis.length) return;
        evento.preventDefault();
        const passo = evento.key === "ArrowRight" ? 1 : -1;
        const proxima = visiveis[(indice + passo + visiveis.length) % visiveis.length];
        proxima.focus();
        trocarAba(proxima);
    });
    posicionarIndicador(false);
    window.addEventListener("resize", () => posicionarIndicador(false));
    document.fonts?.ready.then(() => posicionarIndicador(false));
}

if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", inicializarAbas, { once: true });
else inicializarAbas();

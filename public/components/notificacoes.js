const ICONES = Object.freeze({ sucesso: "✓", erro: "×", aviso: "!", informacao: "i" });
const TIPOS = new Set(Object.keys(ICONES));

function obterRegiao() {
    let region = document.getElementById("porto-ajuda-toast-region");
    if (region) return region;
    region = document.createElement("div");
    region.id = "porto-ajuda-toast-region";
    region.className = "toast-region";
    region.setAttribute("role", "region");
    region.setAttribute("aria-label", "Notificações");
    region.setAttribute("aria-relevant", "additions");
    document.body.append(region);
    return region;
}

export function notificarToast(mensagem, { tipo = "informacao", duracao = 5000 } = {}) {
    if (typeof mensagem !== "string" || !mensagem.trim()) return null;
    if (tipo === "success") tipo = "sucesso";
    if (tipo === "error") tipo = "erro";
    if (tipo === "warning") tipo = "aviso";
    if (tipo === "info") tipo = "informacao";
    if (!TIPOS.has(tipo)) tipo = "informacao";

    const toast = document.createElement("article");
    toast._returnFocus = document.activeElement;
    toast.className = "toast-notificacao";
    toast.dataset.tipo = tipo;
    toast.setAttribute("role", tipo === "erro" ? "alert" : "status");
    toast.setAttribute("aria-live", tipo === "erro" ? "assertive" : "polite");
    toast.setAttribute("aria-atomic", "true");

    const icon = document.createElement("span");
    icon.className = "toast-notificacao__icone";
    icon.setAttribute("aria-hidden", "true");
    icon.textContent = ICONES[tipo];

    const text = document.createElement("p");
    text.className = "toast-notificacao__texto";
    text.textContent = mensagem.trim();

    const close = document.createElement("button");
    close.type = "button";
    close.className = "toast-notificacao__fechar";
    close.setAttribute("aria-label", "Fechar notificação");
    close.textContent = "×";
    const dispensar = () => {
        const devolverFoco = toast.contains(document.activeElement);
        if (toast._timer) window.clearTimeout(toast._timer);
        toast.remove();
        if (devolverFoco && toast._returnFocus?.isConnected && toast._returnFocus !== document.body) {
            toast._returnFocus.focus();
        }
    };
    close.addEventListener("click", dispensar);

    toast.append(icon, text, close);
    obterRegiao().append(toast);

    if (tipo !== "erro") {
        const timeout = Number.isFinite(duracao) ? Math.max(0, duracao) : 5000;
        toast._timer = window.setTimeout(dispensar, timeout);
    }
    return toast;
}

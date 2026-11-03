const ICONES_NOTIFICACAO = {
    sucesso: "✓",
    erro: "×",
    aviso: "!",
    informacao: "i"
};

function obterToast() {
    let toast = document.getElementById("notificacao-osc");
    if (!toast) {
        toast = document.createElement("div");
        toast.id = "notificacao-osc";
        toast.className = "notificacao-osc";
        toast.setAttribute("role", "status");
        toast.setAttribute("aria-live", "polite");
        toast.hidden = true;
        document.body.append(toast);
    }
    return toast;
}

export function notificar(mensagem, tipo = "informacao", duracao = 5000) {
    const toast = obterToast();
    clearTimeout(toast._timerNotificacao);
    toast.replaceChildren();
    const icone = document.createElement("span");
    icone.className = "notificacao-osc-icone";
    icone.setAttribute("aria-hidden", "true");
    icone.textContent = ICONES_NOTIFICACAO[tipo] || ICONES_NOTIFICACAO.informacao;
    const texto = document.createElement("span");
    texto.textContent = mensagem;
    toast.append(icone, texto);
    toast.dataset.tipo = tipo;
    toast.hidden = false;
    toast._timerNotificacao = setTimeout(() => { toast.hidden = true; }, duracao);
}

function obterModal() {
    let modal = document.getElementById("modal-confirmacao-osc");
    if (modal) return modal;

    modal = document.createElement("dialog");
    modal.id = "modal-confirmacao-osc";
    modal.className = "modal-confirmacao-osc";
    modal.setAttribute("role", "alertdialog");
    modal.setAttribute("aria-labelledby", "modal-confirmacao-titulo");
    modal.setAttribute("aria-describedby", "modal-confirmacao-descricao modal-confirmacao-identidade");
    modal.innerHTML = `
      <form class="modal-confirmacao-cartao" novalidate>
        <p class="modal-confirmacao-tipo" id="modal-confirmacao-tipo"></p>
        <h2 id="modal-confirmacao-titulo"></h2>
        <p id="modal-confirmacao-descricao"></p>
        <p class="modal-confirmacao-identidade" id="modal-confirmacao-identidade"></p>
        <label class="modal-confirmacao-instrucao" id="modal-confirmacao-instrucao" for="modal-confirmacao-texto">Digite exatamente:</label>
        <code class="modal-confirmacao-frase" id="modal-confirmacao-frase"></code>
        <input id="modal-confirmacao-texto" type="text" autocomplete="off" spellcheck="false" aria-describedby="modal-confirmacao-frase modal-confirmacao-erro">
        <p class="modal-confirmacao-erro" id="modal-confirmacao-erro" role="alert" aria-live="assertive"></p>
        <div class="modal-confirmacao-acoes">
          <button type="button" class="btn-modal-cancelar">Cancelar</button>
          <button type="submit" class="btn-modal-confirmar" disabled></button>
        </div>
      </form>`;
    document.body.append(modal);

    const form = modal.querySelector("form");
    const input = modal.querySelector("#modal-confirmacao-texto");
    const confirmar = modal.querySelector(".btn-modal-confirmar");
    const cancelar = modal.querySelector(".btn-modal-cancelar");
    let ocupado = false;
    function limpar() {
        input.value = "";
        input.setCustomValidity("");
        confirmar.disabled = true;
        modal.querySelector("#modal-confirmacao-erro").textContent = "";
        modal.querySelector("#modal-confirmacao-tipo").textContent = "";
        modal.dataset.tipo = "";
        ocupado = false;
        input.disabled = false;
        cancelar.disabled = false;
        confirmar.textContent = "";
    }

    function fechar() {
        if (ocupado) return;
        modal.close();
        limpar();
        if (modal._origem?.isConnected) modal._origem.focus();
        modal._resolver?.(false);
    }

    input.addEventListener("input", () => {
        confirmar.disabled = input.value.trim().toLocaleLowerCase("pt-BR") !== modal._frase.trim().toLocaleLowerCase("pt-BR") || ocupado;
        modal.querySelector("#modal-confirmacao-erro").textContent = "";
    });
    cancelar.addEventListener("click", fechar);
    modal.addEventListener("click", (evento) => {
        if (evento.target === modal) fechar();
    });
    modal.addEventListener("keydown", (evento) => {
        if (evento.key !== "Escape") return;
        evento.preventDefault();
        fechar();
    }, true);
    modal.addEventListener("cancel", (evento) => {
        if (ocupado) evento.preventDefault();
        else {
            evento.preventDefault();
            fechar();
        }
    });
    form.addEventListener("submit", async (evento) => {
        evento.preventDefault();
        if (ocupado || input.value.trim().toLocaleLowerCase("pt-BR") !== modal._frase.trim().toLocaleLowerCase("pt-BR")) return;
        ocupado = true;
        input.disabled = true;
        cancelar.disabled = true;
        confirmar.disabled = true;
        confirmar.textContent = "Processando…";
        modal.setAttribute("aria-busy", "true");
        try {
            await modal._confirmar();
            modal.close();
            limpar();
            notificar(modal._sucesso || "Operação concluída com sucesso.", "sucesso");
            if (modal._origem?.isConnected) modal._origem.focus();
            modal._resolver?.(true);
        } catch (erro) {
            ocupado = false;
            input.disabled = false;
            cancelar.disabled = false;
            confirmar.disabled = false;
            confirmar.textContent = modal._confirmarTexto;
            modal.querySelector("#modal-confirmacao-erro").textContent = erro?.message || "Não foi possível concluir a operação. Tente novamente.";
            notificar(erro?.message || "Não foi possível concluir a operação.", "erro");
            input.focus();
        } finally {
            modal.removeAttribute("aria-busy");
        }
    });
    modal._limpar = limpar;
    return modal;
}

export function confirmarPorDigitacao(configuracao) {
    const modal = obterModal();
    if (modal.open) return Promise.resolve(false);
    const tipo = configuracao.tipo || "confirmacao";
    const input = modal.querySelector("#modal-confirmacao-texto");
    const identidade = modal.querySelector("#modal-confirmacao-identidade");
    modal.querySelector("#modal-confirmacao-titulo").textContent = configuracao.titulo;
    modal.querySelector("#modal-confirmacao-descricao").textContent = configuracao.descricao;
    identidade.textContent = configuracao.identificacao || "";
    identidade.hidden = !configuracao.identificacao;
    modal.querySelector("#modal-confirmacao-instrucao").textContent = configuracao.instrucao || "Digite exatamente:";
    modal.querySelector("#modal-confirmacao-frase").textContent = configuracao.frase;
    modal.querySelector("#modal-confirmacao-tipo").textContent = tipo;
    modal.querySelector(".btn-modal-confirmar").textContent = configuracao.textoConfirmar;
    modal.dataset.tipo = configuracao.estilo || "padrao";
    modal._confirmarTexto = configuracao.textoConfirmar;
    modal._confirmar = configuracao.aoConfirmar;
    modal._sucesso = configuracao.mensagemSucesso;
    modal._frase = configuracao.frase;
    modal._limpar();
    modal.querySelector(".btn-modal-confirmar").textContent = configuracao.textoConfirmar;
    modal._origem = configuracao.origem || document.activeElement;
    input.value = "";
    return new Promise((resolve) => {
        modal._resolver = resolve;
        modal.showModal();
        input.focus();
    });
}

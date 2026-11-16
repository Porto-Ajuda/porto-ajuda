import { fetchAutenticado } from "./api.js";

function criarDialogo() {
    let dialogo = document.getElementById("dialogo-denuncia-conteudo");
    if (dialogo) return dialogo;
    dialogo = document.createElement("dialog");
    dialogo.id = "dialogo-denuncia-conteudo";
    dialogo.className = "dialogo-denuncia-conteudo";
    dialogo.setAttribute("aria-labelledby", "titulo-denuncia-conteudo");
    dialogo.innerHTML = `
      <form class="dialogo-denuncia-conteudo__cartao" novalidate>
        <button type="button" class="dialogo-denuncia-conteudo__fechar" aria-label="Fechar">×</button>
        <p class="dialogo-denuncia-conteudo__rotulo">SEGURANÇA DA COMUNIDADE</p>
        <h2 id="titulo-denuncia-conteudo">Denunciar conteúdo</h2>
        <p>As denúncias são analisadas pela equipe. Sua identidade não é mostrada à organização.</p>
        <label for="motivo-denuncia-conteudo">Motivo</label>
        <select id="motivo-denuncia-conteudo" required aria-describedby="erro-motivo-denuncia-conteudo">
          <option value="">Selecione</option><option value="MISLEADING">Informação enganosa</option>
          <option value="INAPPROPRIATE">Conteúdo inadequado</option><option value="FRAUD">Suspeita de fraude</option>
          <option value="PRIVACY">Exposição de informação privada</option><option value="OTHER">Outro</option>
        </select>
        <p id="erro-motivo-denuncia-conteudo" class="dialogo-denuncia-conteudo__erro" role="alert" hidden></p>
        <label for="detalhes-denuncia-conteudo">Descrição do motivo</label>
        <p id="ajuda-denuncia-conteudo">Descreva o que aconteceu para ajudar na análise.</p>
        <textarea id="detalhes-denuncia-conteudo" required maxlength="1000" rows="4" aria-describedby="ajuda-denuncia-conteudo erro-detalhes-denuncia-conteudo"></textarea>
        <p id="erro-detalhes-denuncia-conteudo" class="dialogo-denuncia-conteudo__erro" role="alert" hidden></p>
        <p class="dialogo-denuncia-conteudo__estado" role="status" aria-live="polite"></p>
        <div class="dialogo-denuncia-conteudo__acoes"><button type="button" class="botao botao-secundario" data-cancelar>Voltar</button><button type="submit" class="botao botao-primario">Enviar denúncia</button></div>
      </form>`;
    document.body.append(dialogo);
    const close = () => { if (dialogo.open) dialogo.close(); };
    dialogo.querySelectorAll("[data-cancelar], .dialogo-denuncia-conteudo__fechar").forEach(button => button.addEventListener("click", close));
    dialogo.addEventListener("click", event => { if (event.target === dialogo) close(); });
    dialogo.addEventListener("submit", async event => {
        event.preventDefault();
        const submit = dialogo.querySelector('[type="submit"]');
        const status = dialogo.querySelector(".dialogo-denuncia-conteudo__estado");
        const type = dialogo.dataset.targetType;
        const id = Number(dialogo.dataset.targetId);
        const reasonField = dialogo.querySelector("#motivo-denuncia-conteudo");
        const detailsField = dialogo.querySelector("#detalhes-denuncia-conteudo");
        const errorField = dialogo.querySelector("#erro-detalhes-denuncia-conteudo");
        const reason = reasonField.value;
        const details = detailsField.value.trim();
        if (submit.disabled) return;
        const reasonError = dialogo.querySelector("#erro-motivo-denuncia-conteudo");
        if (!reason) {
            reasonField.setAttribute("aria-invalid", "true");
            reasonError.textContent = "Selecione o motivo da denúncia.";
            reasonError.hidden = false;
            reasonField.focus();
            return;
        }
        reasonField.removeAttribute("aria-invalid");
        reasonError.hidden = true;
        reasonError.textContent = "";
        if (!details || details.length > 1000) {
            detailsField.setAttribute("aria-invalid", "true");
            errorField.textContent = !details ? "Descreva o motivo da denúncia." : "A descrição deve ter no máximo 1000 caracteres.";
            errorField.hidden = false;
            detailsField.focus();
            return;
        }
        detailsField.removeAttribute("aria-invalid");
        errorField.hidden = true;
        errorField.textContent = "";
        if (!Number.isInteger(id) || id < 1) return;
        submit.disabled = true;
        submit.textContent = "Enviando…";
        status.textContent = "";
        try {
            const response = await fetchAutenticado("/denuncias", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ tipo: type, alvoId: id, motivo: reason, justificativa: details })
            });
            if (response.status === 409) {
                const payload = await response.json().catch(() => null);
                const message = payload?.detail || payload?.message;
                const knownConflict = message === "Você já possui uma denúncia pendente para este conteúdo."
                    || message === "Uma nova denúncia estará disponível 7 dias após a última análise.";
                throw new Error(knownConflict ? message : "Não foi possível enviar: há uma denúncia pendente ou um prazo de reenvio ativo.");
            }
            if (response.status === 401 || response.status === 403) throw new Error("Entre em uma conta ativa com e-mail confirmado para enviar uma denúncia.");
            if (response.status === 429) throw new Error("Você atingiu o limite temporário de denúncias. Tente novamente mais tarde.");
            if (!response.ok) throw new Error("Não foi possível enviar a denúncia. Tente novamente.");
            status.textContent = "Denúncia enviada para análise.";
            dialogo.querySelector("form").reset();
            window.setTimeout(close, 900);
        } catch (error) {
            status.textContent = error?.message || "Não foi possível enviar a denúncia.";
        } finally {
            submit.disabled = false;
            submit.textContent = "Enviar denúncia";
        }
    });
    const detailsField = dialogo.querySelector("#detalhes-denuncia-conteudo");
    detailsField.addEventListener("input", () => {
        detailsField.removeAttribute("aria-invalid");
        const errorField = dialogo.querySelector("#erro-detalhes-denuncia-conteudo");
        errorField.hidden = true;
        errorField.textContent = "";
    });
    dialogo.querySelector("#motivo-denuncia-conteudo").addEventListener("change", event => {
        event.currentTarget.removeAttribute("aria-invalid");
        const errorField = dialogo.querySelector("#erro-motivo-denuncia-conteudo");
        errorField.hidden = true;
        errorField.textContent = "";
    });
    return dialogo;
}

    document.addEventListener("click", event => {
    const button = event.target.closest("[data-content-report]");
    if (!button || button.hidden || button.disabled) return;
    event.preventDefault();
    const dialogo = criarDialogo();
    dialogo.dataset.targetType = button.dataset.reportType;
    dialogo.dataset.targetId = button.dataset.reportId;
    dialogo.querySelector("form").reset();
    dialogo.querySelector(".dialogo-denuncia-conteudo__estado").textContent = "";
    dialogo.querySelector("#motivo-denuncia-conteudo").removeAttribute("aria-invalid");
    dialogo.querySelector("#erro-motivo-denuncia-conteudo").hidden = true;
    dialogo.querySelector("#erro-motivo-denuncia-conteudo").textContent = "";
    dialogo.querySelector("#detalhes-denuncia-conteudo").removeAttribute("aria-invalid");
    dialogo.querySelector("#erro-detalhes-denuncia-conteudo").hidden = true;
    dialogo.querySelector("#erro-detalhes-denuncia-conteudo").textContent = "";
    dialogo.showModal();
    dialogo.querySelector("#motivo-denuncia-conteudo").focus();
});

import { getAPI_URL } from "./modoBack.js";
import { obterTokenCSRF } from "./auth.js";

const dialogo = document.getElementById("dialog-alterar-senha");
const botaoAbrir = document.getElementById("abrir-alterar-senha");
const formulario = document.getElementById("form-alterar-senha");
const estado = document.getElementById("estado-alterar-senha");
let focoOrigem = null;
let enviando = false;

if (dialogo && botaoAbrir && formulario && estado) {
    const fechar = () => {
        if (dialogo.open) dialogo.close();
        formulario.reset();
        estado.textContent = "";
        if (focoOrigem?.isConnected) focoOrigem.focus();
    };

    botaoAbrir.addEventListener("click", () => {
        focoOrigem = document.activeElement;
        dialogo.showModal();
        document.getElementById("senha-atual-conta").focus();
    });
    document.getElementById("fechar-dialog-alterar-senha")?.addEventListener("click", fechar);
    document.getElementById("cancelar-alterar-senha")?.addEventListener("click", fechar);
    dialogo.addEventListener("click", (event) => {
        if (event.target === dialogo) fechar();
    });
    dialogo.addEventListener("close", () => {
        if (focoOrigem?.isConnected) focoOrigem.focus();
    });
    dialogo.querySelectorAll(".alternar-visibilidade-senha").forEach((botao) => {
        botao.addEventListener("click", () => {
            const campo = document.getElementById(botao.getAttribute("aria-controls"));
            if (!campo) return;
            const mostrar = campo.type === "password";
            campo.type = mostrar ? "text" : "password";
            botao.setAttribute("aria-pressed", String(mostrar));
            botao.setAttribute("aria-label", `${mostrar ? "Ocultar" : "Mostrar"} ${campo.labels?.[0]?.textContent?.toLowerCase() || "senha"}`);
            const icone = botao.querySelector("i");
            icone?.classList.toggle("fa-eye", !mostrar);
            icone?.classList.toggle("fa-eye-slash", mostrar);
        });
    });

    formulario.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (enviando) return;
        const atual = document.getElementById("senha-atual-conta").value;
        const nova = document.getElementById("nova-senha-conta").value;
        const confirmacao = document.getElementById("confirmar-nova-senha-conta").value;
        if (nova !== confirmacao) {
            estado.textContent = "As senhas informadas não coincidem.";
            return;
        }
        if (nova.length < 8 || nova.length > 128 || !/[A-Z]/.test(nova) || !/[a-z]/.test(nova)
            || !/[0-9]/.test(nova) || !/[^A-Za-z0-9]/.test(nova)) {
            estado.textContent = "Use ao menos 8 caracteres, com maiúscula, minúscula, número e caractere especial.";
            return;
        }

        const botao = document.getElementById("confirmar-alterar-senha");
        enviando = true;
        botao.disabled = true;
        botao.setAttribute("aria-busy", "true");
        estado.textContent = "Atualizando senha...";
        try {
            const api = (await getAPI_URL()).replace(/\/+$/, "");
            const csrf = await obterTokenCSRF();
            const resposta = await fetch(`${api}/usuario/alterar-senha`, {
                method: "POST", credentials: "include", cache: "no-store",
                headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
                body: JSON.stringify({
                    senhaAtual: atual,
                    novaSenha: nova,
                    confirmarSenha: confirmacao,
                    desconectarOutrosDispositivos: document.getElementById("desconectar-outros-dispositivos").checked
                })
            });
            const dados = await resposta.json().catch(() => ({}));
            if (!resposta.ok) throw new Error(dados.mensagem || "Não foi possível alterar a senha.");
            estado.textContent = dados.mensagem || "Senha alterada. Sua sessão continua ativa.";
            formulario.reset();
        } catch (erro) {
            estado.textContent = erro.message || "Não foi possível alterar a senha. Tente novamente.";
        } finally {
            enviando = false;
            botao.disabled = false;
            botao.removeAttribute("aria-busy");
        }
    });
}

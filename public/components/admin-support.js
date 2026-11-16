import { AdminRequestError, fetchAdminModuleRequest } from "./admin-rbac.js";

export function initializeAdminSupport({ apiUrl, authorization, onUnauthorized }) {
  const status = document.getElementById("status-atendimentos-admin");
  const list = document.getElementById("lista-atendimentos-admin");
  const filter = document.getElementById("filtro-atendimentos-admin");
  const refresh = document.getElementById("atualizar-atendimentos-admin");
  const detail = document.getElementById("detalhe-atendimento-admin");
  const actions = document.getElementById("acoes-atendimento-admin");
  let currentId = null;
  let mutating = false;
  let pendingReplyBody = null;
  let pendingReplyKey = null;
  let pollTimer = null;
  let active = false;
  let eligibleStaffCache = null;
  let eligibleStaffLoadedAt = 0;
  let eligibleStaffRequest = null;

  function errorMessage(error) {
    if (error instanceof AdminRequestError && error.kind === "unauthorized") { onUnauthorized?.(); return "Sua sessão administrativa expirou."; }
    if (error instanceof AdminRequestError && error.kind === "forbidden") return "Seu perfil não tem permissão para esta ação.";
    if (error instanceof AdminRequestError && error.kind === "unavailable") return "Atendimentos temporariamente indisponíveis nesta instância.";
    if (error instanceof AdminRequestError && error.kind === "conflict") return "A conversa foi alterada por outra pessoa. Atualize e tente novamente.";
    return "Não foi possível carregar ou atualizar os atendimentos.";
  }

  async function api(path, options) {
    return fetchAdminModuleRequest(apiUrl, path, options);
  }

  async function load(page = 0, preserveDetail = false) {
    active = true;
    window.clearTimeout(pollTimer);
    if (!authorization.hasPermission("SUPPORT_READ")) { status.textContent = "Seu perfil não tem permissão para consultar atendimentos."; return; }
    status.textContent = "Carregando atendimentos…"; list.setAttribute("aria-busy", "true");
    if (!preserveDetail) list.replaceChildren();
    try {
      const params = new URLSearchParams({ state: filter.value, page: String(page), size: "20" });
      const result = await api(`/admin/support-assistant/conversations?${params}`);
      list.replaceChildren();
      if (!preserveDetail) { detail.replaceChildren(); actions.replaceChildren(); currentId = null; }
      for (const item of result.items || []) {
        const card = document.createElement("article"); card.className = "admin-report-card support-admin-card";
        const heading = document.createElement("h2");
        const stateLabel = item.state === "AI_ACTIVE" && item.unread
          ? "Pessoa pediu para continuar com a Sophie" : labelState(item.state);
        heading.textContent = stateLabel + " · " + item.id.slice(0, 8);
        const waiting = document.createElement("p"); waiting.textContent = `Estado desde ${new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(item.stateChangedAt || item.createdAt))}`;
        const unread = document.createElement("p"); unread.textContent = item.unread ? "Mensagem de usuário não lida" : "Sem mensagens novas";
        const open = document.createElement("button"); open.type = "button"; open.className = "admin-secondary-button"; open.textContent = "Abrir conversa";
        open.addEventListener("click", () => void loadDetail(item.id));
        card.append(heading, waiting, unread, open); list.append(card);
      }
      if (result.items?.length) status.textContent = `${result.items.length} atendimento(s) nesta página.`;
      else {
        const empty = document.createElement("p"); empty.className = "support-admin-empty";
        empty.textContent = "Não há conversas nesta fila."; list.append(empty);
        status.textContent = "Fila carregada.";
      }
      if(preserveDetail && currentId && !mutating && !actions.contains(document.activeElement)) await loadDetail(currentId);
    } catch (error) {
      if (!preserveDetail) list.replaceChildren();
      status.textContent = `${errorMessage(error)} Use “Atualizar fila” para tentar novamente.`;
    }
    finally {
      list.setAttribute("aria-busy", "false");
      if (active) pollTimer = window.setTimeout(() => void load(page, true), 15000);
    }
  }

  function labelState(state) {
    return ({ AI_ACTIVE: "Assistente ativo", WAITING_HUMAN: "Aguardando equipe", HUMAN_ACTIVE: "Em atendimento", CLOSED: "Encerrada" })[state] || "Atendimento";
  }

  async function loadDetail(id) {
    status.textContent = "Carregando histórico…"; detail.replaceChildren(); actions.replaceChildren();
    try {
      const conversation = await api(`/admin/support-assistant/conversations/${encodeURIComponent(id)}`);
      if(currentId!==id){pendingReplyBody=null;pendingReplyKey=null;}
      currentId = id;
      const heading = document.createElement("h2"); heading.textContent = `${labelState(conversation.state)} · ${id}`;
      const history = document.createElement("ol"); history.className = "support-admin-history";
      for (const message of conversation.messages || []) {
        const item = document.createElement("li");
        const meta = document.createElement("strong"); meta.textContent = `${message.senderLabel} · ${new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(message.createdAt))}`;
        const body = document.createElement("p"); body.textContent = message.body;
        item.append(meta, body); history.append(item);
      }
      const transitionHeading=document.createElement("h3"); transitionHeading.textContent="Histórico técnico de transições";
      const transitions=document.createElement("ol"); transitions.className="support-admin-history support-admin-transitions";
      for(const event of conversation.transitions||[]){
        const item=document.createElement("li");
        const meta=document.createElement("strong");
        const at=new Date(event.createdAt);
        meta.textContent=`${event.fromState||"Início"} → ${event.toState||"Evento"} · ${Number.isNaN(at.valueOf())?"":new Intl.DateTimeFormat("pt-BR",{dateStyle:"short",timeStyle:"short"}).format(at)}`;
        const description=document.createElement("p"); description.textContent=event.eventType;
        item.append(meta,description); transitions.append(item);
      }
      detail.replaceChildren(heading, history, transitionHeading, transitions);
      await renderActions(conversation);
      status.textContent = "Histórico carregado.";
    } catch (error) {
      const failure = document.createElement("p"); failure.className = "support-admin-error";
      failure.setAttribute("role", "alert"); failure.textContent = `${errorMessage(error)} Atualize a fila e tente novamente.`;
      detail.replaceChildren(failure); status.textContent = "Não foi possível abrir esta conversa.";
    }
  }

  function field(labelText, id, type = "textarea") {
    const wrapper = document.createElement("div"); wrapper.className = "admin-form-field support-admin-field";
    const label = document.createElement("label"); label.htmlFor = id; label.textContent = labelText;
    const input = document.createElement(type === "input" ? "input" : "textarea"); input.id = id; input.maxLength = type === "input" ? 11 : 1000;
    wrapper.append(label, input); return { wrapper, input };
  }

  function button(label, handler, style = "admin-secondary-button") {
    const element = document.createElement("button"); element.type = "button"; element.className = style; element.textContent = label;
    element.addEventListener("click", handler); return element;
  }

  async function loadEligibleStaff() {
    if (eligibleStaffCache && Date.now() - eligibleStaffLoadedAt < 30000) return eligibleStaffCache;
    if (!eligibleStaffRequest) {
      eligibleStaffRequest = api("/admin/support-assistant/eligible-staff")
        .then(rows => {
          if (!Array.isArray(rows) || rows.some(row => !Number.isSafeInteger(row?.userId)
              || typeof row?.displayName !== "string" || !row.displayName.trim())) {
            throw new Error("INVALID_STAFF_DIRECTORY");
          }
          eligibleStaffCache = rows; eligibleStaffLoadedAt = Date.now(); return rows;
        })
        .finally(() => { eligibleStaffRequest = null; });
    }
    return eligibleStaffRequest;
  }

  async function renderActions(conversation) {
    actions.replaceChildren();
    if (!authorization.hasPermission("SUPPORT_MANAGE")) return;
    if (["AI_ACTIVE", "WAITING_HUMAN"].includes(conversation.state)) {
      actions.append(button("Assumir atendimento", () => mutate("take-over", {})));
      return;
    }
    if (conversation.state !== "HUMAN_ACTIVE") return;
    const reply = document.createElement("form"); reply.className = "support-admin-reply";
    const msg = field("Resposta à pessoa usuária", "support-admin-reply-body"); msg.input.maxLength = 4000;
    if (pendingReplyBody) msg.input.value = pendingReplyBody;
    const send = document.createElement("button"); send.type = "submit"; send.className = "admin-primary-button"; send.textContent = "Enviar resposta";
    reply.append(msg.wrapper, send);
    reply.addEventListener("submit", event => {
      event.preventDefault(); const text=msg.input.value.trim(); if(!text)return;
      if(pendingReplyBody!==text){pendingReplyBody=text;pendingReplyKey=crypto.randomUUID();}
      void mutate("messages", { body: text, idempotencyKey: pendingReplyKey });
    });
    actions.append(reply);
    const reason = field("Justificativa da ação", "support-admin-action-reason"); reason.input.maxLength = 1000;
    const transferField = document.createElement("div"); transferField.className = "support-admin-transfer";
    const search = document.createElement("div"); search.className = "admin-form-field support-admin-field";
    const searchLabel = document.createElement("label"); searchLabel.htmlFor = "support-admin-staff-search"; searchLabel.textContent = "Buscar outro atendente";
    const searchInput = document.createElement("input"); searchInput.type = "search"; searchInput.id = "support-admin-staff-search"; searchInput.autocomplete = "off"; searchInput.placeholder = "Digite um nome";
    search.append(searchLabel, searchInput);
    const recipient = document.createElement("div"); recipient.className = "admin-form-field support-admin-field";
    const recipientLabel = document.createElement("label"); recipientLabel.htmlFor = "support-admin-staff-recipient"; recipientLabel.textContent = "Atendente elegível";
    const select = document.createElement("select"); select.id = "support-admin-staff-recipient"; select.required = true;
    const placeholder = document.createElement("option"); placeholder.value = ""; placeholder.textContent = "Selecione um atendente"; select.append(placeholder);
    let staffRows = [];
    try {
      staffRows = await loadEligibleStaff();
      for (const staff of staffRows) {
        const option = document.createElement("option"); option.value = String(staff.userId); option.textContent = staff.displayName;
        option.dataset.search = staff.displayName.toLocaleLowerCase("pt-BR"); select.append(option);
      }
      if (!staffRows.length) { placeholder.textContent = "Nenhum outro atendente habilitado"; select.disabled = true; }
    } catch (error) {
      const failure = document.createElement("small"); failure.className = "support-admin-directory-error";
      failure.textContent = errorMessage(error) + " Atualize a fila para tentar novamente.";
      recipient.append(failure); select.disabled = true;
    }
    searchInput.addEventListener("input", () => {
      const query = searchInput.value.trim().toLocaleLowerCase("pt-BR");
      for (const option of Array.from(select.options).slice(1)) option.hidden = Boolean(query) && !option.dataset.search.includes(query);
      if (select.selectedOptions[0]?.hidden) select.value = "";
    });
    recipient.append(recipientLabel, select);
    const transferHelp = document.createElement("p"); transferHelp.className = "support-admin-action-help";
    transferHelp.textContent = "Transfere a responsabilidade e preserva o histórico da conversa. O servidor valida a elegibilidade novamente.";
    const returnHelp = document.createElement("p"); returnHelp.className = "support-admin-action-help";
    returnHelp.textContent = "Devolve o atendimento à Sophie. Ela responderá somente a novas mensagens compatíveis; o histórico não será reenviado.";
    transferField.append(search, recipient, transferHelp);
    actions.append(reason.wrapper, transferField);
    actions.append(button("Transferir para outro atendente", () => void mutate("transfer", { targetStaffUserId: Number(select.value), reason: reason.input.value.trim() })));
    actions.append(returnHelp, button("Devolver à Sophie", () => void mutate("return-to-ai", { reason: reason.input.value.trim() })));
    actions.append(button("Encerrar atendimento", () => void mutate("close", { reason: reason.input.value.trim() }), "admin-danger-button"));
  }

  async function mutate(action, body) {
    if (mutating || !currentId) return;
    if (["transfer", "return-to-ai", "close"].includes(action) && !body.reason?.trim()) { status.textContent = "Informe a justificativa obrigatória."; return; }
    if (action === "transfer" && (!Number.isSafeInteger(body.targetStaffUserId) || body.targetStaffUserId < 1)) { status.textContent = "Selecione um atendente elegível."; return; }
    mutating = true; actions.querySelectorAll("button").forEach(item => item.disabled = true);
    try {
      const result = await api(`/admin/support-assistant/conversations/${encodeURIComponent(currentId)}/${action}`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
      });
      if(action==="messages"){pendingReplyBody=null;pendingReplyKey=null;}
      await loadDetail(currentId); await load(0, true);
      if (result?.state) status.textContent = `Atendimento atualizado: ${labelState(result.state)}.`;
    } catch (error) { status.textContent = errorMessage(error); if(action!=="messages")await loadDetail(currentId); }
    finally { mutating = false; }
  }

  filter.addEventListener("change", () => void load());
  refresh.addEventListener("click", () => void load());
  return { load, clear() { active = false; window.clearTimeout(pollTimer); pollTimer = null; list.replaceChildren(); detail.replaceChildren(); actions.replaceChildren(); status.textContent = ""; }, isMutating() { return mutating; } };
}

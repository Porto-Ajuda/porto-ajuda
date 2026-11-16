import { fetchAutenticado } from "./api.js";
import { createSupportVoiceController, DEFAULT_VOICE_PREFERENCE, normalizeVoicePreference, readVoicePreference, writeVoicePreference } from "./support-voice.js";

const STATES = Object.freeze({
  AI_ACTIVE: "Sophie pode ajudar",
  WAITING_HUMAN: "Aguardando atendimento humano",
  HUMAN_ACTIVE: "Atendimento com a equipe",
  CLOSED: "Conversa encerrada"
});

if (!document.getElementById("support-assistant-launcher")) {
  document.body.insertAdjacentHTML("beforeend", `
    <button id="support-assistant-launcher" type="button" aria-haspopup="dialog" aria-controls="support-assistant-panel" aria-label="Abrir atendimento Porto Ajuda">
      Ajuda <span id="support-assistant-unread" hidden aria-label="Mensagens não lidas"></span>
    </button>
    <dialog id="support-assistant-panel" aria-labelledby="support-title" aria-describedby="support-description">
      <div class="support-shell">
        <header class="support-header">
          <div><h2 id="support-title">Sophie</h2><p>Assistente virtual do Porto Ajuda</p><p id="support-status" aria-live="polite">Orientação pública</p></div>
          <div class="support-header__actions"><button type="button" id="support-info-toggle" aria-label="Informações sobre o atendimento" aria-expanded="false" aria-controls="support-info">?</button><button type="button" id="support-close" aria-label="Fechar atendimento">×</button></div>
        </header>
        <aside class="support-info" id="support-info" role="dialog" aria-label="Informações sobre privacidade e retenção" hidden>
          <button type="button" class="support-info__close" id="support-info-close" aria-label="Fechar informações">×</button>
          <p>A equipe autorizada pode ler as conversas para prestar atendimento. As respostas humanas são assíncronas e podem levar algum tempo.</p>
          <p>Após o encerramento, mensagens e conteúdo da conversa são removidos em 30 dias. Reabrir cancela esse prazo; um novo encerramento inicia outro prazo. Permanecem apenas metadados técnicos mínimos. Esta é uma política do produto, não uma garantia jurídica.</p>
        </aside>
        <section class="support-intro" id="support-intro" aria-live="polite"></section>
        <div class="support-toolbar" id="support-toolbar">
          <button type="button" id="support-new" hidden>Nova conversa</button>
          <button type="button" id="support-human" hidden>Falar com uma pessoa</button>
          <button type="button" id="support-retry-ai" hidden>Tentar novamente</button>
          <button type="button" id="support-resume-ai" hidden>Continuar com Sophie</button>
          <button type="button" id="support-voice-mode" aria-pressed="false">Ativar assistência por voz</button>
          <button type="button" id="support-reopen" hidden>Reabrir conversa</button>
          <button type="button" id="support-close-conversation" hidden>Encerrar conversa</button>
          <button type="button" id="support-history" aria-expanded="false" aria-controls="support-conversation-list">Histórico</button>
        </div>
        <div class="support-messages" id="support-messages" role="log" aria-live="polite" aria-relevant="additions text" tabindex="0">
          <section class="support-conversation-list" id="support-conversation-list" hidden aria-label="Conversas anteriores">
            <div class="support-history__header"><strong>Histórico</strong><button type="button" id="support-history-close" aria-label="Fechar histórico">Fechar</button></div>
            <div id="support-history-items"></div>
          </section>
          <div id="support-message-stream"></div>
        </div>
        <p class="support-status" id="support-feedback" role="status" aria-live="polite"></p>
        <section id="support-new-confirm" class="support-new-confirm" role="alertdialog" aria-modal="true" aria-labelledby="support-new-confirm-title" aria-describedby="support-new-confirm-description" hidden>
          <h3 id="support-new-confirm-title">Iniciar nova conversa?</h3>
          <p id="support-new-confirm-description"></p>
          <div><button type="button" id="support-new-cancel">Continuar nesta conversa</button><button type="button" id="support-new-confirm-action">Encerrar e iniciar</button></div>
        </section>
        <section id="support-human-confirm" class="support-new-confirm" role="alertdialog" aria-modal="true" aria-labelledby="support-human-confirm-title" aria-describedby="support-human-confirm-description" hidden>
          <h3 id="support-human-confirm-title">Falar com uma pessoa?</h3>
          <p id="support-human-confirm-description">A equipe responderá de forma assíncrona e poderá ler esta conversa para prestar atendimento.</p>
          <div><button type="button" id="support-human-cancel">Continuar com Sophie</button><button type="button" id="support-human-confirm-action">Confirmar pedido</button></div>
        </section>
        <section id="support-voice-onboarding" class="support-voice-onboarding" role="region" aria-labelledby="support-voice-onboarding-title" hidden>
          <h3 id="support-voice-onboarding-title">Deseja ativar a assistência por voz?</h3>
          <p>Você também pode continuar usando o chat por texto. A voz só começa após uma ação sua.</p>
          <div class="support-voice-onboarding__actions"><button type="button" id="support-voice-enable">Ativar</button><button type="button" id="support-voice-skip">Agora não</button><button type="button" id="support-voice-instructions">Ouvir instruções</button></div>
          <progress id="support-voice-hold-progress" max="3" value="0" aria-label="Atalho opcional: mantenha Ativar pressionado por três segundos" hidden></progress>
        </section>
        <section id="support-voice-settings" class="support-voice-settings" aria-label="Preferências de voz" hidden>
          <label><input type="checkbox" id="support-voice-enabled"> Ativar assistência por voz neste dispositivo</label>
          <label><input type="checkbox" id="support-read-aloud"> Ler novas respostas em voz alta</label>
          <label><input type="checkbox" id="support-avoid-screen-reader" checked> Evitar conflito com leitor de tela</label>
          <label for="support-voice-rate">Velocidade da leitura</label><input id="support-voice-rate" type="range" min="0.75" max="1.5" step="0.05" value="1">
          <label for="support-voice-select">Voz em português</label><select id="support-voice-select"><option value="">Voz padrão do navegador</option></select>
          <p>O ditado depende do navegador e pode usar um serviço externo de reconhecimento. Nenhum áudio é salvo pelo Porto Ajuda.</p>
          <button type="button" id="support-voice-settings-close">Fechar preferências</button>
        </section>
        <section id="support-voice-controls" class="support-voice-controls" aria-label="Controles da assistência por voz" hidden>
          <div class="support-voice-controls__shortcuts"><button type="button" id="support-voice-dictate" aria-describedby="support-voice-double-help">Iniciar ditado</button><span id="support-voice-double-help">Atalho opcional: dois toques neste botão iniciam e param o ditado.</span></div>
          <button type="button" id="support-voice-stop-dictation" disabled>Parar ditado</button>
          <button type="button" id="support-voice-read-transcript" disabled>Ouvir transcrição</button>
          <button type="button" id="support-voice-send" disabled>Enviar</button>
          <button type="button" id="support-voice-cancel" disabled>Cancelar</button>
          <button type="button" id="support-voice-read-reply">Ouvir resposta</button>
          <button type="button" id="support-voice-stop-reading" disabled>Parar leitura</button>
          <button type="button" id="support-voice-exit">Sair da assistência por voz</button>
        </section>
        <form class="support-compose" id="support-form">
          <label class="sr-only" for="support-message">Mensagem</label>
          <textarea id="support-message" maxlength="4000" placeholder="Escreva sua mensagem…" required aria-describedby="support-login"></textarea>
          <div class="support-send-row"><a id="support-login" href="/login" hidden>Entre para enviar uma mensagem</a><button type="button" id="support-dictate" aria-label="Iniciar ditado">🎙</button><button class="support-send" id="support-send" type="submit" disabled hidden>Enviar</button></div>
        </form>
      </div>
    </dialog>`);
}

const launcher = document.getElementById("support-assistant-launcher");
const dialog = document.getElementById("support-assistant-panel");
const intro = document.getElementById("support-intro");
const statusLabel = document.getElementById("support-status");
const feedback = document.getElementById("support-feedback");
const messagesEl = document.getElementById("support-messages");
const messageStream = document.getElementById("support-message-stream");
const form = document.getElementById("support-form");
const compose = document.getElementById("support-message");
const sendButton = document.getElementById("support-send");
const loginLink = document.getElementById("support-login");
const newButton = document.getElementById("support-new");
const newConfirm = document.getElementById("support-new-confirm");
const newConfirmDescription = document.getElementById("support-new-confirm-description");
const historyButton = document.getElementById("support-history");
const humanButton = document.getElementById("support-human");
const resumeAiButton = document.getElementById("support-resume-ai");
const reopenButton = document.getElementById("support-reopen");
const closeConversationButton = document.getElementById("support-close-conversation");
const historyList = document.getElementById("support-conversation-list");
const historyItems = document.getElementById("support-history-items");
const historyCloseButton = document.getElementById("support-history-close");
const unreadBadge = document.getElementById("support-assistant-unread");
const infoToggle = document.getElementById("support-info-toggle");
const infoPopover = document.getElementById("support-info");
const humanConfirm = document.getElementById("support-human-confirm");
const voiceModeButton = document.getElementById("support-voice-mode");
const voiceOnboarding = document.getElementById("support-voice-onboarding");
const voiceSettings = document.getElementById("support-voice-settings");
const voiceControls = document.getElementById("support-voice-controls");
const voicePreference = { ...DEFAULT_VOICE_PREFERENCE };
let introData = null;
let currentConversation = null;
let pollTimer = null;
let historyLoaded = false;
let historyRows = [];
let busy = false;
let unreadConversationIds = new Set();
let pendingMessageBody = null;
let pendingMessageKey = null;
let pendingStartConversationId = null;
let pendingStartPreviousId = null;
let authenticated = false;
let voiceScope = "anonymous";
let voiceChoiceMade = false;
let voiceModeActive = false;
let voiceReadMessageIds = new Set();
let holdTimer = null;
let holdProgressTimer = null;
let holdStartedAt = 0;
let holdActivated = false;
const voice = createSupportVoiceController({
  window,
  onTranscript: text => {
    compose.value = [compose.value.trim(), text].filter(Boolean).join(" ");
    compose.dispatchEvent(new Event("input"));
    document.getElementById("support-voice-read-transcript").disabled = !compose.value.trim();
    document.getElementById("support-voice-send").disabled = !compose.value.trim();
    document.getElementById("support-voice-cancel").disabled = !compose.value.trim();
  },
  onStatus: text => { feedback.textContent = text; },
  onSpeaking: speaking => { document.getElementById("support-voice-stop-reading").disabled = !speaking; },
  onDictation: active => {
    document.getElementById("support-voice-stop-dictation").disabled = !active;
    const dictate = document.getElementById("support-dictate");
    dictate.setAttribute("aria-label", active ? "Parar ditado" : "Iniciar ditado");
    dictate.textContent = active ? "■" : "🎙";
  }
});

function preferenceKey() { return `portoAjudaAssistenciaVoz:v1:${voiceScope}`; }
function onboardingKey() { return `portoAjudaAssistenciaVozOnboarding:v1:${voiceScope}`; }
function voiceStorage() { try { return window.localStorage; } catch { return null; } }
function persistVoicePreference() { writeVoicePreference(voiceStorage(), preferenceKey(), voicePreference); syncVoiceControls(); }
function syncVoiceControls() {
  const pairs = [
    ["support-voice-enabled", voicePreference.enabled], ["support-read-aloud", voicePreference.readAloud],
    ["support-avoid-screen-reader", voicePreference.avoidScreenReaderAnnouncements],
    ["preferencia-assistencia-voz", voicePreference.enabled], ["preferencia-leitura-voz", voicePreference.readAloud],
    ["preferencia-evitar-leitor-tela", voicePreference.avoidScreenReaderAnnouncements]
  ];
  for (const [id, checked] of pairs) { const input = document.getElementById(id); if (input) input.checked = checked; }
  const rate = document.getElementById("support-voice-rate"); if (rate) rate.value = String(voicePreference.rate);
  const selector = document.getElementById("support-voice-select"); if (selector) selector.value = voicePreference.voiceURI;
  const exit = document.getElementById("acss-voz-sair"); if (exit) exit.hidden = !voiceModeActive;
}
async function resolveVoiceScope() {
  try {
    const scope = await request("/support-assistant/voice-preference-scope");
    if (Number.isSafeInteger(scope?.userId)) { voiceScope = `user-${scope.userId}`; return true; }
  } catch { /* Anonymous preference stays in its separate local namespace. */ }
  voiceScope = "anonymous";
  return false;
}
async function loadVoicePreferenceScope() {
  if (authenticated) await resolveVoiceScope();
  const storage = voiceStorage();
  const stored = readVoicePreference(storage, preferenceKey());
  Object.assign(voicePreference, stored || DEFAULT_VOICE_PREFERENCE);
  voiceChoiceMade = storage?.getItem(onboardingKey()) === "done" || Boolean(stored?.enabled);
  syncVoiceControls();
}
function completeVoiceOnboarding(enabled) {
  Object.assign(voicePreference, normalizeVoicePreference({ ...voicePreference, enabled }));
  voiceChoiceMade = true;
  try { voiceStorage()?.setItem(onboardingKey(), "done"); } catch { /* Local preference is optional. */ }
  persistVoicePreference();
  voiceOnboarding.hidden = true;
  if (enabled) setVoiceMode(true);
  else voiceModeButton.focus();
}
function setVoiceMode(active) {
  voiceModeActive = Boolean(active);
  voiceControls.hidden = !voiceModeActive;
  voiceSettings.hidden = true;
  voiceModeButton.setAttribute("aria-pressed", String(voiceModeActive));
  voiceModeButton.textContent = voiceModeActive ? "Assistência por voz ativa" : "Ativar assistência por voz";
  if (dialog.classList?.toggle) dialog.classList.toggle("support-voice-mode", voiceModeActive);
  else dialog.className = voiceModeActive ? `${dialog.className} support-voice-mode`.trim() : dialog.className.replace(/\bsupport-voice-mode\b/g, "").trim();
  syncVoiceControls();
  if (active) document.getElementById("support-voice-dictate").focus();
  else document.getElementById("support-voice-mode").focus();
}
function populateVoiceChoices() {
  const select = document.getElementById("support-voice-select");
  if (!select) return;
  select.replaceChildren();
  const fallback = document.createElement("option"); fallback.value = ""; fallback.textContent = "Voz padrão do navegador"; select.append(fallback);
  for (const option of voice.voices()) {
    const item = document.createElement("option"); item.value = option.voiceURI; item.textContent = `${option.name} (${option.lang})`; select.append(item);
  }
  select.value = voicePreference.voiceURI;
}

function syncUnreadIndicator() {
  unreadBadge.hidden = unreadConversationIds.size === 0;
  if (unreadConversationIds.size) unreadBadge.setAttribute("aria-label", `${unreadConversationIds.size} conversa(s) com mensagens não lidas`);
}

async function request(path, options = {}) {
  const response = await fetchAutenticado(`${path}`, {
    ...options,
    headers: { Accept: "application/json", ...(options.body ? { "Content-Type": "application/json" } : {}), ...(options.headers || {}) },
    ...(options.body ? { body: JSON.stringify(options.body) } : {})
  });
  if (response.status === 401) throw new Error("AUTH_REQUIRED");
  if (!response.ok) {
    let message = "Não foi possível concluir a solicitação. Tente novamente.";
    try { const data = await response.json(); if (typeof data?.message === "string") message = data.message; } catch { /* resposta sem detalhe seguro */ }
    const error = new Error(message); error.status = response.status; throw error;
  }
  if (response.status === 204) return null;
  return response.json();
}

function readableLinks(message) {
  const body = document.createElement("p");
  body.className = "support-message__body";
  const lines = String(message.body || "").split("\n");
  const allowed = /^\/(?:ong-profile\?id=\d+(?:#[\w-]+)?|catalogo\?categoria=(?:ongs|publicacoes|voluntariado|doacoes)(?:&filtro=[A-Z_]+)?|login(?:#recuperacao-senha)?|perfil|pages\/cadastro-osc\.html)$/;
  lines.forEach((line, lineIndex) => {
    if (message.senderType === "AI") {
      const pattern = /\[([^\]]{1,120})\]\((\/[^)\s]{1,300})\)/g;
      let cursor = 0;
      let match;
      while ((match = pattern.exec(line))) {
        body.append(document.createTextNode(line.slice(cursor, match.index)));
        if (allowed.test(match[2])) {
          const link = document.createElement("a");
          link.href = match[2];
          link.textContent = match[1];
          body.append(link);
        } else body.append(document.createTextNode(match[1]));
        cursor = pattern.lastIndex;
      }
      body.append(document.createTextNode(line.slice(cursor)));
    } else body.append(document.createTextNode(line));
    if (lineIndex < lines.length - 1) body.append(document.createElement("br"));
  });
  return body;
}
function renderConversation(data) {
  const previous = currentConversation?.id === data.id ? currentConversation : null;
  const priorIds = new Set((previous?.messages || []).map(message => String(message.id)));
  const firstView = !previous || !(previous.messages || []).length;
  currentConversation = data;
  if (historyRows.some(row => row.id === data.id)) {
    const firstUser = (data.messages || []).find(message => message.senderType === "USER");
    const last = (data.messages || []).at(-1);
    historyRows = historyRows.map(row => row.id === data.id ? {
      ...row, state: data.state, updatedAt: data.updatedAt, lastMessageAt: data.updatedAt,
      firstUserMessage: row.firstUserMessage || firstUser?.body || "",
      lastMessagePreview: last?.body || row.lastMessagePreview, unread: data.unread
    } : row);
  }
  if (data.unread) unreadConversationIds.add(data.id);
  else unreadConversationIds.delete(data.id);
  syncUnreadIndicator();
  statusLabel.textContent = data.state === "HUMAN_ACTIVE"
    ? data.assignedStaffLabel || "Atendimento humano com a equipe Porto Ajuda"
    : data.state === "AI_ACTIVE" && introData?.aiAvailable === false
      ? "Sophie indisponível · mensagens ficam nesta conversa"
      : STATES[data.state] || "Atendimento";
  messageStream.replaceChildren();
  intro.hidden = true;
  for (const message of (Array.isArray(data.messages) ? data.messages : [])) {
    const article = document.createElement("article");
    article.className = `support-message support-message--${String(message.senderType || "system").toLowerCase()}`;
    const meta = document.createElement("div"); meta.className = "support-message__meta";
    const sender = document.createElement("strong"); sender.textContent = message.senderType === "AI"
      ? `${introData?.assistantName || "Sophie"} · resposta automática`
      : message.senderType === "STAFF" ? "Equipe Porto Ajuda · atendimento humano"
        : message.senderLabel || (message.senderType === "SYSTEM" ? "Aviso automático" : "Porto Ajuda");
    const time = document.createElement("time"); time.dateTime = message.createdAt;
    const date = new Date(message.createdAt); time.textContent = Number.isNaN(date.valueOf()) ? "" : new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(date);
    meta.append(sender, time); article.append(meta, readableLinks(message)); messageStream.append(article);
  }
  if (firstView) {
    for (const message of data.messages || []) voiceReadMessageIds.add(`${data.id}:${message.id}`);
  } else if (voicePreference.readAloud && !voicePreference.avoidScreenReaderAnnouncements) {
    const unseen = (data.messages || []).filter(message => !priorIds.has(String(message.id))
      && ["AI", "STAFF"].includes(message.senderType)
      && !voiceReadMessageIds.has(`${data.id}:${message.id}`));
    const lastReply = unseen.at(-1);
    if (lastReply) {
      voiceReadMessageIds.add(`${data.id}:${lastReply.id}`);
      voice.speak(lastReply.body, voicePreference);
    }
  }
  messagesEl.scrollTop = messagesEl.scrollHeight;
  const latestMessage = data.messages?.at(-1);
  const securityBoundary = latestMessage?.senderType === "SYSTEM"
    && String(latestMessage.body || "").includes("o atendimento humano também não contorna essas permissões");
  humanButton.hidden = data.state !== "AI_ACTIVE" || securityBoundary;
  document.getElementById("support-retry-ai").hidden = data.state !== "AI_ACTIVE" || introData?.aiAvailable === false
    || !(data.messages || []).some(message => message.senderType === "SYSTEM" && /não conseguiu responder|temporariamente indisponível/i.test(message.body || ""));
  resumeAiButton.hidden = !["WAITING_HUMAN", "HUMAN_ACTIVE"].includes(data.state) || introData?.aiAvailable === false;
  resumeAiButton.title = data.state === "HUMAN_ACTIVE"
    ? "Encerra o atendimento humano atual, avisa a pessoa atendente e retoma com Sophie."
    : "Cancela a espera da equipe e continua esta conversa com Sophie.";
  reopenButton.hidden = data.state !== "CLOSED";
  closeConversationButton.hidden = data.state === "CLOSED";
  sendButton.disabled = busy || data.state === "CLOSED";
  updateComposerControls();
  renderHistoryRows(historyRows);
  startPolling();
}

function formatHistoryDate(value) {
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? "Data indisponível"
    : new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(date);
}

function historyStateLabel(state) {
  return ({ AI_ACTIVE: "Com Sophie", WAITING_HUMAN: "Aguardando equipe",
    HUMAN_ACTIVE: "Com atendimento humano", CLOSED: "Encerrada" })[state] || "Conversa";
}

function historyTitle(row) {
  const source = String(row.firstUserMessage || "").replace(/[\u0000-\u001F\u007F-\u009F]+/g, " ").replace(/\s+/g, " ").trim();
  if (!source) return "Nova conversa";
  return source.length > 54 ? source.slice(0, 53).trimEnd() + "…" : source;
}

function safePreview(value) {
  const source = String(value || "").replace(/[\u0000-\u001F\u007F-\u009F]+/g, " ").replace(/\s+/g, " ").trim();
  return source.length > 110 ? source.slice(0, 109).trimEnd() + "…" : source || "Sem mensagens";
}

function closeHistory() {
  historyList.hidden = true;
  historyButton.setAttribute("aria-expanded", "false");
}

function renderHistoryRows(rows) {
  historyRows = Array.isArray(rows) ? rows : [];
  historyItems.replaceChildren();
  if (!historyRows.length) {
    const empty = document.createElement("p"); empty.className = "support-history-empty";
    empty.textContent = authenticated ? "Nenhuma conversa anterior." : "Entre para acessar seu histórico.";
    historyItems.append(empty);
    return;
  }
  for (const row of historyRows) {
    const button = document.createElement("button"); button.type = "button";
    button.className = "support-history-item";
    const selected = currentConversation?.id === row.id;
    button.setAttribute("aria-pressed", String(selected));
    if (selected) button.setAttribute("aria-current", "true");
    const heading = document.createElement("span"); heading.className = "support-history-item__title";
    heading.textContent = historyTitle(row);
    const date = document.createElement("time"); date.className = "support-history-item__date";
    date.dateTime = row.updatedAt || row.createdAt || "";
    date.textContent = formatHistoryDate(row.updatedAt || row.createdAt);
    const state = document.createElement("span"); state.className = "support-history-item__state";
    state.textContent = historyStateLabel(row.state) + (row.unread ? " · não lida" : "");
    const preview = document.createElement("span"); preview.className = "support-history-item__preview";
    preview.textContent = safePreview(row.lastMessagePreview);
    button.append(heading, date, state, preview);
    button.addEventListener("click", async () => {
      if (busy) return;
      try {
        renderConversation(await request("/support-assistant/conversations/" + encodeURIComponent(row.id)));
        closeHistory(); feedback.textContent = "";
      } catch (error) { showError(error); }
    });
    historyItems.append(button);
  }
}

async function loadHistory() {
  if (historyLoaded) return;
  historyLoaded = true;
  try {
    const rows = await request("/support-assistant/conversations");
    authenticated = true;
    compose.disabled = false;
    newButton.hidden = false;
    sendButton.disabled = !introData?.conversationsEnabled;
    unreadConversationIds = new Set();
    renderHistoryRows(rows);
    for (const row of rows) if (row.unread) unreadConversationIds.add(row.id);
    syncUnreadIndicator();
    if (!currentConversation && rows.length) {
      try { renderConversation(await request("/support-assistant/conversations/" + encodeURIComponent(rows[0].id))); }
      catch (error) { showError(error); }
    }
  } catch (error) {
    historyLoaded = false;
    if (error.message === "AUTH_REQUIRED") {
      authenticated = false; compose.disabled = true; sendButton.disabled = true; newButton.hidden = true;
      loginLink.hidden = false;
      feedback.textContent = "Entre na sua conta para criar uma conversa persistente e enviar mensagens.";
    }
    else feedback.textContent = error.message;
    renderHistoryRows([]);
  }
}

function showError(error) {
  if (error.message === "AUTH_REQUIRED") {
    authenticated = false; historyLoaded = false; newButton.hidden = true; compose.disabled = true; sendButton.disabled = true;
    loginLink.hidden = false;
    feedback.textContent = "Entre na sua conta para criar uma conversa persistente e enviar mensagens.";
  } else feedback.textContent = error.message || "Não foi possível conectar ao atendimento.";
}

function updateComposerControls() {
  const hasText = Boolean(compose.value.trim());
  const dictate = document.getElementById("support-dictate");
  sendButton.hidden = !hasText;
  dictate.hidden = hasText;
  const unavailable = !authenticated || !introData?.conversationsEnabled || currentConversation?.state === "CLOSED";
  sendButton.disabled = busy || !hasText || unavailable;
  document.getElementById("support-voice-send").disabled = busy || !hasText || unavailable
    || currentConversation?.state === "CLOSED";
  document.getElementById("support-voice-cancel").disabled = !hasText;
  document.getElementById("support-voice-read-transcript").disabled = !hasText;
}

async function requestHumanNow() {
  if (!currentConversation || busy) return;
  busy = true; humanConfirm.hidden = true; humanButton.disabled = true;
  try {
    renderConversation(await request(`/support-assistant/conversations/${encodeURIComponent(currentConversation.id)}/request-human`, {
      method: "POST", body: { reason: "A pessoa confirmou que deseja atendimento humano." }
    }));
    feedback.textContent = "Pedido confirmado e encaminhado. A equipe responderá de forma assíncrona.";
  } catch (error) { showError(error); }
  finally { busy = false; humanButton.disabled = false; humanButton.focus(); }
}

async function retryAi() {
  if (!currentConversation || busy) return;
  const button = document.getElementById("support-retry-ai");
  busy = true; button.disabled = true; feedback.textContent = "Tentando responder novamente…";
  try {
    renderConversation(await request(`/support-assistant/conversations/${encodeURIComponent(currentConversation.id)}/retry-ai`, { method: "POST" }));
    feedback.textContent = "A tentativa foi concluída.";
  } catch (error) { showError(error); }
  finally { busy = false; button.disabled = false; updateComposerControls(); }
}

async function sendCurrentMessage() {
  const text = compose.value.trim(); if (!text || busy) return;
  if (pendingMessageBody !== text) { pendingMessageBody = text; pendingMessageKey = crypto.randomUUID(); }
  busy = true; sendButton.disabled = true; feedback.textContent = "Enviando…";
  try {
    if (!currentConversation) {
      if (!pendingStartConversationId) { pendingStartConversationId = crypto.randomUUID(); pendingStartPreviousId = null; }
      currentConversation = await request("/support-assistant/conversations/new", {
        method: "POST", body: { conversationId: pendingStartConversationId, previousConversationId: pendingStartPreviousId }
      });
      pendingStartConversationId = null; pendingStartPreviousId = null;
    }
    const result = await request(`/support-assistant/conversations/${encodeURIComponent(currentConversation.id)}/messages`, {
      method: "POST", body: { body: text, idempotencyKey: pendingMessageKey }
    });
    compose.value = ""; pendingMessageBody = null; pendingMessageKey = null; historyLoaded = false; renderConversation(result);
    feedback.textContent = result.state === "WAITING_HUMAN" ? "Conversa confirmada na fila de atendimento." : "Mensagem registrada nesta conversa.";
  } catch (error) { showError(error); }
  finally { busy = false; updateComposerControls(); }
}

async function showVoiceSettings() {
  const trigger = document.activeElement;
  if (!dialog.open) await openPanel();
  voiceOnboarding.hidden = true;
  voiceSettings.hidden = false;
  populateVoiceChoices();
  voiceSettings.dataset.returnFocusId = trigger?.id || "support-voice-mode";
  document.getElementById("support-voice-enabled").focus();
}

async function askBeforeStartingNewConversation() {
  if (busy || !authenticated) return;
  if (currentConversation?.id) {
    busy = true; newButton.disabled = true;
    try {
      renderConversation(await request(`/support-assistant/conversations/${encodeURIComponent(currentConversation.id)}`));
    } catch (error) {
      if (error.status !== 404) { showError(error); busy = false; newButton.disabled = false; return; }
      currentConversation = null; messageStream.replaceChildren(); intro.hidden = false;
    } finally { busy = false; newButton.disabled = false; sendButton.disabled = !currentConversation || currentConversation.state === "CLOSED"; }
  }
  if (["WAITING_HUMAN", "HUMAN_ACTIVE"].includes(currentConversation?.state)) {
    const activeHuman = currentConversation.state === "HUMAN_ACTIVE";
    newConfirmDescription.textContent = activeHuman
      ? "Esta conversa está sendo atendida pela equipe. Iniciar outra encerrará esta solicitação; o histórico continuará disponível até a retenção de 30 dias após o encerramento."
      : "Esta solicitação está aguardando a equipe. Iniciar outra encerrará o pedido atual; o histórico continuará disponível até a retenção de 30 dias após o encerramento.";
    newConfirm.hidden = false;
    document.getElementById("support-new-cancel").focus();
    return;
  }
  void startNewConversation();
}

async function startNewConversation() {
  if (busy || !authenticated) return;
  const previousId = currentConversation?.id || null;
  if (!pendingStartConversationId || pendingStartPreviousId !== previousId) {
    pendingStartConversationId = crypto.randomUUID();
    pendingStartPreviousId = previousId;
  }
  busy = true; newButton.disabled = true; feedback.textContent = "Iniciando conversa…";
  try {
    const result = await request("/support-assistant/conversations/new", {
      method: "POST",
      body: { conversationId: pendingStartConversationId, previousConversationId: pendingStartPreviousId }
    });
    pendingStartConversationId = null; pendingStartPreviousId = null;
    historyLoaded = false; closeHistory();
    newConfirm.hidden = true;
    compose.value = ""; pendingMessageBody = null; pendingMessageKey = null;
    renderConversation(result);
    feedback.textContent = "Nova conversa iniciada. O histórico anterior continua disponível.";
    compose.focus();
  } catch (error) { showError(error); }
  finally { busy = false; newButton.disabled = !authenticated; sendButton.disabled = !currentConversation || currentConversation.state === "CLOSED"; }
}

async function openPanel() {
  if (!dialog.open) dialog.show();
  feedback.textContent = ""; launcher.setAttribute("aria-expanded", "true");
  document.getElementById("support-close").focus();
  if (!intro.childElementCount) {
    try {
      const data = await request("/support-assistant/intro");
      introData = data;
      const assistantName = data.assistantName || "Sophie";
      document.getElementById("support-title").textContent = assistantName;
      const greetingLabel = document.createElement("strong"); greetingLabel.className = "support-greeting__label"; greetingLabel.textContent = `${assistantName} · mensagem automática`;
      const p = document.createElement("p"); p.textContent = `Olá! Sou a ${assistantName}, assistente virtual do Porto Ajuda. Como posso ajudar?`; p.className = "support-greeting"; intro.append(greetingLabel, p);
      intro.hidden = Boolean(currentConversation);
      if (!data.conversationsEnabled) feedback.textContent = "Conversas persistentes estão temporariamente indisponíveis. Você ainda pode consultar conteúdo público.";
      sendButton.disabled = !data.conversationsEnabled || !authenticated;
    } catch { feedback.textContent = "A orientação está temporariamente indisponível."; }
  }
  if (!historyLoaded) await loadHistory();
  await loadVoicePreferenceScope();
  if (!voiceChoiceMade) { voiceOnboarding.hidden = false; voiceEnable.focus(); }
  if (currentConversation?.id && !currentConversation.messages?.length) {
    try { renderConversation(await request(`/support-assistant/conversations/${encodeURIComponent(currentConversation.id)}`)); }
    catch (error) {
      if (error.status === 404) {
        currentConversation = null; messageStream.replaceChildren(); intro.hidden = false; humanButton.hidden = true; reopenButton.hidden = true;
        feedback.textContent = "O conteúdo desta conversa já expirou após o prazo de retenção. Você pode iniciar uma nova conversa.";
      } else showError(error);
    }
  }
  startPolling();
}

function startPolling() {
  window.clearInterval(pollTimer); pollTimer = null;
  if (!dialog.open || !currentConversation || currentConversation.state === "CLOSED") return;
  pollTimer = window.setInterval(async () => {
    if (busy || !dialog.open || !currentConversation) return;
    try {
      const newer = await request(`/support-assistant/conversations/${encodeURIComponent(currentConversation.id)}`);
      if (newer.version !== currentConversation.version || newer.messages?.length !== currentConversation.messages?.length) renderConversation(newer);
      if (newer.unread) unreadBadge.hidden = false;
    } catch (error) { if (error.message !== "AUTH_REQUIRED") feedback.textContent = "Não foi possível atualizar a conversa. Tente novamente."; }
  }, 15000);
}

launcher.addEventListener("click", () => void openPanel());
infoToggle.addEventListener("click", () => { infoPopover.hidden = !infoPopover.hidden; infoToggle.setAttribute("aria-expanded", String(!infoPopover.hidden)); if (!infoPopover.hidden) document.getElementById("support-info-close").focus(); });
document.getElementById("support-info-close").addEventListener("click", () => { infoPopover.hidden = true; infoToggle.setAttribute("aria-expanded", "false"); infoToggle.focus(); });
document.addEventListener("click", event => { if (!infoPopover.hidden && !infoPopover.contains(event.target) && event.target !== infoToggle) { infoPopover.hidden = true; infoToggle.setAttribute("aria-expanded", "false"); } });
document.getElementById("support-close").addEventListener("click", () => dialog.close());
newButton.addEventListener("click", askBeforeStartingNewConversation);
document.getElementById("support-new-cancel").addEventListener("click", () => {
  newConfirm.hidden = true; pendingStartConversationId = null; pendingStartPreviousId = null; newButton.focus();
});
document.getElementById("support-new-confirm-action").addEventListener("click", () => void startNewConversation());
newConfirm.addEventListener("keydown", event => {
  if (event.key === "Escape") {
    event.preventDefault(); event.stopPropagation(); newConfirm.hidden = true;
    pendingStartConversationId = null; pendingStartPreviousId = null; newButton.focus();
    return;
  }
  if (event.key === "Tab") {
    const controls = [document.getElementById("support-new-cancel"), document.getElementById("support-new-confirm-action")];
    const first = controls[0], last = controls[controls.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  }
});
dialog.addEventListener("keydown", event => {
  if (event.key !== "Escape") return;
  event.preventDefault();
  cancelVoiceHold();
  if (!humanConfirm.hidden) { humanConfirm.hidden = true; humanButton.focus(); }
  else if (!voiceSettings.hidden) { voiceSettings.hidden = true; document.getElementById(voiceSettings.dataset.returnFocusId || "support-voice-mode")?.focus(); }
  else if (!voiceOnboarding.hidden) { voiceOnboarding.hidden = true; voiceChoiceMade = true; try { voiceStorage()?.setItem(onboardingKey(), "done"); } catch {} voiceModeButton.focus(); }
  else if (!infoPopover.hidden) { infoPopover.hidden = true; infoToggle.setAttribute("aria-expanded", "false"); infoToggle.focus(); }
  else if (!historyList.hidden) { closeHistory(); historyButton.focus(); }
  else dialog.close();
});
dialog.addEventListener("close", () => {
  window.clearInterval(pollTimer); pollTimer = null; launcher.setAttribute("aria-expanded", "false"); launcher.focus();
  if(currentConversation) currentConversation={id:currentConversation.id,state:currentConversation.state,version:currentConversation.version,messages:[],unread:currentConversation.unread};
  newConfirm.hidden = true; humanConfirm.hidden = true; voiceOnboarding.hidden = true; voiceSettings.hidden = true; compose.value="";
  voice.stopDictation(); voice.stopSpeaking(); setVoiceMode(false);
  pendingMessageBody=null; pendingMessageKey=null;
});
dialog.addEventListener("click", event => { if (event.target === dialog) dialog.close(); });
historyButton.addEventListener("click", () => {
  if (!historyList.hidden) { closeHistory(); return; }
  historyList.hidden = false; historyButton.setAttribute("aria-expanded", "true");
  if (!historyLoaded) void loadHistory();
});
historyCloseButton.addEventListener("click", () => { closeHistory(); historyButton.focus(); });
humanButton.addEventListener("click", () => {
  if (!currentConversation || busy) return;
  humanConfirm.hidden = false; document.getElementById("support-human-cancel").focus();
});
document.getElementById("support-human-cancel").addEventListener("click", () => { humanConfirm.hidden = true; humanButton.focus(); });
document.getElementById("support-human-confirm-action").addEventListener("click", () => void requestHumanNow());
humanConfirm.addEventListener("keydown", event => { if (event.key === "Escape") { event.preventDefault(); humanConfirm.hidden = true; humanButton.focus(); } });
document.getElementById("support-retry-ai").addEventListener("click", () => void retryAi());
resumeAiButton.addEventListener("click", async () => {
  if (!currentConversation || busy) return;
  busy = true; resumeAiButton.disabled = true; feedback.textContent = "Retomando a conversa com Sophie…";
  try {
    renderConversation(await request("/support-assistant/conversations/" + encodeURIComponent(currentConversation.id) + "/continue-with-sophie", {
      method: "POST"
    }));
    feedback.textContent = "A conversa voltou para a Sophie. Envie uma nova mensagem para continuar; as mensagens anteriores não serão reenviadas.";
    compose.focus();
  } catch (error) { showError(error); }
  finally {
    busy = false; resumeAiButton.disabled = false;
    sendButton.disabled = !currentConversation || currentConversation.state === "CLOSED";
  }
});
reopenButton.addEventListener("click", async () => {
  if (!currentConversation) return;
  busy = true;
  try { renderConversation(await request(`/support-assistant/conversations/${encodeURIComponent(currentConversation.id)}/reopen`, { method: "POST" })); feedback.textContent = "Conversa reaberta."; }
  catch (error) { showError(error); }
  finally { busy = false; }
});
closeConversationButton.addEventListener("click", async () => {
  if (!currentConversation) return;
  busy = true; closeConversationButton.disabled = true;
  try { renderConversation(await request(`/support-assistant/conversations/${encodeURIComponent(currentConversation.id)}/close`, { method: "POST" })); feedback.textContent = "Conversa encerrada. Você poderá reabri-la mais tarde."; }
  catch (error) { showError(error); }
  finally { busy = false; closeConversationButton.disabled = false; }
});
form.addEventListener("submit", event => { event.preventDefault(); void sendCurrentMessage(); });
compose.addEventListener("input", updateComposerControls);

function cancelVoiceHold() {
  window.clearTimeout(holdTimer); window.clearInterval(holdProgressTimer); holdTimer = null; holdProgressTimer = null;
  const progress = document.getElementById("support-voice-hold-progress");
  if (progress) { progress.value = 0; progress.hidden = true; }
}
function activateVoiceFromHold() { holdActivated = true; cancelVoiceHold(); completeVoiceOnboarding(true); feedback.textContent = "Assistência por voz ativada."; }
const voiceEnable = document.getElementById("support-voice-enable");
voiceEnable.addEventListener("pointerdown", () => {
  cancelVoiceHold(); holdActivated = false; holdStartedAt = Date.now();
  const progress = document.getElementById("support-voice-hold-progress"); progress.hidden = false; progress.value = 0;
  holdProgressTimer = window.setInterval(() => { progress.value = Math.min(3, (Date.now() - holdStartedAt) / 1000); }, 50);
  holdTimer = window.setTimeout(activateVoiceFromHold, 3000);
});
for (const eventName of ["pointerup", "pointerleave", "pointercancel", "blur"]) voiceEnable.addEventListener(eventName, cancelVoiceHold);
voiceEnable.addEventListener("click", () => { if (holdActivated) { holdActivated = false; return; } completeVoiceOnboarding(true); feedback.textContent = "Assistência por voz ativada. O microfone só começa após você escolher Iniciar ditado."; });
document.getElementById("support-voice-skip").addEventListener("click", () => completeVoiceOnboarding(false));
document.getElementById("support-voice-instructions").addEventListener("click", () => voice.speak(
  "Você pode ativar a assistência por voz, iniciar o ditado e revisar a transcrição antes de enviar. A leitura em voz alta é opcional. Você também pode usar o teclado e o chat por texto.", voicePreference));
voiceModeButton.addEventListener("click", () => {
  if (!voicePreference.enabled) { completeVoiceOnboarding(true); return; }
  setVoiceMode(!voiceModeActive);
});
document.getElementById("support-voice-exit").addEventListener("click", () => setVoiceMode(false));
document.getElementById("acss-voz-sair")?.addEventListener("click", () => setVoiceMode(false));
for (const id of ["acss-voz-configurar", "preferencia-configurar-voz"]) document.getElementById(id)?.addEventListener("click", () => void showVoiceSettings());
document.getElementById("support-voice-settings-close").addEventListener("click", () => {
  voiceSettings.hidden = true;
  document.getElementById(voiceSettings.dataset.returnFocusId || "support-voice-mode")?.focus();
});
for (const [id, property] of [["support-voice-enabled", "enabled"], ["support-read-aloud", "readAloud"],
  ["support-avoid-screen-reader", "avoidScreenReaderAnnouncements"], ["preferencia-assistencia-voz", "enabled"],
  ["preferencia-leitura-voz", "readAloud"], ["preferencia-evitar-leitor-tela", "avoidScreenReaderAnnouncements"]]) {
  document.getElementById(id)?.addEventListener("change", event => {
    const checked = event.target.checked;
    void (async () => {
      await resolveVoiceScope();
      Object.assign(voicePreference, readVoicePreference(voiceStorage(), preferenceKey()) || DEFAULT_VOICE_PREFERENCE);
      voicePreference[property] = checked;
      if (property === "enabled") {
        voiceChoiceMade = true;
        try { voiceStorage()?.setItem(onboardingKey(), "done"); } catch { /* Onboarding preference is optional. */ }
      }
      persistVoicePreference();
      if (property === "readAloud" && checked && voicePreference.avoidScreenReaderAnnouncements)
        feedback.textContent = "A leitura automática continua pausada para evitar conflito com o leitor de tela. Desative essa opção se desejar ouvi-la automaticamente.";
    })();
  });
}
document.getElementById("support-voice-rate").addEventListener("input", event => { voicePreference.rate = Number(event.target.value); persistVoicePreference(); });
document.getElementById("support-voice-select").addEventListener("change", event => { voicePreference.voiceURI = event.target.value; persistVoicePreference(); });
window.speechSynthesis?.addEventListener?.("voiceschanged", populateVoiceChoices);
document.getElementById("support-voice-dictate").addEventListener("click", event => {
  if (event.detail >= 2 && voice.dictating) voice.stopDictation();
  else voice.startDictation();
  document.getElementById("support-voice-stop-dictation").disabled = !voice.dictating;
});
document.getElementById("support-dictate").addEventListener("click", () => {
  if (voice.dictating) voice.stopDictation(); else voice.startDictation();
});
document.getElementById("support-voice-stop-dictation").addEventListener("click", () => { voice.stopDictation(); document.getElementById("support-voice-stop-dictation").disabled = true; });
document.getElementById("support-voice-read-transcript").addEventListener("click", () => voice.speak(compose.value, voicePreference));
document.getElementById("support-voice-send").addEventListener("click", () => void sendCurrentMessage());
document.getElementById("support-voice-cancel").addEventListener("click", () => { compose.value = ""; compose.dispatchEvent(new Event("input")); feedback.textContent = "Transcrição cancelada."; });
document.getElementById("support-voice-read-reply").addEventListener("click", () => {
  const reply = [...(currentConversation?.messages || [])].reverse().find(message => ["AI", "STAFF"].includes(message.senderType));
  if (reply) voice.speak(reply.body, voicePreference); else feedback.textContent = "Ainda não há resposta da Sophie ou da equipe para ouvir.";
});
document.getElementById("support-voice-stop-reading").addEventListener("click", () => voice.stopSpeaking());
document.getElementById("support-dictate").setAttribute("title", "O navegador pode usar um serviço externo de reconhecimento de voz. Nenhum áudio é salvo pelo Porto Ajuda.");
syncVoiceControls(); updateComposerControls();

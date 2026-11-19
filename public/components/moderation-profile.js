import { fetchAutenticado } from "./api.js";
import { possuiDadosDeModeracao, possuiHistoricoModeracaoOng } from "./moderation-profile-logic.js";

const formatDate = new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium", timeStyle: "short" });
const restrictionRefreshTimers = new WeakMap();
const organizationStates = new WeakMap();

export async function carregarModeracaoPerfil(section, endpoint) {
    if (!section) return null;
    clearRestrictionRefresh(section);
    const status = section.querySelector("[data-moderation-status]");
    const content = section.querySelector("[data-moderation-content]");
    status.textContent = "Carregando informações de moderação…";
    try {
        const response = await fetchAutenticado(endpoint, { headers: { Accept: "application/json" } });
        if (!response.ok) throw new Error(response.status === 403
            ? "Você não tem permissão para consultar esta pontuação."
            : "Não foi possível carregar as informações de moderação.");
        const subject = await response.json();
        const visible = renderSubject(content, subject, endpoint);
        section.hidden = !visible;
        if (!visible) {
            status.textContent = "";
            return subject;
        }
        scheduleRestrictionRefresh(section, endpoint, subject);
        status.textContent = "";
        return subject;
    } catch (error) {
        status.textContent = error?.message || "Não foi possível carregar as informações de moderação.";
        return null;
    }
}

function renderSubject(container, subject, endpoint) {
    container.replaceChildren();
    if (!possuiDadosDeModeracao(subject)) return false;
    const summary = document.createElement("p");
    const temporary = subject.temporaryRestrictionEndsAt
        ? "Período temporário " + (subject.temporaryRestrictionActive ? "ativo até " : "encerrado em ") + formatDate.format(Date.parse(subject.temporaryRestrictionEndsAt)) + "."
        : "Não há período temporário registrado.";
    const restriction = subject.permanentRestrictionActive
        ? "Restrição permanente para publicações e alterações ativa. " + temporary
        : subject.temporaryRestrictionActive
            ? "Restrição temporária ativa até " + formatDate.format(Date.parse(subject.temporaryRestrictionEndsAt)) + "."
            : "Não há restrição por pontuação ativa.";
    summary.className = "moderation-profile__score-summary";
    summary.textContent = "Pontuação atual: " + subject.score + ". " + restriction
        + " Os pontos permanecem após o término da restrição temporária.";
    container.append(summary);
    renderMutationRestrictionNotices(container, subject);

    const appealByDecision = new Map((subject.appeals || []).map(appeal => [appeal.decisionHistoryId, appeal]));
    const netByDecision = new Map();
    for (const entry of subject.pointsHistory || []) {
        if (entry.decisionHistoryId) netByDecision.set(entry.decisionHistoryId,
            (netByDecision.get(entry.decisionHistoryId) || 0) + entry.delta);
    }
    const historyEntries = Array.isArray(subject.pointsHistory) ? subject.pointsHistory : [];
    if (historyEntries.length) {
        const heading = document.createElement("h4");
        heading.textContent = "Histórico de pontuação";
        container.append(heading);
        const list = document.createElement("ul");
        list.className = "moderation-profile__history";
        for (const entry of historyEntries) {
        const item = document.createElement("li");
        const detail = document.createElement("p");
        const reference = entry.decisionHistoryId ? " · Decisão #" + entry.decisionHistoryId : "";
        detail.textContent = (entry.delta > 0 ? "+" : "") + entry.delta + " ponto(s) · "
            + labelOrigin(entry.origin) + reference + " \u00b7 "
            + (entry.occurredAt ? formatDate.format(Date.parse(entry.occurredAt)) : "data não informada");
        const explanation = document.createElement("p");
        explanation.textContent = entry.decisionJustification || entry.justification || "Sem justificativa disponível.";
        item.append(detail, explanation);
        if (entry.origin === "UPHELD_DECISION" && entry.decisionHistoryId && !appealByDecision.has(entry.decisionHistoryId)
            && (netByDecision.get(entry.decisionHistoryId) || 0) > 0) {
            const appeal = document.createElement("button");
            appeal.type = "button";
            appeal.className = "botao botao-secundario";
            appeal.textContent = "Contestar esta decisão";
            appeal.addEventListener("click", () => submitAppeal(appeal, entry.decisionHistoryId, container, endpoint));
            item.append(appeal);
        }
            list.append(item);
        }
        container.append(list);
    }

    const appeals = Array.isArray(subject.appeals) ? subject.appeals : [];
    if (appeals.length) {
        const appealsHeading = document.createElement("h4");
        appealsHeading.textContent = "Recursos";
        container.append(appealsHeading);
        const appealList = document.createElement("ul");
        appealList.className = "moderation-profile__history";
        for (const appeal of appeals) {
        const item = document.createElement("li");
        const title = document.createElement("p");
        title.textContent = "Recurso #" + appeal.id + " \u00b7 " + labelAppeal(appeal.status) + " \u00b7 "
            + (appeal.createdAt ? formatDate.format(Date.parse(appeal.createdAt)) : "data não informada");
        const explanation = document.createElement("p");
        explanation.textContent = appeal.response || appeal.justification;
        item.append(title, explanation);
            appealList.append(item);
        }
        container.append(appealList);
    }
    return true;
}

function renderMutationRestrictionNotices(container, subjectOrSubjects) {
    const subjects = Array.isArray(subjectOrSubjects) ? subjectOrSubjects : [subjectOrSubjects];
    const active = subjects.flatMap(subject => {
        const scope = subject.subjectType === "USER" ? "Sua conta" : "Esta ONG";
        const restrictions = [];
        if (subject.permanentRestrictionActive === true) restrictions.push({ permanent: true, threshold: 10 });
        if (subject.temporaryRestrictionActive === true && subject.temporaryRestrictionEndsAt)
            restrictions.push({ permanent: false, threshold: 5 });
        return restrictions.map(restriction => ({ subject, scope, restriction }));
    });
    if (!active.length) return false;
    const section = document.createElement("section");
    section.className = "moderation-profile__restriction-notices";
    section.setAttribute("aria-label", "Restri\u00e7\u00f5es para publicar ou editar");
    for (const { subject, scope, restriction } of active) {
        const notice = document.createElement("article");
        notice.className = restriction.permanent
            ? "moderation-profile__restriction moderation-profile__restriction--permanent"
            : "moderation-profile__restriction moderation-profile__restriction--temporary";
        const heading = document.createElement("h4");
        heading.textContent = restriction.permanent
            ? scope + " est\u00e1 permanentemente impedida de publicar ou alterar conte\u00fado."
            : scope + " est\u00e1 temporariamente impedida de publicar ou alterar conte\u00fado.";
        const reason = document.createElement("p");
        reason.textContent = "Pontua\u00e7\u00e3o atual: " + subject.score + ". O limite de " + restriction.threshold
            + " pontos foi atingido. Esta san\u00e7\u00e3o restringe altera\u00e7\u00f5es; leitura, seguran\u00e7a da conta e recursos continuam dispon\u00edveis.";
        notice.append(heading, reason);
        if (restriction.permanent) {
            const end = document.createElement("p");
            end.className = "moderation-profile__restriction-deadline";
            end.textContent = "Restri\u00e7\u00e3o permanente: n\u00e3o possui data autom\u00e1tica de t\u00e9rmino.";
            notice.append(end);
        } else {
            const end = document.createElement("p");
            end.className = "moderation-profile__restriction-deadline";
            end.dataset.restrictionCountdown = "true";
            end.dataset.endsAt = subject.temporaryRestrictionEndsAt;
            end.dataset.serverTime = subject.serverTime || "";
            end.dataset.receivedAt = String(Date.now());
            end.textContent = countdownText(subject.temporaryRestrictionEndsAt, subject.serverTime, Date.now());
            notice.append(end);
        }
        const causes = (subject.pointsHistory || subject.reasons || []).filter(entry =>
            Number(entry.delta ?? entry.pointsDelta ?? 1) > 0).slice(0, 5);
        if (causes.length) {
            const causeHeading = document.createElement("strong");
            causeHeading.textContent = "Motivos e decis\u00f5es relacionados:";
            const causeList = document.createElement("ul");
            for (const entry of causes) {
                const item = document.createElement("li");
                const decisionId = entry.decisionHistoryId;
                const reference = decisionId ? "Decis\u00e3o #" + decisionId : labelOrigin(entry.origin);
                item.textContent = reference + " \u00b7 "
                    + (entry.decisionJustification || entry.justification || "Justificativa n\u00e3o dispon\u00edvel.");
                causeList.append(item);
            }
            notice.append(causeHeading, causeList);
        }
        const historyLink = document.createElement("a");
        historyLink.className = "moderation-profile__history-link";
        historyLink.href = subject.subjectType === "USER"
            ? (container.closest("#moderacao-perfil-usuario") ? "#moderacao-perfil-usuario" : "/pages/perfil.html#configuracoes")
            : "#configuracoes";
        historyLink.textContent = "Ver hist\u00f3rico e recursos";
        historyLink.addEventListener("click", event => {
            if (subject.subjectType === "USER" && !container.closest("#moderacao-perfil-usuario")) return;
            if (subject.subjectType === "OSC" && container.closest("#moderacao-configuracoes-osc")) return;
            const settingsTab = document.getElementById("aba-dados");
            if (!settingsTab) return;
            event.preventDefault();
            const dialog = container.closest("dialog");
            if (dialog?.open) dialog.close();
            settingsTab.click();
            requestAnimationFrame(() => document.getElementById("moderacao-configuracoes-osc")?.scrollIntoView({ behavior: "smooth", block: "center" }));
        });
        notice.append(historyLink);
        section.append(notice);
    }
    container.append(section);
    return true;
}

function countdownText(endsAtValue, serverTimeValue, receivedAt) {
    const endsAt = Date.parse(endsAtValue);
    const serverTime = Date.parse(serverTimeValue);
    if (!Number.isFinite(endsAt)) return "O prazo tempor\u00e1rio será confirmado pelo servidor.";
    if (!Number.isFinite(serverTime)) return "T\u00e9rmino previsto: " + formatDate.format(endsAt)
        + " (tempo restante indispon\u00edvel sem hor\u00e1rio confirmado pelo servidor).";
    const remaining = endsAt - (serverTime + (Date.now() - Number(receivedAt || Date.now())));
    if (remaining <= 0) return "Prazo atingido. Confirmando o estado atualizado do servidor\u2026";
    return "Restri\u00e7\u00e3o tempor\u00e1ria at\u00e9 " + formatDate.format(endsAt) + " (restam " + formatRemaining(remaining) + ").";
}

function clearRestrictionRefresh(section) {
    const timer = restrictionRefreshTimers.get(section);
    if (!timer) return;
    clearTimeout(timer.timeout);
    clearInterval(timer.interval);
    restrictionRefreshTimers.delete(section);
}

function scheduleRestrictionRefresh(section, endpoint, subject) {
    if (subject.temporaryRestrictionActive !== true || !subject.temporaryRestrictionEndsAt || !subject.serverTime) return;
    const endsAt = Date.parse(subject.temporaryRestrictionEndsAt);
    const serverTime = Date.parse(subject.serverTime);
    if (!Number.isFinite(endsAt) || !Number.isFinite(serverTime)) return;
    const localStart = Date.now();
    const update = () => {
        const remaining = endsAt - (serverTime + (Date.now() - localStart));
        for (const field of section.querySelectorAll('[data-restriction-countdown="true"]')) {
            if (remaining <= 0) field.textContent = "Prazo atingido. Confirmando o estado atualizado do servidor…";
            else field.textContent = "Período temporário termina em " + formatDate.format(endsAt)
                + " (restam " + formatRemaining(remaining) + ").";
        }
    };
    update();
    const timeout = setTimeout(() => {
        const status = section.querySelector("[data-moderation-status]");
        if (status) status.textContent = "Prazo atingido; confirmando a restrição com o servidor…";
        void carregarModeracaoPerfil(section, endpoint);
    }, Math.min(2147483000, Math.max(0, endsAt - serverTime) + 500));
    const interval = setInterval(update, 60000);
    restrictionRefreshTimers.set(section, { timeout, interval });
}

function formatRemaining(milliseconds) {
    const totalMinutes = Math.max(0, Math.ceil(milliseconds / 60000));
    const days = Math.floor(totalMinutes / 1440);
    const hours = Math.floor((totalMinutes % 1440) / 60);
    const minutes = totalMinutes % 60;
    if (days) return days + " dia(s) e " + hours + " hora(s)";
    if (hours) return hours + " hora(s) e " + minutes + " minuto(s)";
    return minutes + " minuto(s)";
}

async function submitAppeal(button, decisionHistoryId, container, endpoint) {
    if (button.disabled) return;
    button.disabled = true;
    try {
        const submitted = await askAppealJustification(button, async justification => {
            const response = await fetchAutenticado("/moderation/appeals", {
                method: "POST", headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ decisionHistoryId, justificativa: justification })
            });
            if (response.status === 401) throw new Error("Sua sess\u00e3o expirou. Entre novamente.");
            if (response.status === 403) throw new Error("Voc\u00ea n\u00e3o pode contestar esta decis\u00e3o.");
            if (response.status === 409) throw new Error("J\u00e1 h\u00e1 um recurso para esta decis\u00e3o ou os pontos j\u00e1 foram revertidos.");
            if (!response.ok) throw new Error("N\u00e3o foi poss\u00edvel enviar o recurso.");
        });
        if (!submitted) return;
        const section = container.closest("[data-moderation-section]");
        const status = section?.querySelector("[data-moderation-status]")
            || document.querySelector("#moderacao-organizacoes-usuario [data-managed-ngo-status]");
        if (status) status.textContent = "Recurso enviado para an\u00e1lise.";
        if (section) await carregarModeracaoPerfil(section, endpoint);
        else {
            const response = await fetchAutenticado(endpoint, { headers: { Accept: "application/json" } });
            if (!response.ok) throw new Error("O recurso foi enviado, mas o histórico não pôde ser atualizado.");
            renderSubject(container, await response.json(), endpoint);
        }
    } catch (error) {
        const section = container.closest("[data-moderation-section]");
        const status = section?.querySelector("[data-moderation-status]")
            || document.querySelector("#moderacao-organizacoes-usuario [data-managed-ngo-status]");
        if (status) status.textContent = error?.message || "N\u00e3o foi poss\u00edvel enviar o recurso.";
    } finally {
        if (button.isConnected) {
            button.disabled = false;
            button.textContent = "Contestar esta decis\u00e3o";
        }
    }
}

function askAppealJustification(trigger, onSubmit) {
    return new Promise(resolve => {
        const dialog = document.createElement("dialog");
        dialog.className = "dialogo-verificacao-telefone moderation-profile__appeal-dialog";
        dialog.setAttribute("aria-labelledby", "titulo-recurso-perfil");
        dialog.setAttribute("aria-describedby", "descricao-recurso-perfil");
        const content = document.createElement("div");
        content.className = "dialogo-verificacao-telefone__conteudo";
        const title = document.createElement("h2");
        title.id = "titulo-recurso-perfil";
        title.textContent = "Contestar decis\u00e3o";
        const description = document.createElement("p");
        description.id = "descricao-recurso-perfil";
        description.textContent = "Explique por que deseja recorrer. O mesmo fundamento s\u00f3 pode ser apresentado uma vez para cada decis\u00e3o.";
        const form = document.createElement("form");
        form.className = "moderation-profile__appeal-form";
        const field = document.createElement("div");
        field.className = "moderation-profile__appeal-field";
        const label = document.createElement("label");
        label.htmlFor = "justificativa-recurso-perfil";
        label.textContent = "Justificativa";
        const textarea = document.createElement("textarea");
        textarea.id = label.htmlFor;
        textarea.maxLength = 2000;
        textarea.rows = 5;
        textarea.required = true;
        textarea.placeholder = "Descreva os motivos do recurso.";
        field.append(label, textarea);
        const error = document.createElement("p");
        error.className = "moderation-profile__appeal-error";
        error.setAttribute("role", "alert");
        error.setAttribute("aria-live", "polite");
        const actions = document.createElement("div");
        actions.className = "moderation-profile__appeal-actions";
        const cancel = document.createElement("button");
        cancel.type = "button";
        cancel.className = "botao botao-secundario";
        cancel.textContent = "Voltar";
        const submit = document.createElement("button");
        submit.type = "submit";
        submit.className = "botao moderation-profile__appeal-submit";
        submit.textContent = "Enviar recurso";
        actions.append(cancel, submit);
        form.append(field, error, actions);
        content.append(title, description, form);
        dialog.append(content);
        document.body.append(dialog);
        let resolved = false;
        const finish = result => {
            if (resolved) return;
            resolved = true;
            if (dialog.open) dialog.close();
            dialog.remove();
            if (trigger?.isConnected) trigger.focus();
            resolve(result);
        };
        cancel.addEventListener("click", () => finish(false));
        dialog.addEventListener("cancel", event => { event.preventDefault(); finish(false); });
        form.addEventListener("submit", async event => {
            event.preventDefault();
            if (submit.disabled || !form.reportValidity()) return;
            const justification = textarea.value.trim();
            if (!justification) {
                error.textContent = "Informe a justificativa do recurso.";
                textarea.setAttribute("aria-invalid", "true");
                textarea.focus();
                return;
            }
            submit.disabled = true;
            submit.textContent = "Enviando\u2026";
            error.textContent = "";
            try {
                await onSubmit(justification);
                finish(true);
            } catch (failure) {
                error.textContent = failure?.message || "N\u00e3o foi poss\u00edvel enviar o recurso.";
                submit.disabled = false;
                submit.textContent = "Enviar recurso";
                textarea.focus();
            }
        });
        dialog.showModal();
        textarea.focus();
    });
}

function labelOrigin(origin) {
    return ({
        UPHELD_DECISION: "decis\u00e3o de modera\u00e7\u00e3o",
        MANUAL_ADJUSTMENT: "ajuste administrativo",
        APPEAL_CORRECTION: "corre\u00e7\u00e3o de recurso"
    })[origin] || "lan\u00e7amento";
}

function labelAppeal(status) {
    return ({ PENDING: "Pendente", REJECTED: "Rejeitado", PARTIAL: "Parcialmente aceito", ACCEPTED: "Aceito" })[status]
        || "Status n\u00e3o informado";
}

function initializeModerationProfile() {
    const section = document.getElementById("moderacao-perfil-usuario");
    if (section) void carregarModeracaoPerfil(section, "/moderation/me");
    const organizations = document.getElementById("moderacao-organizacoes-usuario");
    if (!organizations || organizations.dataset.initialized === "true") return;
    organizations.dataset.initialized = "true";
    const state = { loaded: false, loading: null, openId: null, requests: new Map() };
    organizationStates.set(organizations, state);
    organizations.addEventListener("toggle", () => {
        if (organizations.open) void carregarResumoOrganizacoes(organizations, state);
        else fecharHistoricoOrganizacao(organizations, state);
    });
    void carregarResumoOrganizacoes(organizations, state);
}

async function carregarResumoOrganizacoes(section, state) {
    if (state.loaded || state.loading) return state.loading;
    const status = section.querySelector("[data-managed-ngo-status]");
    const content = section.querySelector("[data-managed-ngo-content]");
    status.textContent = "Carregando organiza\u00e7\u00f5es administradas\u2026";
    status.setAttribute("aria-busy", "true");
    state.loading = (async () => {
        try {
            const response = await fetchAutenticado("/moderation/organizations", { headers: { Accept: "application/json" } });
            if (response.status === 403) throw new Error("Somente CRIADOR ou ADMINISTRADOR pode consultar o hist\u00f3rico de uma ONG.");
            if (!response.ok) throw new Error("N\u00e3o foi poss\u00edvel carregar os resumos de modera\u00e7\u00e3o das ONGs.");
            const summaries = await response.json();
            if (!Array.isArray(summaries)) throw new Error("Resposta de organiza\u00e7\u00f5es inv\u00e1lida.");
            state.loaded = true;
            content.replaceChildren();
            const eligible = summaries.filter(possuiHistoricoModeracaoOng);
            for (const subject of eligible) criarLinhaOrganizacao(section, state, subject, content);
            status.textContent = "";
            status.hidden = true;
            section.hidden = eligible.length === 0;
        } catch (error) {
            content.replaceChildren();
            status.textContent = error?.message || "N\u00e3o foi poss\u00edvel carregar as ONGs administradas.";
            status.hidden = false;
            section.hidden = false;
            const retry = document.createElement("button");
            retry.type = "button";
            retry.className = "botao botao-secundario";
            retry.textContent = "Tentar novamente";
            retry.addEventListener("click", () => { state.loading = null; state.loaded = false; void carregarResumoOrganizacoes(section, state); });
            content.append(retry);
        } finally {
            state.loading = null;
            status.setAttribute("aria-busy", "false");
        }
    })();
    return state.loading;
}

function criarLinhaOrganizacao(section, state, subject, container) {
    const row = document.createElement("article");
    row.className = "moderation-profile__ngo";
    const button = document.createElement("button");
    button.type = "button";
    button.className = "moderation-profile__ngo-toggle";
    button.id = "moderacao-ong-toggle-" + subject.subjectId;
    button.setAttribute("aria-expanded", "false");
    const panelId = "moderacao-ong-history-" + subject.subjectId;
    button.setAttribute("aria-controls", panelId);
    const name = document.createElement("span");
    name.className = "moderation-profile__ngo-name";
    name.textContent = subject.subjectLabel || "ONG #" + subject.subjectId;
    const score = document.createElement("span");
    score.className = "moderation-profile__ngo-score";
    score.textContent = subject.score + " ponto(s)";
    button.append(name, score);
    const panel = document.createElement("div");
    panel.id = panelId;
    panel.className = "moderation-profile__ngo-history";
    panel.setAttribute("role", "region");
    panel.setAttribute("aria-labelledby", button.id);
    panel.hidden = true;
    const status = document.createElement("p");
    status.setAttribute("role", "status");
    status.setAttribute("aria-live", "polite");
    const content = document.createElement("div");
    content.className = "moderation-profile__ngo-history-content";
    panel.append(status, content);
    row.append(button, panel);
    button.addEventListener("click", () => void alternarHistoricoOrganizacao(section, state, subject, button, panel, status, content));
    container.append(row);
}

function fecharHistoricoOrganizacao(section, state) {
    if (state.openId === null) return;
    const button = section.querySelector("#moderacao-ong-toggle-" + state.openId);
    const panel = section.querySelector("#moderacao-ong-history-" + state.openId);
    if (button) button.setAttribute("aria-expanded", "false");
    if (panel) panel.hidden = true;
    state.openId = null;
}

async function alternarHistoricoOrganizacao(section, state, summary, button, panel, status, content) {
    const id = Number(summary.subjectId);
    if (state.openId === id) {
        button.setAttribute("aria-expanded", "false");
        panel.hidden = true;
        state.openId = null;
        return;
    }
    fecharHistoricoOrganizacao(section, state);
    state.openId = id;
    button.setAttribute("aria-expanded", "true");
    panel.hidden = false;
    status.textContent = "Carregando hist\u00f3rico e recursos\u2026";
    panel.setAttribute("aria-busy", "true");
    try {
        let request = state.requests.get(id);
        if (!request) {
            request = fetchAutenticado("/osc/" + encodeURIComponent(id) + "/moderation", {
                headers: { Accept: "application/json" }
            }).then(async response => {
                if (response.status === 403) throw new Error("Seu v\u00ednculo atual n\u00e3o autoriza consultar esta ONG.");
                if (!response.ok) throw new Error("N\u00e3o foi poss\u00edvel carregar o hist\u00f3rico desta ONG.");
                return response.json();
            });
            state.requests.set(id, request);
        }
        const detail = await request;
        state.requests.delete(id);
        if (state.openId !== id) return;
        renderSubject(content, detail, "/osc/" + encodeURIComponent(id) + "/moderation");
        status.textContent = "";
    } catch (error) {
        state.requests.delete(id);
        content.replaceChildren();
        status.textContent = error?.message || "N\u00e3o foi poss\u00edvel carregar o hist\u00f3rico desta ONG.";
        const retry = document.createElement("button");
        retry.type = "button";
        retry.className = "botao botao-secundario";
        retry.textContent = "Tentar novamente";
        retry.addEventListener("click", () => void alternarHistoricoOrganizacao(section, state, summary, button, panel, status, content));
        content.append(retry);
    } finally {
        panel.setAttribute("aria-busy", "false");
    }
}

const mutationNoticeCache = new Map();
const writeRestrictionRefreshTimers = new Map();
let activeWriteRestrictionKey = null;
let writeRestrictionRequestVersion = 0;
export async function carregarAvisoRestricaoConteudo(oscId, podeConsultarONG, painelModeracaoONG = null, force = false) {
    const panel = document.getElementById("painel-conteudo");
    const notices = [...document.querySelectorAll("[data-points-write-notice]")];
    if (!panel || !notices.length || !oscId) return;
    const key = String(oscId) + ":" + String(Boolean(podeConsultarONG));
    const pendingRequest = mutationNoticeCache.get(key);
    if (!force && pendingRequest && activeWriteRestrictionKey === key) return pendingRequest;
    if (pendingRequest) mutationNoticeCache.delete(key);
    activeWriteRestrictionKey = key;
    const requestVersion = ++writeRestrictionRequestVersion;
    for (const oldKey of writeRestrictionRefreshTimers.keys()) if (oldKey !== key) clearWriteRestrictionTimer(oldKey);
    clearWriteRestrictionTimer(key);
    const isCurrentRequest = () => activeWriteRestrictionKey === key && requestVersion === writeRestrictionRequestVersion;
    let request;
    request = (async () => {
        setWriteFormsState("checking");
        for (const notice of notices) {
            notice.replaceChildren();
            notice.hidden = false;
            notice.textContent = "Verificando restri\u00e7\u00f5es de publica\u00e7\u00e3o com o servidor\u2026";
        }
        try {
            const userResponse = await fetchAutenticado("/moderation/me", { headers: { Accept: "application/json" } });
            if (!isCurrentRequest()) return;
            if (userResponse.status === 503) {
                setWriteFormsState("allowed");
                notices.forEach(notice => { notice.replaceChildren(); notice.hidden = true; });
                return;
            }
            if (!userResponse.ok) throw new Error(userResponse.status === 401
                ? "Sua sess\u00e3o expirou. Entre novamente para consultar as restri\u00e7\u00f5es."
                : "N\u00e3o foi poss\u00edvel confirmar as restri\u00e7\u00f5es da conta.");
            const userSubject = await userResponse.json();
            if (!isCurrentRequest()) return;
            let organizationSubject = null;
            if (podeConsultarONG) {
                organizationSubject = painelModeracaoONG instanceof Promise ? await painelModeracaoONG : painelModeracaoONG;
                if (!isCurrentRequest()) return;
                if (!organizationSubject) {
                    const orgResponse = await fetchAutenticado("/osc/" + encodeURIComponent(oscId) + "/moderation", {
                        headers: { Accept: "application/json" }
                    });
                    if (!isCurrentRequest()) return;
                    if (orgResponse.status === 503) {
                        setWriteFormsState("allowed");
                        notices.forEach(notice => { notice.replaceChildren(); notice.hidden = true; });
                        return;
                    }
                    if (!orgResponse.ok) throw new Error("N\u00e3o foi poss\u00edvel confirmar as restri\u00e7\u00f5es desta ONG.");
                    organizationSubject = await orgResponse.json();
                    if (!isCurrentRequest()) return;
                }
            } else {
                const orgResponse = await fetchAutenticado("/osc/" + encodeURIComponent(oscId) + "/moderation/restriction", {
                    headers: { Accept: "application/json" }
                });
                if (!isCurrentRequest()) return;
                if (orgResponse.status === 503) {
                    setWriteFormsState("allowed");
                    notices.forEach(notice => { notice.replaceChildren(); notice.hidden = true; });
                    return;
                }
                if (!orgResponse.ok) throw new Error("N\u00e3o foi poss\u00edvel confirmar as restri\u00e7\u00f5es desta ONG.");
                organizationSubject = await orgResponse.json();
                if (!isCurrentRequest()) return;
            }
            const subjects = [userSubject, ...(organizationSubject ? [organizationSubject] : [])];
            if (subjects.some(subject => subject.temporaryRestrictionActive === true
                    && (!Number.isFinite(Date.parse(subject.temporaryRestrictionEndsAt))
                        || !Number.isFinite(Date.parse(subject.serverTime)))))
                throw new Error("N\u00e3o foi poss\u00edvel confirmar o prazo da restri\u00e7\u00e3o com o servidor.");
            const blocked = subjects.some(subject => subject.permanentRestrictionActive === true
                || subject.temporaryRestrictionActive === true);
            setWriteFormsState(blocked ? "blocked" : "allowed");
            for (const notice of notices) {
                notice.replaceChildren();
                notice.hidden = !renderMutationRestrictionNotices(notice, subjects);
            }
            scheduleWriteRestrictionRefresh(oscId, podeConsultarONG, subjects, key);
        } catch (error) {
            if (!isCurrentRequest()) return;
            setWriteFormsState("unknown");
            for (const notice of notices) {
                notice.replaceChildren();
                notice.hidden = false;
                const message = document.createElement("p");
                message.textContent = error?.message || "N\u00e3o foi poss\u00edvel verificar as restri\u00e7\u00f5es de modera\u00e7\u00e3o.";
                const retry = document.createElement("button");
                retry.type = "button";
                retry.className = "botao botao-secundario moderation-profile__retry-restrictions";
                retry.textContent = "Tentar novamente";
                retry.addEventListener("click", () => {
                    mutationNoticeCache.delete(key);
                    void carregarAvisoRestricaoConteudo(oscId, podeConsultarONG, painelModeracaoONG, true);
                });
                notice.append(message, retry);
            }
        } finally {
            if (mutationNoticeCache.get(key) === request) mutationNoticeCache.delete(key);
        }
    })();
    mutationNoticeCache.set(key, request);
    return request;
}

function setWriteFormsState(state) {
    for (const form of document.querySelectorAll("#form-post-osc,#form-oportunidade-osc,#form-edicao-osc")) {
        form.dataset.pointsRestrictionState = state;
        const submit = form.querySelector("button[type=submit]");
        if (submit && form.dataset.salvando !== "true")
            submit.disabled = state === "blocked" || state === "checking" || state === "unknown";
    }
    const editButton = document.getElementById("alterar-dados-osc");
    if (editButton) editButton.disabled = state === "blocked" || state === "checking" || state === "unknown";
}

function clearWriteRestrictionTimer(key) {
    const timer = writeRestrictionRefreshTimers.get(key);
    if (!timer) return;
    clearTimeout(timer.timeout);
    clearInterval(timer.interval);
    writeRestrictionRefreshTimers.delete(key);
}

function scheduleWriteRestrictionRefresh(oscId, canRead, subjects, key) {
    clearWriteRestrictionTimer(key);
    const timed = subjects.filter(subject => subject.temporaryRestrictionActive
        && subject.temporaryRestrictionEndsAt && subject.serverTime);
    if (!timed.length) return;
    const earliestDelay = Math.min(...timed.map(subject => Math.max(0,
        Date.parse(subject.temporaryRestrictionEndsAt) - Date.parse(subject.serverTime))));
    const update = () => {
        for (const field of document.querySelectorAll("[data-restriction-countdown=true]")) {
            field.textContent = countdownText(field.dataset.endsAt, field.dataset.serverTime, field.dataset.receivedAt);
        }
    };
    update();
    const interval = setInterval(update, 60000);
    const timeout = setTimeout(() => {
        setWriteFormsState("checking");
        document.querySelectorAll("[data-points-write-notice]").forEach(notice => {
            if (!notice.hidden) notice.textContent = "O prazo informado foi atingido. Confirmando a situa\u00e7\u00e3o atual com o servidor\u2026";
        });
        mutationNoticeCache.delete(key);
        void carregarAvisoRestricaoConteudo(oscId, canRead, null, true);
    }, Math.min(2147483000, earliestDelay + 500));
    writeRestrictionRefreshTimers.set(key, { timeout, interval });
}

document.addEventListener("DOMContentLoaded", initializeModerationProfile);


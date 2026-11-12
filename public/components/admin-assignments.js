import {
    AdminRequestError,
    fetchAdminAssignments,
    fetchAdminSession,
    mutateAdminAssignment
} from "./admin-rbac.js";

const PAGE_SIZE = 50;
const PROFILE_LABELS = Object.freeze({
    SUPER_ADMIN: "Superadministrador",
    PLATFORM_ADMIN: "Administrador da plataforma",
    MODERATOR: "Moderador",
    AUDITOR: "Auditor"
});
const STATUS_LABELS = Object.freeze({ ACTIVE: "Ativa", REVOKED: "Revogada" });

function formatDate(value) {
    return new Intl.DateTimeFormat("pt-BR", {
        dateStyle: "short",
        timeStyle: "short"
    }).format(new Date(value));
}

function addCell(row, value) {
    const cell = document.createElement("td");
    cell.textContent = value;
    row.append(cell);
    return cell;
}

function errorMessage(error) {
    if (error instanceof AdminRequestError && error.kind === "network") {
        return "Não foi possível conectar ao serviço administrativo. Tente novamente.";
    }
    if (error instanceof AdminRequestError && error.status === 503) {
        return "O serviço está temporariamente indisponível. Tente novamente.";
    }
    return "Não foi possível carregar as atribuições. Verifique a conexão e tente novamente.";
}

export function initializeAdminAssignments({ apiUrl, authorization, onUnauthorized }) {
    const elements = {
        page: document.getElementById("administradores-admin"),
        status: document.getElementById("status-administradores"),
        loading: document.getElementById("carregando-administradores"),
        empty: document.getElementById("lista-administradores-vazia"),
        error: document.getElementById("erro-administradores"),
        errorMessage: document.getElementById("mensagem-erro-administradores"),
        denied: document.getElementById("acesso-negado-administradores"),
        deniedMessage: document.getElementById("mensagem-acesso-negado-administradores"),
        table: document.getElementById("tabela-administradores-wrap"),
        rows: document.getElementById("linhas-administradores"),
        pagination: document.getElementById("paginacao-administradores"),
        pageLabel: document.getElementById("pagina-atual-administradores"),
        previous: document.getElementById("pagina-anterior-administradores"),
        next: document.getElementById("proxima-pagina-administradores"),
        add: document.getElementById("adicionar-administrador"),
        refresh: document.getElementById("atualizar-administradores"),
        retry: document.getElementById("tentar-novamente-administradores"),
        dialog: document.getElementById("dialogo-atribuicao-admin"),
        form: document.getElementById("formulario-atribuicao-admin"),
        dialogTitle: document.getElementById("titulo-dialogo-atribuicao"),
        dialogDescription: document.getElementById("descricao-dialogo-atribuicao"),
        userField: document.getElementById("campo-id-usuario-admin"),
        userId: document.getElementById("id-usuario-admin"),
        profileField: document.getElementById("campo-perfil-admin"),
        profile: document.getElementById("perfil-atribuicao-admin"),
        currentProfile: document.getElementById("perfil-atual-atribuicao-admin"),
        reason: document.getElementById("motivo-atribuicao-admin"),
        fields: document.getElementById("campos-atribuicao-admin"),
        review: document.getElementById("revisao-atribuicao-admin"),
        reviewSummary: document.getElementById("resumo-operacao-atribuicao"),
        reviewReason: document.getElementById("resumo-motivo-atribuicao"),
        invariantWarning: document.getElementById("aviso-invariante-super-admin"),
        sessionWarning: document.getElementById("aviso-revogacao-sessoes"),
        dialogError: document.getElementById("erro-operacao-atribuicao"),
        cancel: document.getElementById("cancelar-atribuicao-admin"),
        back: document.getElementById("voltar-atribuicao-admin"),
        reviewButton: document.getElementById("revisar-atribuicao-admin"),
        confirm: document.getElementById("confirmar-atribuicao-admin")
    };

    let offset = 0;
    let hasMore = false;
    let isLoading = false;
    let isMutating = false;
    let listController = null;
    let listVersion = 0;
    let viewVersion = 0;
    let modalOperation = null;
    let triggerElement = null;
    let uncertainOutcome = false;
    let currentAssignments = [];

    function showStatus(message, kind = "info") {
        elements.status.textContent = message;
        elements.status.dataset.kind = kind;
        elements.status.hidden = !message;
    }

    function hideListStates() {
        elements.loading.hidden = true;
        elements.empty.hidden = true;
        elements.error.hidden = true;
        elements.denied.hidden = true;
        elements.table.hidden = true;
        elements.pagination.hidden = true;
    }

    function showDenied(message = "Seu perfil não pode gerenciar atribuições administrativas.") {
        hideListStates();
        elements.rows.replaceChildren();
        currentAssignments = [];
        elements.add.hidden = true;
        elements.deniedMessage.textContent = message;
        elements.denied.hidden = false;
    }

    function showError(message) {
        hideListStates();
        elements.rows.replaceChildren();
        currentAssignments = [];
        elements.add.hidden = true;
        elements.errorMessage.textContent = message;
        elements.error.hidden = false;
    }

    function renderAssignmentRow(assignment) {
        const row = document.createElement("tr");
        addCell(row, String(assignment.userId));
        addCell(row, PROFILE_LABELS[assignment.profile]);

        const statusCell = document.createElement("td");
        const status = document.createElement("span");
        status.className = `admin-status-pill admin-status-pill--${assignment.status.toLowerCase()}`;
        status.textContent = STATUS_LABELS[assignment.status];
        statusCell.append(status);
        row.append(statusCell);

        addCell(row, formatDate(assignment.grantedAt));
        addCell(row, formatDate(assignment.lastModifiedAt));

        const actions = document.createElement("td");
        if (assignment.status === "ACTIVE") {
            const group = document.createElement("div");
            group.className = "admin-assignment-actions";
            const change = document.createElement("button");
            change.type = "button";
            change.className = "admin-secondary-button";
            change.dataset.assignmentAction = "change-profile";
            change.dataset.assignmentId = assignment.id;
            change.setAttribute("aria-label", `Alterar perfil da conta ${assignment.userId}`);
            change.textContent = "Alterar perfil";
            const revoke = document.createElement("button");
            revoke.type = "button";
            revoke.className = "admin-secondary-button";
            revoke.dataset.assignmentAction = "revoke";
            revoke.dataset.assignmentId = assignment.id;
            revoke.setAttribute("aria-label", `Revogar acesso administrativo da conta ${assignment.userId}`);
            revoke.textContent = "Revogar";
            group.append(change, revoke);
            actions.append(group);
        } else {
            actions.textContent = "—";
        }
        row.append(actions);
        return row;
    }

    function renderRows(assignments, pageOffset, moreAvailable) {
        currentAssignments = assignments;
        elements.rows.replaceChildren(...assignments.map(renderAssignmentRow));
        elements.loading.hidden = true;
        elements.error.hidden = true;
        elements.denied.hidden = true;
        elements.add.hidden = false;
        if (assignments.length === 0) {
            elements.empty.hidden = false;
            elements.table.hidden = true;
            elements.pagination.hidden = true;
            return;
        }

        elements.empty.hidden = true;
        elements.table.hidden = false;
        elements.pagination.hidden = false;
        elements.previous.disabled = pageOffset === 0;
        elements.next.disabled = !moreAvailable;
        elements.pageLabel.textContent = `Página ${Math.floor(pageOffset / PAGE_SIZE) + 1}`
            + (moreAvailable ? " · Há mais resultados" : "");
    }

    async function loadPage(pageOffset = offset) {
        if (!authorization.hasPermission("ADMIN_MANAGE")) {
            showDenied();
            return;
        }
        listController?.abort();
        listController = new AbortController();
        const controller = listController;
        const currentVersion = ++listVersion;
        isLoading = true;
        currentAssignments = [];
        elements.add.hidden = true;
        elements.refresh.disabled = true;
        elements.retry.disabled = true;
        elements.previous.disabled = true;
        elements.next.disabled = true;
        elements.rows.replaceChildren();
        hideListStates();
        elements.loading.hidden = false;

        try {
            const results = await fetchAdminAssignments(
                apiUrl, PAGE_SIZE + 1, pageOffset, fetch, controller.signal);
            if (currentVersion !== listVersion) return;
            const session = await fetchAdminSession(apiUrl, fetch, controller.signal);
            if (currentVersion !== listVersion) return;
            const items = results.slice(0, PAGE_SIZE);
            offset = pageOffset;
            hasMore = results.length > PAGE_SIZE;
            renderRows(items, pageOffset, hasMore);
            return session;
        } catch (error) {
            if (error?.name === "AbortError" || currentVersion !== listVersion) return;
            if (error instanceof AdminRequestError && error.kind === "unauthorized") {
                elements.rows.replaceChildren();
                onUnauthorized();
            } else if (error instanceof AdminRequestError && error.kind === "forbidden") {
                showDenied("O servidor recusou a consulta por falta de autorização. Sua sessão não foi encerrada.");
            } else {
                showError(errorMessage(error));
            }
        } finally {
            if (currentVersion === listVersion) {
                isLoading = false;
                listController = null;
                elements.refresh.disabled = false;
                elements.retry.disabled = false;
            }
        }
    }

    function closeDialog() {
        if (isMutating) return;
        if (elements.dialog.open) elements.dialog.close();
    }

    function resetDialog() {
        elements.form.reset();
        elements.fields.hidden = false;
        elements.review.hidden = true;
        elements.userField.hidden = false;
        elements.userId.disabled = false;
        elements.userId.required = false;
        elements.profileField.hidden = false;
        elements.profile.disabled = false;
        elements.profile.required = true;
        elements.currentProfile.hidden = true;
        elements.dialogError.hidden = true;
        elements.dialogError.textContent = "";
        elements.invariantWarning.hidden = true;
        elements.sessionWarning.hidden = true;
        elements.cancel.disabled = false;
        elements.back.hidden = true;
        elements.reviewButton.hidden = false;
        elements.reviewButton.disabled = false;
        elements.confirm.hidden = true;
        elements.confirm.disabled = false;
        elements.confirm.textContent = "Confirmar";
        elements.cancel.textContent = "Cancelar";
        modalOperation = null;
        isMutating = false;
        uncertainOutcome = false;
    }

    function openDialog(operation, assignment = null, trigger = document.activeElement) {
        if (elements.dialog.open || isMutating) return;
        triggerElement = trigger;
        modalOperation = { operation, assignment };
        uncertainOutcome = false;
        resetDialog();
        modalOperation = { operation, assignment };

        const userId = assignment?.userId;
        if (operation === "assign") {
            elements.dialogTitle.textContent = "Adicionar administrador";
            elements.dialogDescription.textContent = "Atribua um perfil a uma conta existente. O servidor valida a conta, impede autoconcessão e preserva o acesso regular do usuário.";
            elements.userField.hidden = false;
            elements.userId.disabled = false;
            elements.userId.required = true;
            elements.profileField.hidden = false;
            elements.profile.required = true;
            elements.currentProfile.hidden = true;
        } else if (operation === "change-profile") {
            elements.dialogTitle.textContent = "Alterar perfil administrativo";
            elements.dialogDescription.textContent = "O servidor impede autoalteração e protege o último SUPER_ADMIN elegível. Sessões privilegiadas da conta serão invalidadas após a alteração.";
            elements.userField.hidden = true;
            elements.userId.disabled = true;
            elements.userId.required = false;
            elements.profileField.hidden = false;
            elements.profile.required = true;
            elements.profile.value = assignment.profile;
            elements.currentProfile.textContent = `Conta ${userId} · perfil atual: ${PROFILE_LABELS[assignment.profile]}`;
            elements.currentProfile.hidden = false;
        } else {
            elements.dialogTitle.textContent = "Revogar acesso administrativo";
            elements.dialogDescription.textContent = "O servidor impede autorrevogação e protege o último SUPER_ADMIN elegível. A conta regular não será excluída; sessões privilegiadas serão invalidadas.";
            elements.userField.hidden = true;
            elements.userId.disabled = true;
            elements.userId.required = false;
            elements.profileField.hidden = true;
            elements.profile.disabled = true;
            elements.profile.required = false;
            elements.profile.value = assignment.profile;
            elements.currentProfile.textContent = `Conta ${userId} · perfil a revogar: ${PROFILE_LABELS[assignment.profile]}`;
            elements.currentProfile.hidden = false;
        }

        elements.dialog.showModal();
        (operation === "assign" ? elements.userId : operation === "change-profile"
            ? elements.profile : elements.reason).focus();
    }

    function renderReview() {
        const operation = modalOperation.operation;
        const assignment = modalOperation.assignment;
        const reason = elements.reason.value.trim();
        const userId = operation === "assign" ? Number(elements.userId.value) : assignment.userId;
        const newProfile = operation === "revoke" ? assignment.profile : elements.profile.value;

        if (operation === "assign" && (!Number.isSafeInteger(userId) || userId < 1 || userId > 2147483647)) {
            elements.userId.setCustomValidity("Informe um ID de usuário válido.");
            elements.userId.reportValidity();
            return;
        }
        elements.userId.setCustomValidity("");
        if (operation === "change-profile" && newProfile === assignment.profile) {
            elements.profile.setCustomValidity("Selecione um perfil diferente do atual.");
            elements.profile.reportValidity();
            return;
        }
        elements.profile.setCustomValidity("");

        const description = operation === "assign"
            ? `Conceder ${PROFILE_LABELS[newProfile]} à conta ${userId}. A conta regular permanece disponível.`
            : operation === "change-profile"
                ? `Alterar a conta ${userId} de ${PROFILE_LABELS[assignment.profile]} para ${PROFILE_LABELS[newProfile]}.`
                : `Revogar ${PROFILE_LABELS[assignment.profile]} da conta ${userId}. A conta regular permanece disponível.`;
        elements.reviewSummary.textContent = description;
        elements.reviewReason.textContent = reason;
        elements.invariantWarning.hidden = !(operation === "revoke" && assignment.profile === "SUPER_ADMIN"
            || operation === "change-profile" && assignment.profile === "SUPER_ADMIN"
                && newProfile !== "SUPER_ADMIN");
        elements.sessionWarning.hidden = false;
        elements.fields.hidden = true;
        elements.review.hidden = false;
        elements.reviewButton.hidden = true;
        elements.back.hidden = false;
        elements.confirm.hidden = false;
        elements.confirm.focus();
    }

    function mutationErrorMessage(error) {
        if (!(error instanceof AdminRequestError)) {
            return "Não foi possível confirmar o resultado. Atualize a lista antes de tentar novamente.";
        }
        if (error.kind === "validation") return "Os dados ou a justificativa foram recusados. Revise os campos.";
        if (error.kind === "not-found") return "A atribuição ou conta não está disponível para essa operação.";
        if (error.kind === "conflict") {
            return "A operação conflita com o estado atual. A conta pode já ter uma atribuição ou a regra do último SUPER_ADMIN pode impedir a alteração.";
        }
        if (error.kind === "forbidden") return "A operação foi recusada por autorização. Sua sessão permanece ativa.";
        if (error.kind === "unauthorized") return "Sua sessão administrativa expirou ou foi revogada.";
        if (error.kind === "csrf-unavailable" || error.kind === "csrf-invalid") {
            return "Não foi possível validar a proteção da solicitação. Nenhuma alteração foi confirmada.";
        }
        if (error.kind === "network" || error.kind === "invalid-response") {
            return "Não foi possível confirmar o resultado. Atualize a lista antes de tentar novamente.";
        }
        return "Não foi possível concluir a operação. Nenhuma confirmação foi recebida.";
    }

    async function submitMutation(event) {
        event.preventDefault();
        if (isMutating || !modalOperation) return;
        if (elements.review.hidden) {
            if (elements.form.reportValidity()) renderReview();
            return;
        }
        if (uncertainOutcome) return;

        const currentView = viewVersion;
        const { operation, assignment } = modalOperation;
        const payload = {
            reason: elements.reason.value.trim()
        };
        if (operation === "assign") {
            payload.userId = Number(elements.userId.value);
            payload.profile = elements.profile.value;
        } else if (operation === "change-profile") {
            payload.profile = elements.profile.value;
        }

        isMutating = true;
        elements.confirm.disabled = true;
        elements.confirm.textContent = "Enviando…";
        elements.cancel.disabled = true;
        elements.back.disabled = true;
        elements.dialog.setAttribute("aria-busy", "true");
        try {
            await mutateAdminAssignment(
                apiUrl,
                operation,
                assignment?.id ?? null,
                payload,
                fetch
            );
            if (currentView !== viewVersion) return;
            const successMessage = operation === "assign" ? "Atribuição concedida pelo servidor."
                : operation === "change-profile" ? "Perfil alterado pelo servidor."
                    : "Acesso administrativo revogado pelo servidor.";
            isMutating = false;
            elements.dialog.close();
            showStatus(successMessage, "success");
            await loadPage(0);
        } catch (error) {
            if (currentView !== viewVersion) return;
            if (error instanceof AdminRequestError && error.kind === "unauthorized") {
                isMutating = false;
                onUnauthorized();
                return;
            }
            isMutating = false;
            elements.back.disabled = false;
            elements.cancel.disabled = false;
            elements.dialogError.textContent = mutationErrorMessage(error);
            elements.dialogError.hidden = false;
            if (error instanceof AdminRequestError
                    && ["network", "invalid-response"].includes(error.kind)) {
                uncertainOutcome = true;
                elements.confirm.textContent = "Resultado não confirmado";
                elements.cancel.textContent = "Fechar e atualizar lista";
            } else {
                elements.confirm.disabled = false;
                elements.confirm.textContent = "Confirmar";
            }
        } finally {
            elements.dialog.removeAttribute("aria-busy");
        }
    }

    elements.add.addEventListener("click", event => openDialog("assign", null, event.currentTarget));
    elements.refresh.addEventListener("click", () => loadPage(offset));
    elements.retry.addEventListener("click", () => loadPage(offset));
    elements.previous.addEventListener("click", () => loadPage(Math.max(0, offset - PAGE_SIZE)));
    elements.next.addEventListener("click", () => {
        if (hasMore) void loadPage(offset + PAGE_SIZE);
    });
    elements.rows.addEventListener("click", event => {
        const button = event.target.closest("[data-assignment-action]");
        if (!button) return;
        const current = currentAssignments.find(item => item.id === button.dataset.assignmentId);
        if (!current) return;
        openDialog(button.dataset.assignmentAction, current, button);
    });
    elements.reviewButton.addEventListener("click", () => {
        if (elements.form.reportValidity()) renderReview();
    });
    elements.back.addEventListener("click", () => {
        elements.review.hidden = true;
        elements.fields.hidden = false;
        elements.back.hidden = true;
        elements.reviewButton.hidden = false;
        elements.confirm.hidden = true;
        elements.dialogError.hidden = true;
        elements.reason.focus();
    });
    elements.cancel.addEventListener("click", closeDialog);
    elements.form.addEventListener("submit", submitMutation);
    elements.dialog.addEventListener("cancel", event => {
        if (isMutating) event.preventDefault();
    });
    elements.dialog.addEventListener("close", () => {
        const refreshAfterUncertainResult = uncertainOutcome;
        resetDialog();
        if (triggerElement?.isConnected) triggerElement.focus();
        triggerElement = null;
        if (refreshAfterUncertainResult && elements.page.hidden === false) {
            showStatus("Resultado não confirmado. A lista foi atualizada para reconciliar o estado.", "warning");
            void loadPage(offset);
        }
    });

    return Object.freeze({
        load: loadPage,
        clear() {
            viewVersion += 1;
            listVersion += 1;
            listController?.abort();
            listController = null;
            isLoading = false;
            offset = 0;
            hasMore = false;
            elements.refresh.disabled = false;
            elements.retry.disabled = false;
            currentAssignments = [];
            elements.rows.replaceChildren();
            elements.add.hidden = true;
            hideListStates();
            showStatus("");
            uncertainOutcome = false;
            triggerElement = null;
            if (elements.dialog.open) elements.dialog.close();
            isMutating = false;
            elements.page.hidden = true;
        },
        show() {
            elements.page.hidden = false;
        },
        isLoading() {
            return isLoading;
        },
        isMutating() {
            return isMutating;
        }
    });
}

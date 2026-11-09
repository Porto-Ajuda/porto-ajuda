import { getAPI_URL } from "./modoBack.js";
import {
    AdminRequestError,
    clearAdminAuthorizationState,
    clearProtectedDashboard,
    createAdminAuthorizationStore,
    fetchAdminCapabilities,
    fetchAdminDashboard,
    fetchAdminSession,
    renderPermissionNavigation
} from "./admin-rbac.js";
import { initializeAdminAssignments } from "./admin-assignments.js";

(() => {
    "use strict";

    const API_URL = getAPI_URL();
    const formatarNumero = new Intl.NumberFormat("pt-BR");
    const formatarDataHora = new Intl.DateTimeFormat("pt-BR", {
        dateStyle: "short",
        timeStyle: "short"
    });

    function aplicarPreferenciasVisuaisLocais() {
        try {
            const preferences = JSON.parse(localStorage.getItem("portoAjudaPreferencias") ?? "null");
            if (!preferences || typeof preferences !== "object" || Array.isArray(preferences)) return;
            const fontSize = Number(preferences.tamanhoFonte);
            if (Number.isFinite(fontSize)) {
                document.documentElement.style.fontSize = `${Math.min(150, Math.max(80, fontSize))}%`;
            }
            document.body.classList.toggle("modo-claro", preferences.tema === "claro");
            document.body.classList.toggle("alto-contraste", preferences.altoContraste === true);
        } catch {
            // Preferências de aparência não podem bloquear a área administrativa.
        }
    }

    aplicarPreferenciasVisuaisLocais();

    const elements = {
        content: document.getElementById("conteudo-dashboard"),
        loading: document.getElementById("estado-carregamento"),
        unauthenticated: document.getElementById("estado-nao-autenticado"),
        expired: document.getElementById("estado-sessao-expirada"),
        forbidden: document.getElementById("estado-acesso-negado"),
        forbiddenMessage: document.getElementById("mensagem-acesso-negado"),
        forbiddenRetry: document.getElementById("tentar-novamente-acesso-admin"),
        error: document.getElementById("estado-erro-indicadores"),
        errorMessage: document.getElementById("mensagem-erro-indicadores"),
        sessionStatus: document.getElementById("estado-sessao-admin"),
        metricGrid: document.querySelector(".metric-grid"),
        activityState: document.getElementById("estado-atividade-admin"),
        activityList: document.getElementById("lista-atividade-admin"),
        generatedAt: document.getElementById("indicadores-atualizados-em"),
        retry: document.getElementById("tentar-novamente-admin"),
        refresh: document.getElementById("atualizar-indicadores"),
        logout: document.getElementById("sair-painel-admin"),
        menu: document.getElementById("menu-admin"),
        menuToggle: document.getElementById("alternar-menu-admin"),
        navigation: document.getElementById("navegacao-admin"),
        profile: document.getElementById("perfil-admin"),
        profileName: document.getElementById("nome-perfil-admin"),
        overviewPage: document.getElementById("visao-geral"),
        assignmentsPage: document.getElementById("administradores-admin"),
        breadcrumbTitle: document.querySelector(".admin-breadcrumb strong")
    };

    const authorization = createAdminAuthorizationStore();
    const profileLabels = Object.freeze({
        SUPER_ADMIN: "Superadministrador",
        PLATFORM_ADMIN: "Administrador da plataforma",
        MODERATOR: "Moderador",
        AUDITOR: "Auditor"
    });
    const implementedNavigation = Object.freeze([
        { id: "nav-visao-geral", permission: "DASHBOARD_READ" },
        { id: "nav-indicadores", permission: "DASHBOARD_READ" },
        { id: "nav-atividade", permission: "DASHBOARD_READ" },
        { id: "nav-administradores", permission: "ADMIN_MANAGE" }
    ]);

    let requestVersion = 0;
    let activeController = null;
    let expirationTimer = null;
    let sessionCheckInFlight = null;
    let lastSessionCheckAt = 0;
    let loading = false;
    let loggingOut = false;
    let sessionValidated = false;
    let activePage = null;
    let assignmentPanel = null;

    function limparDadosProtegidos({ keepLogout = false } = {}) {
        clearProtectedDashboard({
            metrics: [...document.querySelectorAll("[data-metric]")],
            activityList: elements.activityList,
            activityState: elements.activityState,
            generatedAt: elements.generatedAt,
            content: elements.content,
            refresh: elements.refresh,
            logout: elements.logout
        }, { keepLogout });
        elements.navigation.hidden = true;
        elements.overviewPage.hidden = true;
        elements.assignmentsPage.hidden = true;
        assignmentPanel?.clear();
    }

    function limparAutorizacao() {
        clearAdminAuthorizationState(authorization, {
            navigation: elements.navigation,
            profile: elements.profile,
            profileName: elements.profileName
        });
        sessionValidated = false;
    }

    function esconderEstados() {
        elements.loading.hidden = true;
        elements.unauthenticated.hidden = true;
        elements.expired.hidden = true;
        elements.forbidden.hidden = true;
        elements.error.hidden = true;
    }

    function paginaSolicitada() {
        return window.location.hash === "#administradores" ? "administrators" : "dashboard";
    }

    function atualizarNavegacaoAtiva(page = activePage) {
        const hash = window.location.hash;
        const activeLinkId = page === "administrators" ? "nav-administradores"
            : hash === "#indicadores" ? "nav-indicadores"
                : hash === "#atividade" ? "nav-atividade" : "nav-visao-geral";
        elements.menu.querySelectorAll(".admin-nav__link").forEach(link => {
            if (link.id === activeLinkId) link.setAttribute("aria-current", "page");
            else link.removeAttribute("aria-current");
        });
    }

    function mostrarPagina(page) {
        activePage = page;
        elements.overviewPage.hidden = page !== "dashboard";
        elements.assignmentsPage.hidden = page !== "administrators";
        elements.breadcrumbTitle.textContent = page === "administrators" ? "Administradores" : "Visão geral";
        atualizarNavegacaoAtiva(page);
    }

    function abortarRequisicoesAtuais() {
        requestVersion += 1;
        activeController?.abort();
        activeController = null;
        loading = false;
    }

    function encerrarComSessaoExpirada(mensagem = "Sua sessão administrativa expirou.") {
        abortarRequisicoesAtuais();
        window.clearTimeout(expirationTimer);
        expirationTimer = null;
        limparAutorizacao();
        limparDadosProtegidos();
        esconderEstados();
        mostrarPagina("dashboard");
        elements.expired.querySelector("h2").textContent = mensagem;
        elements.expired.hidden = false;
        elements.sessionStatus.textContent = "Sessão administrativa inativa";
    }

    function mostrarErro(mensagem, { keepSession = sessionValidated } = {}) {
        window.clearTimeout(expirationTimer);
        expirationTimer = null;
        limparDadosProtegidos({ keepLogout: keepSession });
        elements.logout.hidden = !keepSession;
        esconderEstados();
        mostrarPagina("dashboard");
        elements.errorMessage.textContent = mensagem;
        elements.error.hidden = false;
        elements.sessionStatus.textContent = keepSession
            ? "Dados protegidos ocultos; sessão preservada"
            : "Não foi possível validar a sessão";
    }

    function mostrarNaoAutenticado() {
        window.clearTimeout(expirationTimer);
        expirationTimer = null;
        limparAutorizacao();
        limparDadosProtegidos();
        esconderEstados();
        mostrarPagina("dashboard");
        elements.unauthenticated.hidden = false;
        elements.sessionStatus.textContent = "Autenticação necessária";
    }

    function mostrarAcessoNegado(mensagem = "Seu perfil administrativo não tem permissão para consultar esta área.") {
        window.clearTimeout(expirationTimer);
        expirationTimer = null;
        // Keep an explicit self-logout control available even when the session
        // endpoint itself returned 403; never convert that response into logout.
        limparDadosProtegidos({ keepLogout: true });
        elements.logout.hidden = false;
        esconderEstados();
        mostrarPagina("dashboard");
        elements.forbiddenMessage.textContent = mensagem;
        elements.forbidden.hidden = false;
        elements.sessionStatus.textContent = sessionValidated
            ? "Sessão ativa; acesso não autorizado"
            : "Acesso administrativo não autorizado";
    }

    function renderizarContextoAutorizacao() {
        const context = authorization.current;
        if (!context) {
            elements.profile.hidden = true;
            elements.navigation.hidden = true;
            return;
        }

        elements.profileName.textContent = profileLabels[context.profile];
        elements.profile.hidden = false;

        const navigationEntries = implementedNavigation.map(entry => ({
            ...entry,
            element: document.getElementById(entry.id)
        }));
        renderPermissionNavigation(elements.navigation, navigationEntries, authorization);
    }

    assignmentPanel = initializeAdminAssignments({
        apiUrl: API_URL,
        authorization,
        onUnauthorized: () => encerrarComSessaoExpirada("Sua sessão administrativa expirou ou foi revogada.")
    });

    async function obterSessao(signal) {
        return fetchAdminSession(API_URL, fetch, signal);
    }

    async function obterIndicadores(signal) {
        return fetchAdminDashboard(API_URL, fetch, signal);
    }

    function renderizarAtividade(overview) {
        elements.activityList.replaceChildren();
        elements.activityList.hidden = true;
        elements.activityState.hidden = false;

        if (!overview.recentActivityAvailable) {
            elements.activityState.textContent = "Atividade administrativa indisponível no momento.";
            return;
        }
        if (overview.recentActivity.length === 0) {
            elements.activityState.textContent = "Nenhuma atividade administrativa encontrada.";
            return;
        }

        elements.activityState.hidden = true;
        elements.activityList.hidden = false;
        overview.recentActivity.forEach(item => {
            const time = Date.parse(item?.occurredAt);
            if (typeof item?.description !== "string" || !Number.isFinite(time)) return;

            const row = document.createElement("li");
            row.className = "activity-item";
            const dot = document.createElement("span");
            dot.className = "activity-dot";
            dot.setAttribute("aria-hidden", "true");
            const description = document.createElement("span");
            description.className = "activity-description";
            description.textContent = item.description;
            const timestamp = document.createElement("time");
            timestamp.className = "activity-time";
            timestamp.dateTime = new Date(time).toISOString();
            timestamp.textContent = formatarDataHora.format(time);
            row.append(dot, description, timestamp);
            elements.activityList.append(row);
        });

        if (!elements.activityList.childElementCount) {
            elements.activityList.hidden = true;
            elements.activityState.hidden = false;
            elements.activityState.textContent = "Nenhuma atividade administrativa encontrada.";
        }
    }

    function renderizarIndicadores(overview) {
        const metrics = {
            registeredUsers: overview.registeredUsers,
            activeOrganizations: overview.activeOrganizations,
            inactiveOrganizations: overview.inactiveOrganizations,
            archivedOrganizations: overview.archivedOrganizations,
            publications: overview.publications,
            volunteerOpportunities: overview.volunteerOpportunities
        };

        Object.entries(metrics).forEach(([name, value]) => {
            const element = document.querySelector(`[data-metric="${name}"]`);
            if (element) element.textContent = formatarNumero.format(value);
        });

        renderizarAtividade(overview);
        elements.generatedAt.textContent = `Indicadores consultados em ${formatarDataHora.format(Date.parse(overview.generatedAt))}.`;
        elements.content.hidden = false;
        elements.refresh.hidden = false;
        elements.logout.hidden = false;
        elements.sessionStatus.textContent = "Sessão administrativa ativa";
    }

    function agendarExpiracao(session) {
        window.clearTimeout(expirationTimer);
        const deadline = session.remainingMs;
        const localExpiry = Date.now() + deadline;
        const when = formatarDataHora.format(localExpiry);
        elements.sessionStatus.textContent = `Sessão ativa · próxima expiração às ${when}`;
        expirationTimer = window.setTimeout(() => {
            encerrarComSessaoExpirada("Sua sessão administrativa expirou.");
        }, deadline);
    }

    async function revalidarSessaoAoRetornar() {
        const protectedPageVisible = activePage === "administrators"
            ? !elements.assignmentsPage.hidden
            : !elements.content.hidden;
        if (!protectedPageVisible || loading || loggingOut || document.hidden) return;
        if (sessionCheckInFlight) return sessionCheckInFlight;
        if (performance.now() - lastSessionCheckAt < 3000) return;
        lastSessionCheckAt = performance.now();

        const validationVersion = requestVersion;
        const controller = new AbortController();
        sessionCheckInFlight = (async () => {
            try {
                const session = await obterSessao(controller.signal);
                if (validationVersion !== requestVersion || loggingOut) return;
                sessionValidated = true;
                agendarExpiracao(session);
            } catch (error) {
                if (validationVersion !== requestVersion || loggingOut) return;
                if (error instanceof AdminRequestError && error.kind === "unauthorized") {
                    encerrarComSessaoExpirada("Sua sessão administrativa expirou.");
                } else if (error instanceof AdminRequestError && error.kind === "forbidden") {
                    mostrarAcessoNegado("Sua sessão não tem autorização para esta área. A sessão não foi encerrada.");
                } else {
                    mostrarErro("Não foi possível revalidar a sessão. Verifique a conexão e tente novamente.");
                }
            } finally {
                sessionCheckInFlight = null;
            }
        })();
        return sessionCheckInFlight;
    }

    async function carregarDashboard() {
        if (loading || loggingOut) return;
        const requestedPage = paginaSolicitada();
        const sessionWasValidated = sessionValidated;
        loading = true;
        sessionValidated = false;
        activePage = requestedPage;
        const currentVersion = ++requestVersion;
        activeController?.abort();
        const controller = new AbortController();
        activeController = controller;
        window.clearTimeout(expirationTimer);
        limparDadosProtegidos();
        esconderEstados();
        elements.overviewPage.hidden = false;
        elements.assignmentsPage.hidden = true;
        elements.loading.hidden = false;
        elements.profile.hidden = true;
        elements.navigation.hidden = true;
        elements.sessionStatus.textContent = "Verificando sessão administrativa…";
        elements.metricGrid?.setAttribute("aria-busy", "true");

        try {
            const session = await obterSessao(controller.signal);
            if (currentVersion !== requestVersion) return;
            sessionValidated = true;
            agendarExpiracao(session);

            if (!authorization.current) {
                authorization.set(await fetchAdminCapabilities(API_URL, fetch, controller.signal));
            }
            if (currentVersion !== requestVersion) return;
            renderizarContextoAutorizacao();
            if (requestedPage === "administrators") {
                if (!authorization.hasPermission("ADMIN_MANAGE")) {
                    mostrarAcessoNegado("Seu perfil administrativo não pode gerenciar atribuições.");
                    return;
                }
                mostrarPagina("administrators");
                elements.loading.hidden = true;
                const assignmentSession = await assignmentPanel.load(0);
                if (currentVersion === requestVersion && assignmentSession) {
                    sessionValidated = true;
                    agendarExpiracao(assignmentSession);
                }
                return;
            }

            mostrarPagina("dashboard");
            if (!authorization.hasPermission("DASHBOARD_READ")) {
                mostrarAcessoNegado("Seu perfil administrativo não tem permissão para consultar o dashboard.");
                return;
            }

            const overview = await obterIndicadores(controller.signal);
            if (currentVersion !== requestVersion) return;

            // Confirma o estado depois da consulta e usa a expiração mais recente
            // devolvida pelo servidor para limpar a tela sem polling que renove a sessão.
            const currentSession = await obterSessao(controller.signal);
            if (currentVersion !== requestVersion) return;
            sessionValidated = true;

            esconderEstados();
            renderizarIndicadores(overview);
            agendarExpiracao(currentSession);
        } catch (error) {
            if (error?.name === "AbortError" || currentVersion !== requestVersion) return;
            if (error instanceof AdminRequestError && error.kind === "unauthorized") {
                if (sessionValidated || sessionWasValidated) {
                    encerrarComSessaoExpirada("Sua sessão administrativa expirou ou foi revogada.");
                } else {
                    mostrarNaoAutenticado();
                }
            } else if (error instanceof AdminRequestError && error.kind === "forbidden") {
                sessionValidated = sessionValidated || sessionWasValidated;
                mostrarAcessoNegado("A solicitação foi recusada por autorização. Os dados protegidos não foram exibidos.");
            } else if (error instanceof AdminRequestError && error.kind === "unavailable"
                    && error.status === 503) {
                sessionValidated = sessionValidated || sessionWasValidated;
                mostrarErro("Os indicadores estão temporariamente indisponíveis. Tente novamente.");
            } else if (error instanceof AdminRequestError && error.kind === "network") {
                sessionValidated = sessionValidated || sessionWasValidated;
                mostrarErro("Não foi possível conectar ao serviço administrativo. Verifique a rede e tente novamente.");
            } else {
                sessionValidated = sessionValidated || sessionWasValidated;
                mostrarErro("Não foi possível carregar os dados administrativos. Tente novamente.");
            }
        } finally {
            if (currentVersion === requestVersion) {
                loading = false;
                activeController = null;
                elements.loading.hidden = true;
                elements.metricGrid?.setAttribute("aria-busy", "false");
            }
        }
    }

    elements.forbiddenRetry.addEventListener("click", carregarDashboard);

    async function obterCsrf(signal) {
        const resposta = await fetch(`${API_URL}/usuario/csrf`, {
            method: "GET",
            credentials: "include",
            cache: "no-store",
            headers: { Accept: "application/json" },
            signal
        });
        if (!resposta.ok) throw new Error("csrf-unavailable");
        const csrf = await resposta.json();
        if (!csrf?.headerName || !csrf?.token) throw new Error("invalid-csrf");
        return csrf;
    }

    async function encerrarSessaoAdmin() {
        if (loggingOut) return;
        loggingOut = true;
        const sessionWasValidated = sessionValidated;
        abortarRequisicoesAtuais();
        window.clearTimeout(expirationTimer);
        limparAutorizacao();
        limparDadosProtegidos();
        esconderEstados();
        elements.logout.disabled = true;
        elements.sessionStatus.textContent = "Encerrando sessão administrativa…";
        const controller = new AbortController();

        async function lidarComLogoutNaoConfirmado(mensagem) {
            try {
                await obterSessao(controller.signal);
                sessionValidated = true;
                mostrarErro(mensagem, { keepSession: true });
            } catch (error) {
                if (error instanceof AdminRequestError && error.kind === "unauthorized") {
                    encerrarComSessaoExpirada("A sessão administrativa não está mais ativa.");
                } else if (error instanceof AdminRequestError && error.kind === "forbidden") {
                    sessionValidated = sessionWasValidated;
                    mostrarAcessoNegado("O logout não foi confirmado; a sessão administrativa não foi encerrada.");
                } else {
                    sessionValidated = sessionWasValidated;
                    mostrarErro("Não foi possível confirmar o logout. Tente novamente.", {
                        keepSession: sessionWasValidated
                    });
                }
            }
        }

        try {
            const csrf = await obterCsrf(controller.signal);
            const resposta = await fetch(`${API_URL}/admin/auth/logout`, {
                method: "POST",
                credentials: "include",
                cache: "no-store",
                headers: {
                    Accept: "application/json",
                    [csrf.headerName]: csrf.token
                },
                signal: controller.signal
            });

            if (resposta.status === 204) {
                mostrarNaoAutenticado();
                elements.sessionStatus.textContent = "Sessão administrativa encerrada";
                return;
            }

            if (resposta.status === 401) {
                encerrarComSessaoExpirada("A sessão administrativa não está mais ativa.");
                return;
            }
            if (resposta.status === 403) {
                await lidarComLogoutNaoConfirmado("O logout foi recusado. A sessão administrativa continua ativa.");
                return;
            }
            throw new Error("logout-failed");
        } catch (error) {
            if (error?.name === "AbortError") return;
            await lidarComLogoutNaoConfirmado("Não foi possível confirmar o logout. Tente novamente.");
        } finally {
            loggingOut = false;
            elements.logout.disabled = false;
        }
    }

    function abrirLoginAdmin() {
        window.dispatchEvent(new Event("porto-ajuda:abrir-admin-login"));
    }

    elements.retry.addEventListener("click", carregarDashboard);
    elements.refresh.addEventListener("click", carregarDashboard);
    elements.logout.addEventListener("click", encerrarSessaoAdmin);
    document.querySelectorAll("[data-abrir-login-admin]").forEach(button => {
        button.addEventListener("click", abrirLoginAdmin);
    });
    document.getElementById("abrir-login-admin").addEventListener("click", abrirLoginAdmin);

    elements.menuToggle.addEventListener("click", () => {
        const aberto = elements.menu.classList.toggle("is-open");
        elements.menuToggle.setAttribute("aria-expanded", String(aberto));
        elements.menuToggle.setAttribute("aria-label", aberto ? "Fechar navegação" : "Abrir navegação");
    });
    elements.menu.querySelectorAll(".admin-nav__link").forEach(link => {
        link.addEventListener("click", () => {
            elements.menu.classList.remove("is-open");
            elements.menuToggle.setAttribute("aria-expanded", "false");
            elements.menuToggle.setAttribute("aria-label", "Abrir navegação");
        });
    });

    let lastHandledHash = window.location.hash || "#visao-geral";
    window.addEventListener("hashchange", () => {
        const requestedPage = paginaSolicitada();
        if (assignmentPanel.isMutating() && requestedPage !== activePage) {
            window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}${lastHandledHash}`);
            return;
        }
        lastHandledHash = window.location.hash || "#visao-geral";
        if (requestedPage === activePage) {
            atualizarNavegacaoAtiva(requestedPage);
            return;
        }
        if (loading) {
            activeController?.abort();
            activeController = null;
            requestVersion += 1;
            loading = false;
        }
        void carregarDashboard();
    });

    window.addEventListener("porto-ajuda:admin-session-ended", () => {
        abortarRequisicoesAtuais();
        window.clearTimeout(expirationTimer);
        expirationTimer = null;
        mostrarNaoAutenticado();
        elements.sessionStatus.textContent = "Sessão administrativa encerrada";
    });

    document.addEventListener("visibilitychange", () => {
        if (document.visibilityState === "visible") void revalidarSessaoAoRetornar();
    });
    window.addEventListener("focus", () => void revalidarSessaoAoRetornar());

    document.addEventListener("keydown", evento => {
        if (evento.key === "Escape" && elements.menu.classList.contains("is-open")) {
            elements.menu.classList.remove("is-open");
            elements.menuToggle.setAttribute("aria-expanded", "false");
            elements.menuToggle.setAttribute("aria-label", "Abrir navegação");
            elements.menuToggle.focus();
        }
    });

    carregarDashboard();
})();
